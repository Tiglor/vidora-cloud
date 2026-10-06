package org.tiglor.search.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
import org.tiglor.search.dto.SearchRecordRequest;
import org.tiglor.search.entity.SearchHistory;
import org.tiglor.search.service.SearchHistoryService;

/**
 * 我的搜索历史。全部接口只要登录，不占权限位——这是用户自己的数据。
 * <p>
 * {@code userId} 一律取自登录态，不接受请求参数。
 * </p>
 * <p>
 * 这里替换掉了原先的壳控制器，它五个方法全都不带用户过滤：
 * {@code GET /search/page} 任何登录用户都能翻遍全站的搜索历史（这张表是「谁搜过什么」，
 * 全项目最接近隐私的一张），{@code GET/DELETE /search/{id}} 不校验归属，
 * {@code POST /search} 收裸 {@code SearchHistory} 实体（调用方自己填 userId 和 searchCount
 * 就能伪造任何人的历史），{@code PUT /search/{id}} 能把任意行改成任意用户的任意词。
 * </p>
 */
@RestController
@RequestMapping("/search")
@RequiredArgsConstructor
public class SearchHistoryController {

    private final SearchHistoryService service;

    /**
     * 记录一次搜索
     *
     * <p>回报一次搜索，同时写我的历史和当天的全站词频</p>
     * <p>
     * 检索本身还没落地（ES 是独立的待完成项），所以先由调用方在拿到结果之后回报。
     * 等 ES 接上，这一步会挪进检索接口内部，对外的上报入口就撤掉。
     * </p>
     */
    @PostMapping("/record")
    public ApiResult<Void> record(@Valid @RequestBody SearchRecordRequest request) {
        service.record(UserContext.getUserId(), request.getKeyword(),
                request.getResultCount() == null ? 0L : request.getResultCount());
        return ApiResult.ok();
    }

    /**
     * 查询我的搜索历史
     *
     * <p>按最后搜索时间倒序</p>
     */
    @GetMapping("/history")
    public ApiResult<Page<SearchHistory>> history(@RequestParam(defaultValue = "1") long current,
                                                 @RequestParam(defaultValue = "20") long size) {
        return ApiResult.ok(service.myHistory(UserContext.getUserId(), current, size));
    }

    /**
     * 删除我的一条搜索历史
     *
     * <p>删掉我的一条历史</p>
     *
     * @return false 表示这一行不是我的、或者已经删掉了，不报 404——前端只是想让这个标签消失
     */
    @DeleteMapping("/history/{id}")
    public ApiResult<Boolean> remove(@PathVariable Long id) {
        return ApiResult.ok(service.removeMine(UserContext.getUserId(), id));
    }

    /**
     * 清空我的搜索历史
     *
     * <p>清空我的全部历史</p>
     *
     * @return 删除行数
     */
    @DeleteMapping("/history")
    public ApiResult<Integer> clear() {
        return ApiResult.ok(service.clearMine(UserContext.getUserId()));
    }
}
