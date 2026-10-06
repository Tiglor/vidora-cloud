package org.tiglor.recommend.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.tiglor.common.core.ApiResult;
import org.tiglor.common.core.security.UserContext;
import org.tiglor.recommend.dto.RecommendBatchRequest;
import org.tiglor.recommend.entity.RecommendResult;
import org.tiglor.recommend.service.RecommendResultService;

import java.util.List;

/**
 * 推荐候选。
 * <p>
 * 用户侧的三个接口（feed / click / history）的 {@code userId} 一律取自登录态，
 * 不接受请求参数——否则改一个 query 就能看别人的推荐、替别人记点击。
 * 管理侧接口用 {@code recommend:manage} 挡住。
 * </p>
 * <p>
 * 这里替换掉了原先的壳控制器，它有三处越权：{@code GET /recommends/page} 不带任何过滤条件
 * （任何登录用户都能翻遍全站候选，等于公开「谁在被推什么」），
 * {@code POST /recommends} 收裸实体（调用方自己填 userId 就能往任何人 feed 里塞视频），
 * {@code PUT /recommends/{id}} 能把任意行的 userId 和曝光/点击位改成任意值。
 * </p>
 */
@RestController
@RequestMapping("/recommends")
@RequiredArgsConstructor
public class RecommendResultController {

    private final RecommendResultService service;

    /**
     * 取一屏推荐
     *
     * <p>返回的同时这些候选就被标记为已曝光。</p>
     */
    @GetMapping("/feed")
    public ApiResult<List<RecommendResult>> feed(@RequestParam String scene,
                                                 @RequestParam(defaultValue = "10") int size) {
        return ApiResult.ok(service.feed(UserContext.getUserId(), scene, size));
    }

    /**
     * 上报点击
     *
     * @return false 表示这条已经记过了——重复上报是幂等的，不算错误
     */
    @PostMapping("/{id}/click")
    public ApiResult<Boolean> click(@PathVariable Long id) {
        return ApiResult.ok(service.reportClick(UserContext.getUserId(), id));
    }

    /**
     * 查询我的推荐历史
     *
     * <p>我已经被推过的候选，按写入先后倒序，给「不感兴趣」这类反馈入口做展示。</p>
     */
    @GetMapping("/history")
    public ApiResult<Page<RecommendResult>> history(@RequestParam(required = false) String scene,
                                                    @RequestParam(defaultValue = "1") long current,
                                                    @RequestParam(defaultValue = "20") long size) {
        return ApiResult.ok(service.myHistory(UserContext.getUserId(), scene, current, size));
    }

    /**
     * 批量写入推荐候选
     *
     * <p>给离线算法任务回写结果用。</p>
     */
    @PostMapping("/batch")
    @PreAuthorize("hasAuthority('recommend:manage')")
    public ApiResult<Integer> batchUpsert(@Valid @RequestBody RecommendBatchRequest request) {
        return ApiResult.ok(service.batchUpsert(request));
    }

    /**
     * 推荐候选分页
     *
     * <p>
     * 全站的推荐候选，按 id 倒序（最近写入的在前）。这是本服务里唯一不带 {@code user_id} 条件查询的地方——用户侧三个接口一律把 userId
     * 钉在登录态上。正因为这里能跨用户翻，才必须由 {@code recommend:manage} 挡住。
     * </p>
     *
     * @param scene   home / follow / topic，非法取值当场报错而不是查出一页空结果
     * @param exposed 是否已下发过。true 只看在 feed 里露过面的，false 只看还没下发的，不传则两种都列
     * @param clicked 是否被点过，三态同上；{@code true + exposed=false} 理论上查不出东西，点击必然伴随曝光
     */
    @GetMapping("/page")
    @PreAuthorize("hasAuthority('recommend:manage')")
    public ApiResult<Page<RecommendResult>> page(@RequestParam(defaultValue = "1") long current,
                                                 @RequestParam(defaultValue = "20") long size,
                                                 @RequestParam(required = false) Long userId,
                                                 @RequestParam(required = false) String scene,
                                                 @RequestParam(required = false) Boolean exposed,
                                                 @RequestParam(required = false) Boolean clicked) {
        return ApiResult.ok(service.page(current, size, userId, scene, exposed, clicked));
    }

    /**
     * 清理过期推荐候选
     *
     * <p>
     * 清理 {@code days} 天前生成的候选。已曝光的行永远回不到 feed 里，却一直占着表和索引，没有清理任务的话这张表只增不减。
     * 目前只能手动调，见 ARCHITECTURE 的待完善清单。
     * </p>
     *
     * @return 删除行数
     */
    @DeleteMapping("/stale")
    @PreAuthorize("hasAuthority('recommend:purge')")
    public ApiResult<Integer> pruneStale(@RequestParam(defaultValue = "30") int days) {
        return ApiResult.ok(service.pruneStale(days));
    }
}
