package org.tiglor.video.entity;

import org.tiglor.common.core.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("video_info")
public class VideoInfo extends BaseEntity {

    /** 业务唯一 key（UUID），对外标识视频用它；HLS 产物在对象存储里也以它建目录 */
    private String videoKey;
    /** 投稿用户 ID，一律取自网关透传的登录态，不接受前端传入 */
    private Long userId;
    /** 标题。上传或合并时留空则回退成原始文件名，文件名也是空的才是「未命名视频」 */
    private String title;
    /** 简介，纯文本，没填就是 null */
    private String description;
    /**
     * 封面地址：转码成功抽帧时才回填，在此之前为 null，所以发布前的视频没有封面。
     * <p>
     * 它是 endpoint/bucket/objectName 拼出来的直链，不带签名，bucket 必须公开读或经 CDN 回源。
     * </p>
     */
    private String coverUrl;
    /** 时长（秒），落库时用 ffprobe 探测；ffprobe 不可用会降级跳过，此时是 0 而不是真实时长 */
    private Integer duration;
    /** 画面宽（像素），来源与探测降级同上，探测失败时为 null */
    private Integer width;
    /** 画面高（像素），来源与探测降级同上，探测失败时为 null */
    private Integer height;
    /** 源片字节数；分片上传路径取会话里登记的 fileSize，不是实际落盘大小 */
    private Long fileSize;
    /**
     * 整文件摘要值（一般是 MD5），秒传按它匹配。
     * <p>故意不建唯一索引：秒传复用的是存储层 blob，同一文件被不同人各投一份是合法的。</p>
     */
    private String fileHash;
    /** 存储对象名（MinIO objectName 或本地相对路径） */
    private String storagePath;
    /** 转码后的 HLS 播放索引（m3u8）地址 */
    private String hlsUrl;
    /**
     * 状态：0-上传中 1-转码中 2-审核中 3-已发布 4-已下架。
     * <p>
     * 实际写入只有两档：落库时置 0，转码成功置 3；1、2、4 在代码里没有任何赋值路径，
     * 也还没有审核与下架接口。所以「卡在 0」既可能是真在等转码，也可能是转码已经失败终结。
     * </p>
     */
    private Integer status;
    /**
     * 可见性：0-私密 1-公开 2-仅粉丝。当前没有任何接口写过它，一律是建表默认值 1，
     * 列表也只按 status 过滤——这个字段还不参与任何鉴权。
     */
    private Integer visibility;
    /** 所属分类，对应 content_category.id。只是取值，没有外键也不校验存在性，传不存在的分类只会让它筛不出来 */
    private Long categoryId;
    /** 播放数。插入时写死 0，之后不再回写：真实计数在 interact 服务的按天表里，这里目前是常量列 */
    private Integer playCount;
    /** 点赞数，同上，插入时写死 0 且无人累加 */
    private Integer likeCount;
    /** 评论数，同上，插入时写死 0 且无人累加 */
    private Integer commentCount;
    /** 分享数，同上，插入时写死 0 且无人累加 */
    private Integer shareCount;
    /**
     * 发布时间：只在转码成功那一刻回填，之前一直是 null。
     * <p>公开列表按它倒序排，所以首页的顺序等于「转码完成的先后」，跟上传早晚不是一回事。</p>
     */
    private LocalDateTime publishTime;
}
