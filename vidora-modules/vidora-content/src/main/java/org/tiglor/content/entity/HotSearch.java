package org.tiglor.content.entity;

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
 * 某一天的热搜榜单，{@code uk_keyword_date(keyword, rank_date)} 决定一个词一天只有一行。
 * <p>
 * <b>{@code rank} 是 MySQL 8 的保留字</b>（窗口函数 RANK()），所以字段上必须写
 * {@code @TableField("`rank`")} 把反引号带进列名。不加的话 MyBatis-Plus 生成的
 * {@code ORDER BY rank ASC} 会直接报语法错误——而 DDL 里因为有反引号是能建表的，
 * 光看建表脚本发现不了这个坑。
 * </p>
 * <p>
 * 表上没有 {@code update_time}：榜单是按天生成的快照，一天之内的改动（下线敏感词、重算排名）
 * 不构成需要追溯的业务时间线。
 * </p>
 */
@Data
@TableName("content_hot_search")
public class HotSearch implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String keyword;

    private Integer heatScore;

    /** 榜单排名，1 开始；由 {@code HotSearchService#rebuild} 按热度重算，不接受外部直接指定 */
    @TableField("`rank`")
    private Integer rank;

    private Long searchCount;

    /** 状态：0-下线 1-上线 */
    private Integer status;

    private LocalDate rankDate;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
