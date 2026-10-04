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
 * 算法重跑是**刷新分数**而不是追加一条，否则 feed 里会出现重复视频。
 * </p>
 * <p>
 * {@code is_exposed} 只能从 0 翻到 1，不会翻回去——曝光是一次性事实。
 * 因此批量写入的 SQL 里刻意**不**碰 {@code is_exposed} / {@code is_clicked}，
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

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

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

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
