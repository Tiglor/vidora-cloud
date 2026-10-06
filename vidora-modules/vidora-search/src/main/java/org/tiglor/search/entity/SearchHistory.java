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
 * DDL 里 {@code user_id DEFAULT 0} 注释写的是「0 为游客」，但代码「从不写 0」：
 * 所有游客会挤进同一批 {@code (0, keyword)} 行里，那既不是任何人的历史，
 * 一旦被 {@code myHistory} 读到就等于把全站游客的搜索词摊给一个人看。
 * 游客的搜索只计入 {@link SearchKeywordStat}，不落这张表。
 * </p>
 */
@Data
@TableName("search_history")
public class SearchHistory implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 自增主键，删除单条历史时按它定位，同时必须匹配当前用户 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 归属用户，一律取自登录态；未登录根本写不进这张表，所以代码写入的行不会出现 0 */
    private Long userId;

    /**
     * 关键词：入库前去掉首尾空白、把连续空白压成一个空格，大小写按第一次搜的那种拼法保留。
     * <p>唯一键本身大小写不敏感，所以 {@code Java} 和 {@code java} 是同一行，后来者只加计数。</p>
     */
    private String keyword;

    /** 这个词被这个人搜过多少次，由 upsert 语句累加，不接受外部传值 */
    private Integer searchCount;

    /**
     * 最后一次搜索时间，由 DDL 的 {@code ON UPDATE CURRENT_TIMESTAMP} 维护，
     * upsert 语句不写它。这是「历史」列表的排序键。
     */
    private LocalDateTime lastSearchTime;

    /** 这个词第一次出现在历史里的时间，之后同一行再被搜也不更新——所以列表排的是 lastSearchTime */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
