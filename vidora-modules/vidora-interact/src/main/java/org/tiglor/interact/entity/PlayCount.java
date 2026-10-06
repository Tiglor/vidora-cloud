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
 * 视频计数的「按天」快照，{@code uk_video_date(video_id, stat_date)} 保证一视频一天一行。
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

    /** 主键，数据库自增；业务上定位一行靠 (videoId, statDate) 这条唯一键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 计数归属的视频，与 {@link #statDate} 一起构成唯一键 */
    private Long videoId;

    /**
     * 当天的播放次数，不是历史累计——累计值要把该视频所有日行的本列 SUM 起来。
     * <p>同一用户对同一视频的重复上报在 5 分钟窗口内只算一次（Redis 不可用时退化为照计）；
     * 匿名播放没有稳定身份可去重，全部累加。</p>
     */
    private Long playCount;

    /**
     * 当天净点赞数：点赞 +1、取消 -1，所以它统计的是「当前还挂着多少赞」，不是点赞发生过多少次。
     * <p>由 {@code interact_action} 状态真正翻转时推过来；收藏不进任何计数列，分享只进 {@link #shareCount}。</p>
     */
    private Long likeCount;

    /** 当天净评论数，发评论 +1、删评论 -1，与 {@link #likeCount} 一样会被负增量往下拉 */
    private Long commentCount;

    /**
     * 当天分享次数。分享被视为「发生过的事」不支持取消，所以这一列只增不减，
     * 同一用户重复上报会各计一次。
     */
    private Long shareCount;

    /**
     * 这一行代表的自然日，由服务端取本地当天日期写入，客户端无法指定。
     * <p>跨零点的那次请求会落到新的一天，边界上差一次计数换来的是不需要协商「算哪一天」。
     * 一旦跨过当天，这行就再也不被更新。</p>
     */
    private LocalDate statDate;

    /** 这一天的首条计数落库的时刻，即当天第一次有播放/点赞/评论事件的时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 当天最后一次计数变化的时间；过了零点这行不再被写，它就定格在当天末尾 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
