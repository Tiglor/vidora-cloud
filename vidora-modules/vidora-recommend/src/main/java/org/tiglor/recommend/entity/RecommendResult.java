package org.tiglor.recommend.entity;

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
 * 一条「给某用户在某场景下推荐某视频」的候选记录，由离线算法任务批量写入。
 * <p>
 * {@code uk_user_video_scene} 决定了同一个视频在同一场景下对同一用户只有一行：
 * 算法重跑是「刷新分数」而不是追加一条，否则 feed 里会出现重复视频。
 * </p>
 * <p>
 * {@code is_exposed} 只能从 0 翻到 1，不会翻回去——曝光是一次性事实。
 * 因此批量写入的 SQL 里刻意「不」碰 {@code is_exposed} / {@code is_clicked}，
 * 否则一次重算就会把「已经给用户看过」的历史抹掉，同一个视频被反复推给同一个人。
 * </p>
 * <p>
 * 表上没有 {@code update_time}，所以只知道这条候选是什么时候生成的，
 * 不知道它是什么时候被曝光、什么时候被点击的。按天算 CTR 需要这个时间戳，见 ARCHITECTURE 的待完善清单。
 * </p>
 */
@Data
@TableName("recommend_result")
public class RecommendResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 未曝光 / 未点击 */
    public static final int FLAG_NO = 0;
    /** 已曝光 / 已点击 */
    public static final int FLAG_YES = 1;

    /** 主键，数据库自增。批量重算按 {@code uk_user_video_scene}（userId + videoId + scene）定位并复用原行，不认调用方传进来的 id */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 归属用户。批量写入时由算法任务在请求体里给出、不取登录态；feed / history 侧才钉在登录态上 */
    private Long userId;

    /** 候选视频，和 {@code (userId, scene)} 一起构成唯一键，所以同一视频在同一场景下对同一人只有一行；不校验视频是否已下架 */
    private Long videoId;

    /** 推荐场景，取值见 {@link org.tiglor.recommend.enums.RecommendScene} */
    private String scene;

    /** 推荐得分，DECIMAL(10,6)，上限 9999.999999 */
    private BigDecimal score;

    /** 产出这条候选的算法，取值见 {@link org.tiglor.recommend.enums.AlgoType} */
    private String algoType;

    /** 是否已曝光：0-未曝光 1-已曝光 */
    private Integer isExposed;

    /** 是否点击：0-未点击 1-已点击 */
    private Integer isClicked;

    /** 第一次入池的时刻。批量重算的 {@code INSERT ... ON DUPLICATE KEY UPDATE} 列清单里没有这一列（由 DDL 的 {@code DEFAULT CURRENT_TIMESTAMP} 给），更新分支也只刷 score 与 algo_type，所以复用旧行时它不被改写 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
