package org.tiglor.search.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.IService;
import org.tiglor.search.entity.SearchHistory;

/**
 * 用户搜索历史。
 * <p>
 * 每个方法都以 {@code userId} 为边界——这张表里存的是「谁搜过什么」，
 * 是所有表里最接近隐私的一张。原先的壳控制器五个方法全都不带用户过滤，
 * 任何登录用户都能翻遍全站搜索历史、还能收裸实体改任意一行。
 * </p>
 */
public interface SearchHistoryService extends IService<SearchHistory> {

    /**
     * 记一次搜索：既写这个人的历史，也写当天的全站词频统计。
     * <p>
     * 两张表都是 upsert，重复调用会「重复计数」——计数器本来就没有幂等键，
     * 客户端重试一次就多算一次，这是可接受的误差，不值得为它引入去重表。
     * </p>
     *
     * @param userId      登录用户，为 null 直接 401；游客不落历史（见实体注释）
     * @param keyword     原始关键词，服务层会 trim 并校验
     * @param resultCount 这次搜到多少条结果，负数按 0 处理
     */
    void record(Long userId, String keyword, long resultCount);

    /** 我的历史，按最后搜索时间倒序。返回的整行里带 searchCount，前端要按次数排自己排 */
    Page<SearchHistory> myHistory(Long userId, long current, long size);

    /**
     * 删掉我的一条历史。
     *
     * @return false 表示这一行不是我的、或者已经删掉了——两种情况都不该报 404，
     *         前端只是想让这个标签消失
     */
    boolean removeMine(Long userId, Long id);

    /** 清空我的全部历史，返回删除行数 */
    int clearMine(Long userId);
}
