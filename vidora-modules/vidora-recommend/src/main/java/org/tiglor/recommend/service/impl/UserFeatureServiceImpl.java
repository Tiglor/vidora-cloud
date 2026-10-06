package org.tiglor.recommend.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.tiglor.common.core.BizException;
import org.tiglor.common.core.ResultCode;
import org.tiglor.recommend.dto.UserFeatureBatchRequest;
import org.tiglor.recommend.dto.UserFeatureItemRequest;
import org.tiglor.recommend.entity.UserFeature;
import org.tiglor.recommend.enums.FeatureType;
import org.tiglor.recommend.mapper.UserFeatureMapper;
import org.tiglor.recommend.service.UserFeatureService;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 用户行为特征。
 * <p>
 * 这里的 {@code userId} 一律是「参数」而不是登录态：读写双方都是算法任务和管理端，
 * 它们需要按任意用户操作。所以三个方法所在的接口全部由 {@code recommend:manage} 权限挡住，
 * 不能像 feed 那样开放给普通登录用户。
 * </p>
 */
@Service
public class UserFeatureServiceImpl extends ServiceImpl<UserFeatureMapper, UserFeature>
        implements UserFeatureService {

    /** 单条 INSERT 语句最多拼多少行，超了会撞 {@code max_allowed_packet} */
    public static final int BATCH_CHUNK = 500;

    @Override
    public List<UserFeature> top(Long userId, String featureType, int limit) {
        requireUserId(userId);
        FeatureType type = FeatureType.of(featureType);
        int max = (int) Math.min(Math.max(limit, 1), MAX_LIMIT);

        // searchCount=false：要的是权重最高的前 N 个，不是总数
        Page<UserFeature> page = new Page<>(1, max, false);
        lambdaQuery()
                .eq(UserFeature::getUserId, userId)
                .eq(UserFeature::getFeatureType, type.getCode())
                .orderByDesc(UserFeature::getWeight)
                // id 是兜底排序键：权重相同的特征没有稳定顺序的话，两次召回会拿到不同的集合
                .orderByAsc(UserFeature::getId)
                .page(page);
        return page.getRecords();
    }

    @Override
    public int batchUpsert(UserFeatureBatchRequest request) {
        requireUserId(request.getUserId());
        List<UserFeatureItemRequest> items = request.getItems();
        List<UserFeature> rows = new ArrayList<>(items.size());
        for (UserFeatureItemRequest item : items) {
            UserFeature row = new UserFeature();
            row.setUserId(request.getUserId());
            // 存规范值：featureType 是唯一键的一段，"TAG" 和 "tag" 在 _ci 下算同一行，
            // 但库里留下两种写法会让按类型统计多一步归一
            row.setFeatureType(FeatureType.of(item.getFeatureType()).getCode());
            row.setFeatureValue(item.getFeatureValue().trim());
            row.setWeight(item.getWeight() == null ? BigDecimal.ZERO : item.getWeight());
            rows.add(row);
        }
        // 不包事务，理由同 RecommendResultServiceImpl.batchUpsert
        for (int from = 0; from < rows.size(); from += BATCH_CHUNK) {
            baseMapper.batchUpsert(rows.subList(from, Math.min(from + BATCH_CHUNK, rows.size())));
        }
        return rows.size();
    }

    @Override
    public int removeFeatures(Long userId, String featureType) {
        requireUserId(userId);
        String typeCode = featureType == null || featureType.isBlank()
                ? null
                : FeatureType.of(featureType).getCode();
        // 命中 idx_user_id；typeCode 为 null 时清掉这个人的全部画像
        return baseMapper.delete(Wrappers.<UserFeature>lambdaQuery()
                .eq(UserFeature::getUserId, userId)
                .eq(typeCode != null, UserFeature::getFeatureType, typeCode));
    }

    private static void requireUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BizException(ResultCode.VALIDATE_FAILED, "userId 非法");
        }
    }
}
