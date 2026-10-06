package org.tiglor.recommend.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.IService;
import org.tiglor.recommend.dto.RecommendBatchRequest;
import org.tiglor.recommend.entity.RecommendResult;

import java.util.List;

/**
 * 推荐候选的存取与曝光/点击回写。
 * <p>
 * 这个服务「不产出推荐」：算法在外部训练和运行，通过 {@link #batchUpsert} 把结果写进来。
 * 本服务负责的是「按用户取一屏」「记住哪些已经给这个人看过」「回收点击信号」。
 * </p>
 */
public interface RecommendResultService extends IService<RecommendResult> {

    /** 单屏最大条数，不接受外部传更大的值 */
    int MAX_FEED_SIZE = 50;

    /**
     * 取一屏推荐并「同时」标记为已曝光。
     * <p>
     * 读取和标记必须是一个动作：拆成两个接口的话，客户端漏调标记就会反复拿到同一屏，
     * {@code is_exposed} 也就永远统计不出真实曝光量。
     * </p>
     *
     * @param userId 当前登录用户，候选严格按它过滤——绝不能返回别人的推荐
     */
    List<RecommendResult> feed(Long userId, String scene, int size);

    /**
     * 上报点击，同时把 {@code is_exposed} 也置 1。
     * <p>
     * 点击蕴含曝光：能点说明看到了。不一起置位的话 CTR = clicked / exposed 可能大于 1，
     * 那种数据错得悄无声息，比丢一条点击信号糟糕得多。
     * </p>
     *
     * @return 是否真的发生了 0→1 翻转；重复上报同一个 id 返回 false
     */
    boolean reportClick(Long userId, long id);

    /** 我已经曝光过的候选，按写入先后倒序 */
    Page<RecommendResult> myHistory(Long userId, String scene, long current, long size);

    /**
     * 算法任务批量写入。
     *
     * @return 提交的条数，<b>不是</b>受影响行数（见 {@code RecommendResultMapper.batchUpsert}）
     */
    int batchUpsert(RecommendBatchRequest request);

    /** 管理端分页，四个过滤条件都可选 */
    Page<RecommendResult> page(long current, long size, Long userId, String scene,
                               Boolean exposed, Boolean clicked);

    /**
     * 清理 {@code days} 天前生成的候选，返回删除行数。
     * <p>
     * 候选池不会自己收缩：已曝光的行永远回不到 feed 里，却一直占着表和
     * {@code idx_user_scene_score}。没有清理任务的话这张表只增不减。
     * </p>
     */
    int pruneStale(int days);
}
