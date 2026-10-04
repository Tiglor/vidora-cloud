package org.tiglor.recommend.service.impl;

import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.tiglor.common.core.BizException;
import org.tiglor.common.core.ResultCode;
import org.tiglor.common.core.support.JsonValues;
import org.tiglor.common.redis.CacheNames;
import org.tiglor.recommend.dto.AlgoConfigRequest;
import org.tiglor.recommend.entity.AlgoConfig;
import org.tiglor.recommend.enums.AlgoType;
import org.tiglor.recommend.enums.RecommendScene;
import org.tiglor.recommend.mapper.AlgoConfigMapper;
import org.tiglor.recommend.service.AlgoConfigService;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 推荐算法配置。
 * <p>
 * 缓存的是「某场景某算法的启用配置」这一个组合（key = {@code scene:algoType}，最多 3×3 = 9 条），
 * 而不是整张表：算法侧每次只关心自己那一路的参数，缓存整表会让任何一次改动都失效掉全部场景。
 * </p>
 * <p>
 * 写入路径一律 {@code allEntries = true}：{@code status} 的启停会影响哪些项进入 Map，
 * 而失效粒度只有 cacheName，没法只清一个 key 的一部分。
 * </p>
 */
@Service
public class AlgoConfigServiceImpl extends ServiceImpl<AlgoConfigMapper, AlgoConfig> implements AlgoConfigService {

    private static final int STATUS_ENABLED = 1;

    private static final long MAX_PAGE_SIZE = 100L;

    @Override
    @Cacheable(cacheNames = CacheNames.RECOMMEND_ALGO_CONFIG, key = "#scene.code + ':' + #algoType.code")
    public Map<String, String> configsOf(RecommendScene scene, AlgoType algoType) {
        Map<String, String> configs = new LinkedHashMap<>();
        for (AlgoConfig config : enabled(scene.getCode(), algoType.getCode()).list()) {
            // 值为 null 的项不进 Map：Collectors.toMap 不接受 null 值，
            // 而且「没配」和「配了个 null」对算法侧本来就没区别
            if (config.getConfigValue() != null) {
                configs.put(config.getConfigKey(), config.getConfigValue());
            }
        }
        return configs;
    }

    @Override
    public List<AlgoConfig> listEnabled(RecommendScene scene) {
        return enabled(scene.getCode(), null).list();
    }

    @Override
    public Page<AlgoConfig> page(long current, long size, String scene, String algoType, Integer status) {
        String sceneCode = scene == null || scene.isBlank() ? null : RecommendScene.of(scene).getCode();
        String algoCode = algoType == null || algoType.isBlank() ? null : AlgoType.of(algoType).getCode();
        return lambdaQuery()
                .eq(sceneCode != null, AlgoConfig::getScene, sceneCode)
                .eq(algoCode != null, AlgoConfig::getAlgoType, algoCode)
                .eq(status != null, AlgoConfig::getStatus, status)
                .orderByAsc(AlgoConfig::getScene)
                .orderByAsc(AlgoConfig::getAlgoType)
                .orderByAsc(AlgoConfig::getConfigKey)
                .page(new Page<>(Math.max(current, 1), clampSize(size)));
    }

    @Override
    @CacheEvict(cacheNames = CacheNames.RECOMMEND_ALGO_CONFIG, allEntries = true)
    public AlgoConfig upsert(AlgoConfigRequest request) {
        String scene = RecommendScene.of(request.getScene()).getCode();
        String algoType = AlgoType.of(request.getAlgoType()).getCode();
        String configKey = request.getConfigKey().trim();
        baseMapper.upsert(scene, algoType, configKey,
                // config_value 约定存 JSON，非法值要在到达 MySQL 之前就被拦下来
                JsonValues.requireValid(request.getConfigValue(), "configValue"),
                trimToNull(request.getDescription()));
        // uk_scene_algo_key 保证至多一行，回读拿到的是数据库真正存下的那份（含 id 与时间戳）
        return lambdaQuery()
                .eq(AlgoConfig::getScene, scene)
                .eq(AlgoConfig::getAlgoType, algoType)
                .eq(AlgoConfig::getConfigKey, configKey)
                .one();
    }

    @Override
    @CacheEvict(cacheNames = CacheNames.RECOMMEND_ALGO_CONFIG, allEntries = true)
    public void setStatus(Long id, int status) {
        if (status != 0 && status != 1) {
            throw new BizException(ResultCode.VALIDATE_FAILED, "status 只能是 0-禁用 或 1-启用");
        }
        if (getById(id) == null) {
            throw new BizException(ResultCode.NOT_FOUND, "配置不存在：" + id);
        }
        // ne(status) 让「已经是目标值」的情况影响 0 行，重复点击是幂等的
        lambdaUpdate()
                .set(AlgoConfig::getStatus, status)
                .eq(AlgoConfig::getId, id)
                .ne(AlgoConfig::getStatus, status)
                .update();
    }

    @Override
    @CacheEvict(cacheNames = CacheNames.RECOMMEND_ALGO_CONFIG, allEntries = true)
    public void delete(Long id) {
        if (getById(id) == null) {
            throw new BizException(ResultCode.NOT_FOUND, "配置不存在：" + id);
        }
        removeById(id);
    }

    /** 启用项，按算法、再按 key 排序，让同一算法的参数在管理端表格里挨在一起 */
    private LambdaQueryChainWrapper<AlgoConfig> enabled(String sceneCode, String algoCode) {
        return lambdaQuery()
                .eq(AlgoConfig::getScene, sceneCode)
                .eq(algoCode != null, AlgoConfig::getAlgoType, algoCode)
                .eq(AlgoConfig::getStatus, STATUS_ENABLED)
                .orderByAsc(AlgoConfig::getAlgoType)
                .orderByAsc(AlgoConfig::getConfigKey);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static long clampSize(long size) {
        return Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    }
}
