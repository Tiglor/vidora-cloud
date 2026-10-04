package org.tiglor.common.redis;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Redis 缓存装配。
 * <p>
 * 默认的 JDK 序列化要求缓存对象实现 {@code Serializable}，且落盘是二进制无法排查；
 * 这里统一改成带类型信息的 JSON，并给不同缓存名配置不同 TTL——
 * 菜单/分类这类「读多改少」的可以放很久，播放地址这类要短。
 * </p>
 */
@Configuration
@EnableCaching
public class RedisCacheConfig implements CachingConfigurer {

    private static final Logger log = LoggerFactory.getLogger(RedisCacheConfig.class);

    private static final Duration DEFAULT_TTL = Duration.ofMinutes(30);

    /**
     * 各缓存名的 TTL，未列出的用 {@link #DEFAULT_TTL}。
     * <p>用 {@code Map.ofEntries} 而不是 {@code Map.of}：后者最多 10 个键值对，
     * 加第 11 个缓存名会直接编译失败，而错误信息（找不到合适的方法）看不出是条数超了。</p>
     */
    private static final Map<String, Duration> TTL_BY_CACHE = Map.ofEntries(
            Map.entry(CacheNames.MENU_USER_TREE, Duration.ofHours(2)),
            Map.entry(CacheNames.MENU_ALL_TREE, Duration.ofHours(2)),
            Map.entry(CacheNames.MENU_ROLE_IDS, Duration.ofHours(2)),
            Map.entry(CacheNames.CATEGORY_LIST, Duration.ofHours(6)),
            Map.entry(CacheNames.TAG_HOT, Duration.ofHours(1)),
            Map.entry(CacheNames.FEED_CONFIG, Duration.ofHours(1)),
            // 榜单一天内会被运营反复重算和下线，缓存久了改动静不下来
            Map.entry(CacheNames.HOT_SEARCH_BOARD, Duration.ofMinutes(5)),
            Map.entry(CacheNames.RECOMMEND_ALGO_CONFIG, Duration.ofHours(1)),
            // 建议词是运营维护的字典，改完等十分钟生效可以接受
            Map.entry(CacheNames.SEARCH_SUGGEST_TOP, Duration.ofMinutes(10)),
            Map.entry(CacheNames.VIDEO_PLAY_URL, Duration.ofMinutes(10)),
            // 计数每次播放/点赞都在变，只做「吸收热点读」用，一分钟的延迟对播放数没有影响
            Map.entry(CacheNames.INTERACT_VIDEO_TOTALS, Duration.ofSeconds(60))
    );

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration defaults = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(DEFAULT_TTL)
                .disableCachingNullValues()
                // key 形如 vidora:menu:user-tree:42，便于按前缀排查与批量清理
                .computePrefixWith(cacheName -> "vidora:" + cacheName + ":")
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(jsonSerializer()));

        Map<String, RedisCacheConfiguration> perCache = new HashMap<>();
        TTL_BY_CACHE.forEach((name, ttl) -> perCache.put(name, defaults.entryTtl(ttl)));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaults)
                .withInitialCacheConfigurations(perCache)
                .build();
    }

    /**
     * Redis 不可用时不要把整个接口打成 500。
     * <p>
     * Spring 默认的 {@code SimpleCacheErrorHandler} 会把连接异常直接抛给调用方，
     * 于是 Redis 一挂，所有带 {@code @Cacheable} 的接口全部失败——
     * 而缓存本来只是加速手段，数据库完全能扛住这段时间的读。
     * 这里改成记日志后放行，让调用自然落到底层方法上。
     * </p>
     */
    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
                log.warn("缓存读取失败，降级为直接查库：cache={}, key={}", name(cache), key, exception);
            }

            @Override
            public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
                log.warn("缓存写入失败，本次结果不缓存：cache={}, key={}", name(cache), key, exception);
            }

            @Override
            public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
                log.warn("缓存失效失败，可能读到旧值直到 TTL 过期：cache={}, key={}", name(cache), key, exception);
            }

            @Override
            public void handleCacheClearError(RuntimeException exception, Cache cache) {
                log.warn("缓存清空失败：cache={}", name(cache), exception);
            }

            private String name(Cache cache) {
                return cache == null ? "unknown" : cache.getName();
            }
        };
    }

    /** 手动操作 Redis（计数器、分布式结构等）用；键为字符串，值为 JSON */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        GenericJackson2JsonRedisSerializer valueSerializer = jsonSerializer();
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(valueSerializer);
        template.setHashValueSerializer(valueSerializer);
        template.afterPropertiesSet();
        return template;
    }

    private static GenericJackson2JsonRedisSerializer jsonSerializer() {
        return new GenericJackson2JsonRedisSerializer(cacheObjectMapper());
    }

    /**
     * 写入类型信息（{@code @class}）才能在读回时还原具体类型。
     * 白名单限定在项目自身与 JDK 集合/时间类，避免 Redis 被写入恶意 payload 时
     * 触发任意类实例化（多态反序列化 gadget）。
     */
    private static ObjectMapper cacheObjectMapper() {
        PolymorphicTypeValidator validator = BasicPolymorphicTypeValidator.builder()
                .allowIfSubType("org.tiglor.")
                .allowIfSubType("java.util.")
                .allowIfSubType("java.time.")
                .build();
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .activateDefaultTyping(validator, ObjectMapper.DefaultTyping.NON_FINAL, JsonTypeInfo.As.PROPERTY);
    }
}
