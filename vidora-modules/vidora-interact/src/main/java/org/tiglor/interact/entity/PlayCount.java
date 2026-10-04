package org.tiglor.interact.entity;

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
 * 视频计数的**按天**快照，{@code uk_video_date(video_id, stat_date)} 保证一视频一天一行。
 * <p>
 * 不继承 {@code BaseEntity}：表上没有 is_deleted 列。
 * </p>
 * <p>
 * 存在的意义是把高频计数从 video_info 那一行上摘下来：播放数每次点击都要 +1，
 * 直接更新视频主表会让热门视频的那一行变成写热点。这里按天分散成多行，
 * 总数由 {@code SUM} 或缓存给出，video_info 上的冗余计数只需定期回写。
 * </p>
 */
@Data
@TableName("interact_play_count")
public class PlayCount implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long videoId;

    private Long playCount;

    private Long likeCount;

    private Long commentCount;

    private Long shareCount;

    private LocalDate statDate;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
