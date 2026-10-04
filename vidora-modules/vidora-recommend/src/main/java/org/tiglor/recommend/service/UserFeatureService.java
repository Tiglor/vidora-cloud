package org.tiglor.recommend.service;

import com.baomidou.mybatisplus.spring.service.IService;
import org.tiglor.recommend.dto.UserFeatureBatchRequest;
import org.tiglor.recommend.entity.UserFeature;

import java.util.List;

/**
 * 用户行为特征的存取。
 * <p>
 * 特征由外部任务算好后写进来，召回模型读出去。本服务不做任何特征工程——
 * 没有衰减、没有归一化，写入是**覆盖**语义（见 {@code UserFeatureMapper.batchUpsert}）。
 * </p>
 */
public interface UserFeatureService extends IService<UserFeature> {

    /** 单次查询最多返回的特征条数 */
    int MAX_LIMIT = 200;

    /** 某用户某一类特征里权重最高的若干个，权重相同时按 id 升序保证稳定 */
    List<UserFeature> top(Long userId, String featureType, int limit);

    /**
     * 写入一个用户的画像。
     *
     * @return 提交的条数，<b>不是</b>受影响行数
     */
    int batchUpsert(UserFeatureBatchRequest request);

    /**
     * 删掉某用户的特征。
     * <p>
     * 不叫 {@code remove}：{@code IService} 上已经有 {@code remove(Wrapper)} / {@code removeById}，
     * 同名重载会让人以为这是框架方法。
     * </p>
     *
     * @param featureType 为 null 时删这个用户的全部特征
     * @return 删除行数
     */
    int removeFeatures(Long userId, String featureType);
}
