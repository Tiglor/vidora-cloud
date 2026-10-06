package org.tiglor.interact.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.tiglor.common.core.ApiResult;
import org.tiglor.common.log.annotation.BusinessType;
import org.tiglor.common.log.annotation.OperLog;
import org.tiglor.common.core.security.UserContext;
import org.tiglor.interact.dto.CommentCreateRequest;
import org.tiglor.interact.dto.CommentView;
import org.tiglor.interact.service.CommentService;

import java.util.List;

/**
 * 评论与回复。
 * <p>
 * 读接口和发表接口都只要求登录，不挂权限位——{@code comment:list} 那个权限管的是后台
 * 「评论管理」页面的可见性，不是「用户能不能看视频下面的评论」。
 * </p>
 */
@RestController
@RequestMapping("/comments")
@RequiredArgsConstructor
public class CommentController {

    private static final String PERM_DELETE = "comment:delete";

    private final CommentService commentService;

    /**
     * 查询视频评论列表
     *
     * <p>某视频的顶层评论分页，每条带前几条回复。{@code sort=hot} 按点赞数，默认按时间倒序。</p>
     */
    @GetMapping("/video/{videoId}")
    public ApiResult<Page<CommentView>> listByVideo(@PathVariable Long videoId,
                                                    @RequestParam(defaultValue = "1") long current,
                                                    @RequestParam(defaultValue = "20") long size,
                                                    @RequestParam(defaultValue = "new") String sort) {
        return ApiResult.ok(commentService.listByVideo(videoId, current, size, sort));
    }

    /**
     * 查询楼中楼回复
     *
     * <p>楼中楼「查看更多回复」用，按 {@code rootId} 取这一楼的回复分页。</p>
     */
    @GetMapping("/replies/{rootId}")
    public ApiResult<Page<CommentView>> listReplies(@PathVariable Long rootId,
                                                    @RequestParam(defaultValue = "1") long current,
                                                    @RequestParam(defaultValue = "20") long size) {
        return ApiResult.ok(commentService.listReplies(rootId, current, size));
    }

    /**
     * 发表评论
     *
     * <p>发顶层评论还是回复看 {@code parentId}；{@code rootId} 由服务端从 parentId 推导，客户端传了也不认。</p>
     */
    @PostMapping
    public ApiResult<CommentView> publish(@Valid @RequestBody CommentCreateRequest request) {
        return ApiResult.ok(commentService.publish(request, UserContext.getUserId()));
    }

    /**
     * 删除评论
     *
     * <p>作者本人可删自己的，持有 {@code comment:delete} 的审核者可删任何一条。</p>
     * <p>
     * 这里不用 {@code @PreAuthorize}：它表达不了「本人「或」有权限」，
     * 而把删除接口整体锁成审核者专用又会让用户删不掉自己的评论。
     * 所以由控制器算出审核者身份，交给 service 做归属判定。
     * </p>
     */
    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(@PathVariable Long id) {
        List<String> permissions = UserContext.getPermissions();
        boolean moderator = permissions != null && permissions.contains(PERM_DELETE);
        commentService.delete(id, UserContext.getUserId(), moderator);
        return ApiResult.ok();
    }

    /**
     * 评论分页
     *
     * <p>后台「评论列表」用：跨视频全量，含审核中的和楼中楼回复。</p>
     * <p>
     * {@code comment:list} 这个权限位从建表起就挂在菜单 6 上，类注释里也写明了它管的是
     * 后台评论管理页的可见性，但一直没有接口真正消费它——审核者只能一个视频一个视频地翻。
     * </p>
     */
    @GetMapping("/admin/page")
    @PreAuthorize("hasAuthority('comment:list')")
    public ApiResult<Page<CommentView>> pageForAdmin(@RequestParam(required = false) String keyword,
                                                     @RequestParam(required = false) Integer status,
                                                     @RequestParam(defaultValue = "1") long current,
                                                     @RequestParam(defaultValue = "10") long size) {
        return ApiResult.ok(commentService.pageForAdmin(keyword, status, current, size));
    }

    /**
     * 审核评论
     *
     * <p>把评论置为正常(1)或审核中(2)。</p>
     */
    @PutMapping("/{id}/audit")
    @PreAuthorize("hasAuthority('comment:audit')")
    @OperLog(title = "评论管理", type = BusinessType.AUDIT)
    public ApiResult<Void> audit(@PathVariable Long id, @RequestParam int status) {
        commentService.audit(id, status);
        return ApiResult.ok();
    }
}
