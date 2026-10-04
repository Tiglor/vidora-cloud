package org.tiglor.content.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.tiglor.common.core.BizException;
import org.tiglor.common.core.ResultCode;
import org.tiglor.common.redis.CacheNames;
import org.tiglor.content.entity.Tag;
import org.tiglor.content.mapper.TagMapper;
import org.tiglor.content.service.TagService;

import java.util.List;

/**
 * 视频标签。
 * <p>
 * 这里只维护标签字典本身；{@code use_count} 的加减在 video-service（打标签 / 撤标签）那边，
 * 本模块不提供任意增减的入口——那等于把计数开放给任何能调到这里的人。
 * </p>
 */
@Service
public class TagServiceImpl extends ServiceImpl<TagMapper, Tag> implements TagService {

    private static final long MAX_PAGE_SIZE = 100L;

    /** 标签云和联想框都用不到更多；同时也是缓存 key 的取值上限 */
    private static final int MAX_LIMIT = 50;

    private static final int STATUS_ENABLED = 1;

    @Override
    // 命中 idx_status_count(status, use_count)，不用回表排序
    @Cacheable(cacheNames = CacheNames.TAG_HOT, key = "#limit")
    public List<Tag> hot(int limit) {
        return lambdaQuery()
                .eq(Tag::getStatus, STATUS_ENABLED)
                .orderByDesc(Tag::getUseCount)
                .orderByAsc(Tag::getId)
                .page(new Page<>(1, clampLimit(limit)))
                .getRecords();
    }

    @Override
    public List<Tag> suggest(String keyword, int limit) {
        String prefix = trimToNull(keyword);
        if (prefix == null) {
            // 空前缀就是「列出全部标签」，联想框里毫无意义，还会白白扫一遍表
            return List.of();
        }
        // likeRight 生成 name LIKE 'kw%'，前缀匹配能走 uk_name
        return lambdaQuery()
                .eq(Tag::getStatus, STATUS_ENABLED)
                .likeRight(Tag::getName, prefix)
                .orderByDesc(Tag::getUseCount)
                .orderByAsc(Tag::getId)
                .page(new Page<>(1, clampLimit(limit)))
                .getRecords();
    }

    @Override
    public Page<Tag> page(long current, long size, String keyword, Integer status) {
        String fuzzy = trimToNull(keyword);
        return lambdaQuery()
                .like(fuzzy != null, Tag::getName, fuzzy)
                .eq(status != null, Tag::getStatus, status)
                .orderByDesc(Tag::getUseCount)
                .orderByAsc(Tag::getId)
                .page(new Page<>(Math.max(current, 1), clampSize(size)));
    }

    @Override
    @CacheEvict(cacheNames = CacheNames.TAG_HOT, allEntries = true)
    public Tag create(String name) {
        String normalized = trimToNull(name);
        if (normalized == null) {
            throw new BizException(ResultCode.VALIDATE_FAILED, "标签名不能为空");
        }
        // uk_name 建在 _ci 排序规则的列上，等值比较同样大小写不敏感，和库里的唯一性判定一致。
        // 用 Java 的 equalsIgnoreCase 去比也一样，但那样就得把整表拉出来
        if (lambdaQuery().eq(Tag::getName, normalized).count() > 0) {
            throw new BizException(ResultCode.VALIDATE_FAILED, "标签「" + normalized + "」已经存在");
        }
        Tag tag = new Tag();
        tag.setName(normalized);
        tag.setUseCount(0L);
        tag.setStatus(STATUS_ENABLED);
        save(tag);
        return tag;
    }

    @Override
    @CacheEvict(cacheNames = CacheNames.TAG_HOT, allEntries = true)
    public void setStatus(Long id, int status) {
        requireStatus(status);
        requireExists(id);
        lambdaUpdate()
                .set(Tag::getStatus, status)
                .eq(Tag::getId, id)
                .ne(Tag::getStatus, status)
                .update();
    }

    @Override
    @CacheEvict(cacheNames = CacheNames.TAG_HOT, allEntries = true)
    public void delete(Long id) {
        Tag tag = getById(id);
        if (tag == null) {
            throw new BizException(ResultCode.NOT_FOUND, "标签不存在：" + id);
        }
        // 删掉还在用的标签，视频侧的 tags 数组里就留下一个查不到名字的 id
        if (tag.getUseCount() != null && tag.getUseCount() > 0) {
            throw new BizException(ResultCode.VALIDATE_FAILED,
                    "还有 " + tag.getUseCount() + " 个视频在用这个标签，请改成禁用而不是删除");
        }
        removeById(id);
    }

    private void requireExists(Long id) {
        if (getById(id) == null) {
            throw new BizException(ResultCode.NOT_FOUND, "标签不存在：" + id);
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static void requireStatus(int status) {
        if (status != 0 && status != 1) {
            throw new BizException(ResultCode.VALIDATE_FAILED, "status 只能是 0-禁用 或 1-启用");
        }
    }

    private static int clampLimit(int limit) {
        return (int) Math.min(Math.max(limit, 1), MAX_LIMIT);
    }

    private static long clampSize(long size) {
        return Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    }
}
