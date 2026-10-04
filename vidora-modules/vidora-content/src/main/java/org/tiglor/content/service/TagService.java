package org.tiglor.content.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.IService;
import org.tiglor.content.entity.Tag;

import java.util.List;

public interface TagService extends IService<Tag> {

    /**
     * {@link #hot} / {@link #suggest} 的条数上限。
     * <p>
     * 调用方必须先把入参夹到这个范围内再传进来：{@code hot} 的 limit 同时是缓存 key，
     * 不设上限的话外部随便传几个不同的整数就能往 Redis 里塞任意多条缓存。
     * </p>
     */
    int MAX_LIMIT = 50;

    /** 热门标签（标签云），只取启用状态，按 use_count 倒序 */
    List<Tag> hot(int limit);

    /** 上传页的标签联想，按名字前缀匹配；keyword 为空直接返回空列表 */
    List<Tag> suggest(String keyword, int limit);

    Page<Tag> page(long current, long size, String keyword, Integer status);

    /** 建标签。名字全站唯一，且唯一键是大小写不敏感的 */
    Tag create(String name);

    void setStatus(Long id, int status);

    /** 删除；还有视频在用（use_count > 0）时拒绝 */
    void delete(Long id);
}
