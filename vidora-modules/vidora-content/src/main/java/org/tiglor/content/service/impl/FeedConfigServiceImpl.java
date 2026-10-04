package org.tiglor.content.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.tiglor.common.core.BizException;
import org.tiglor.common.core.ResultCode;
import org.tiglor.common.core.support.JsonValues;
import org.tiglor.common.redis.CacheNames;
import org.tiglor.content.dto.FeedConfigRequest;
import org.tiglor.content.entity.FeedConfig;
import org.tiglor.content.enums.FeedType;
import org.tiglor.content.mapper.FeedConfigMapper;
import org.tiglor.content.service.FeedConfigService;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 推荐流配置。
 * <p>
 * 一个流类型下的配置项就那么几条，读的时候整批取出来摊成 Map，一次缓存命中就够推荐服务用；
 * 写的时候按唯一键 upsert，不先查再决定。
 * </p>
 */
@Service
public class FeedConfigServiceImpl extends ServiceImpl<FeedConfigMapper, FeedConfig> implements FeedConfigService {

    @Override
    // key 用的是调用方传进来的原样字符串，"HOT" 和 "hot" 会各占一份内容相同的缓存；
    // 写路径是 allEntries 整体失效，两者不会各自漂移
    @Cacheable(cacheNames = CacheNames.FEED_CONFIG, key = "#feedType")
    public Map<String, String> configsOf(String feedType) {
        return listByFeedType(feedType).stream()
                // 值为 NULL 的项直接不出现：Collectors.toMap 不接受 null 值，
                // 而且「没配」和「配了个 null」对调用方本来就没区别
                .filter(config -> config.getConfigValue() != null)
                .collect(Collectors.toMap(FeedConfig::getConfigKey, FeedConfig::getConfigValue));
    }

    @Override
    public List<FeedConfig> listByFeedType(String feedType) {
        String code = FeedType.of(feedType).getCode();
        return lambdaQuery()
                .eq(FeedConfig::getFeedType, code)
                .orderByAsc(FeedConfig::getConfigKey)
                .list();
    }

    @Override
    @CacheEvict(cacheNames = CacheNames.FEED_CONFIG, allEntries = true)
    public FeedConfig upsert(FeedConfigRequest request) {
        String code = FeedType.of(request.getFeedType()).getCode();
        String key = trimToNull(request.getConfigKey());
        if (key == null) {
            throw new BizException(ResultCode.VALIDATE_FAILED, "configKey 不能为空");
        }
        String value = JsonValues.requireValid(request.getConfigValue(), "configValue");
        baseMapper.upsert(code, key, value, trimToNull(request.getDescription()));
        // uk_feed_key 保证至多一行
        return lambdaQuery()
                .eq(FeedConfig::getFeedType, code)
                .eq(FeedConfig::getConfigKey, key)
                .one();
    }

    @Override
    @CacheEvict(cacheNames = CacheNames.FEED_CONFIG, allEntries = true)
    public void delete(Long id) {
        if (getById(id) == null) {
            throw new BizException(ResultCode.NOT_FOUND, "配置不存在：" + id);
        }
        removeById(id);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
