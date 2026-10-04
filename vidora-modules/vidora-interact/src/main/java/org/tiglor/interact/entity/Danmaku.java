package org.tiglor.interact.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 弹幕。
 * <p>
 * 不继承 {@code BaseEntity}：表上既没有 is_deleted 也没有 update_time——弹幕是只追加的时间线数据，
 * 屏蔽靠 status，不靠逻辑删除。
 * </p>
 */
@Data
@TableName("interact_danmaku")
public class Danmaku implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long videoId;

    private Long userId;

    private String content;

    /** 弹幕在视频里出现的时间点（秒，保留 3 位小数），DECIMAL 避免 float 累积误差导致排序抖动 */
    private BigDecimal appearTime;

    /** 十六进制颜色，如 #FFFFFF */
    private String color;

    private Integer fontSize;

    /** 位置：0-滚动 1-顶部 2-底部 */
    private Integer position;

    /** 状态：0-屏蔽 1-正常 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
