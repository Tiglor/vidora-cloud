package org.tiglor.search.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 一个用户对一个关键词的搜索历史，{@code uk_user_keyword(user_id, keyword)} 唯一。
 * <p>
 * 写入是 upsert：同一个词搜第二次不会多出一行，只是 {@code search_count + 1}、
 * {@code last_search_time} 往前推。所以这张表的行数是「这个人搜过多少个不同的词」，
 * 不是「搜了多少次」——按时间倒序翻页时同一屏不会出现重复的词。
 * </p>
 * <p>
 * DDL 里 {@code user_id DEFAULT 0} 注释写的是「0 为游客」，但代码**从不写 0**：
 * 所有游客会挤进同一批 {@code (0, keyword)} 行里，那既不是任何人的历史，
 * 一旦被 {@code myHistory} 读到就等于把全站游客的搜索词摊给一个人看。
 * 游客的搜索只计入 {@link SearchKeywordStat}，不落这张表。
 * </p>
 */
@Data
@TableName("search_history")
public class SearchHistory implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String keyword;

    /** 这个词被这个人搜过多少次，由 upsert 语句累加，不接受外部传值 */
    private Integer searchCount;

    /**
     * 最后一次搜索时间，由 DDL 的 {@code ON UPDATE CURRENT_TIMESTAMP} 维护，
     * upsert 语句不写它。这是「历史」列表的排序键。
     */
    private LocalDateTime lastSearchTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
