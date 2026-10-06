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

    /** 主键，数据库自增。弹幕的读取按时间窗走 {@code idx_video_time}，翻页游标是 appearTime 不是 id */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 弹幕挂在哪条视频的时间线上；读取按 (videoId, appearTime) 走同一条索引 */
    private Long videoId;

    /** 发送人，取当前登录用户；未登录直接拒发，所以这一列不会是 null */
    private Long userId;

    /** 弹幕文本，入库前已 trim；非空与长度上限在请求 DTO 上校验 */
    private String content;

    /** 弹幕在视频里出现的时间点（秒，保留 3 位小数），DECIMAL 避免 float 累积误差导致排序抖动 */
    private BigDecimal appearTime;

    /** 十六进制颜色，如 #FFFFFF */
    private String color;

    /** 渲染用的字号（px）。客户端省略时服务端补默认值 25，与列默认值一致；发送接口把取值夹在 12~48 */
    private Integer fontSize;

    /** 位置：0-滚动 1-顶部 2-底部 */
    private Integer position;

    /** 状态：0-屏蔽 1-正常 */
    private Integer status;

    /** 发送的真实墙钟时刻，插入时由 MyBatis-Plus 填充。它和 {@code appearTime} 是两个量：前者是「什么时候发的」，后者是「出现在视频的第几秒」 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
