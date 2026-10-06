package org.tiglor.common.redis;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Redisson 分布式锁配置。
 * <p>
 * 与 {@link RedisCacheConfig} 里的 Lettuce 客户端并存——
 * Lettuce 负责 {@code @Cacheable} 缓存读写和 {@link org.springframework.data.redis.core.RedisTemplate} 操作，
 * Redisson 只负责分布式锁、计数器、并发集合等高级数据结构。
 * </p>
 * <p>
 * 不用 redisson-spring-boot-starter 的原因：
 * 它会注册 {@code RedissonConnectionFactory} 并覆盖默认的 {@code LettuceConnectionFactory}，
 * 导致 {@code @Cacheable} 的缓存实现也换成 Redisson（性能不如 Lettuce + 手动序列化）。
 * 手动配一个独立的 RedissonClient Bean 只做锁，互不干扰。
 * </p>
 * <p>
 * 连接信息复用 {@code spring.data.redis.*}（和 Lettuce 同一套 host/port/password/database），
 * 不需要额外的配置项。
 * </p>
 */
@Configuration
public class RedissonConfig {

    private static final Logger log = LoggerFactory.getLogger(RedissonConfig.class);

    @Value("${spring.data.redis.host:127.0.0.1}")
    private String host;

    @Value("${spring.data.redis.port:6379}")
    private int port;

    @Value("${spring.data.redis.password:}")
    private String password;

    @Value("${spring.data.redis.database:0}")
    private int database;

    /**
     * RedissonClient 单例。服务内所有需要锁的地方 {@code @Autowired RedissonClient} 即可。
     * Spring 容器关闭时 RedissonClient 会自动 shutdown（{@code RedissonClient} 实现了 {@code AutoCloseable}）。
     */
    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient() {
        Config config = new Config();
        config.useSingleServer()
                .setAddress("redis://" + host + ":" + port)
                // 空字符串表示无密码，null 也能行但 spring 的默认值是 ""
                .setPassword(password.isBlank() ? null : password)
                .setDatabase(database)
                // 连接空闲 10s 后关闭，避免 Redis 连接堆积
                .setConnectionMinimumIdleSize(5)
                .setConnectionPoolSize(16)
                // 命令等待超时（毫秒），默认 3s，锁竞争时适当放宽
                .setTimeout(3000);

        RedissonClient client = Redisson.create(config);
        log.info("RedissonClient 初始化完成：redis://{}:{} (db={})", host, port, database);
        return client;
    }
}
