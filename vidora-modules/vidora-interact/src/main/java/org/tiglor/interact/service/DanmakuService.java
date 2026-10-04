package org.tiglor.interact.service;

import org.tiglor.interact.dto.DanmakuSendRequest;
import org.tiglor.interact.entity.Danmaku;

import java.math.BigDecimal;
import java.util.List;

/**
 * 弹幕。
 * <p>
 * 弹幕是「按视频一次性拉全量时间线」的读模型，不是分页列表：播放器需要在任意进度
 * 立即知道该显示哪些弹幕。所以读取接口按 appear_time 升序返回，并对单次条数设硬上限，
 * 超长视频由客户端按时间窗口分段拉取。
 * </p>
 */
public interface DanmakuService {

    /** 单次拉取上限。热门视频弹幕可达百万条，不设上限一个请求就能打爆内存 */
    int MAX_PER_LOAD = 3000;

    /** 发送弹幕，服务端补默认样式并落库 */
    Danmaku send(DanmakuSendRequest request, Long userId);

    /**
     * 拉取某视频的弹幕时间线（只含未屏蔽的）。
     *
     * @param fromTime 起始秒（含），null 表示从头
     * @param toTime   结束秒（含），null 表示到尾
     * @param limit    条数上限，超过 {@link #MAX_PER_LOAD} 会被压到上限
     */
    List<Danmaku> listByVideo(Long videoId, BigDecimal fromTime, BigDecimal toTime, int limit);

    /** 屏蔽 / 恢复弹幕（审核用）。status：0-屏蔽 1-正常 */
    void setStatus(Long danmakuId, int status);
}
