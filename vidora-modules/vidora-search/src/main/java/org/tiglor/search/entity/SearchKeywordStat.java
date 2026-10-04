package org.tiglor.search.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 一个关键词一天的搜索统计，{@code uk_keyword_date(keyword, stat_date)} 唯一。
 * <p>
 * 按天分行而不是一个词一行：热词的生命周期就是一天，昨天的「双十一」和今天的「双十一」
 * 是两个不同的热度，合成一列就再也拆不回来了。要算周榜月榜在查询侧按 {@code stat_date} 聚合。
 * </p>
 * <p>
 * {@code resultCount} 是**平均结果数**不是总数（DDL 注释就是这么写的），
 * 由 upsert 语句用增量平均公式维护，见 {@code SearchKeywordStatMapper.upsertStat}。
 * 它的用途是找出「搜的人多但搜不到东西」的词——那批词是内容缺口，该去补片源，
 * 而不是当成热词推给用户。
 * </p>
 * <p>
 * 表上没有 {@code update_time}：{@code stat_date} 已经说明了这一行属于哪天，
 * 而当天这一行会被反复更新，最后一次更新的时间点没有分析价值。
 * </p>
 */
@Data
@TableName("search_keyword_stat")
public class SearchKeywordStat implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String keyword;

    /** 当天这个词被搜了多少次 */
    private Long searchCount;

    /** 当天这个词的平均结果数，整数除法会截断小数 */
    private Long resultCount;

    private LocalDate statDate;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
