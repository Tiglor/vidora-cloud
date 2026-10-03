package org.tiglor.interact.controller;

import org.tiglor.interact.entity.Comment;
import org.tiglor.interact.service.CommentService;
import org.tiglor.common.core.security.UserContext;
import org.tiglor.common.core.ApiResult;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/comments")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService service;

    @GetMapping("/video/{videoId}")
    public ApiResult<Page<Comment>> listByVideo(@PathVariable Long videoId,
                                                @RequestParam(defaultValue = "1") long current,
                                                @RequestParam(defaultValue = "20") long size) {
        return ApiResult.ok(service.page(new Page<>(current, size),
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Comment>()
                        .eq(Comment::getVideoId, videoId)
                        .eq(Comment::getStatus, 1)
                        .eq(Comment::getParentId, 0)
                        .orderByDesc(Comment::getCreateTime)));
    }

    @GetMapping("/page")
    public ApiResult<Page<Comment>> page(@RequestParam(defaultValue = "1") long current,
                                           @RequestParam(defaultValue = "10") long size) {
        return ApiResult.ok(service.page(new Page<>(current, size)));
    }

    @GetMapping("/{id}")
    public ApiResult<Comment> getById(@PathVariable Long id) {
        return ApiResult.ok(service.getById(id));
    }

    @PostMapping
    public ApiResult<Boolean> save(@RequestBody Comment entity) {
        Long userId = UserContext.getUserId();
        if (userId == null || entity.getVideoId() == null || entity.getContent() == null
                || entity.getContent().isBlank()) {
            return ApiResult.error(400, "视频和评论内容不能为空");
        }
        entity.setUserId(userId);
        entity.setParentId(entity.getParentId() == null ? 0L : entity.getParentId());
        entity.setRootId(entity.getRootId() == null ? 0L : entity.getRootId());
        entity.setLikeCount(0L);
        entity.setReplyCount(0);
        entity.setStatus(1);
        return ApiResult.ok(service.save(entity));
    }

    @PutMapping("/{id}")
    public ApiResult<Boolean> update(@PathVariable Long id, @RequestBody Comment entity) {
        entity.setId(id);
        return ApiResult.ok(service.updateById(entity));
    }

    @DeleteMapping("/{id}")
    public ApiResult<Boolean> remove(@PathVariable Long id) {
        return ApiResult.ok(service.removeById(id));
    }
}
