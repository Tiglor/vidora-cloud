package org.tiglor.interact.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.tiglor.common.core.BizException;
import org.tiglor.common.core.ResultCode;
import org.tiglor.interact.dto.DanmakuSendRequest;
import org.tiglor.interact.entity.Danmaku;
import org.tiglor.interact.mapper.DanmakuMapper;
import org.tiglor.interact.service.DanmakuService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DanmakuServiceImpl extends ServiceImpl<DanmakuMapper, Danmaku> implements DanmakuService {

    private static final int STATUS_BLOCKED = 0;
    private static final int STATUS_NORMAL = 1;

    /** 与 DDL 上的列默认值保持一致：显式填好再插，返回给前端的对象才是完整的 */
    private static final String DEFAULT_COLOR = "#FFFFFF";
    private static final int DEFAULT_FONT_SIZE = 25;
    private static final int DEFAULT_POSITION = 0;

    /** appear_time 是 DECIMAL(10,3)，多出来的小数位会被 MySQL 静默截断，不如自己先对齐 */
    private static final int TIME_SCALE = 3;

    @Override
    public Danmaku send(DanmakuSendRequest request, Long userId) {
        if (userId == null) {
            throw new BizException(ResultCode.UNAUTHORIZED, "未登录");
        }
        Danmaku danmaku = new Danmaku();
        danmaku.setVideoId(request.getVideoId());
        danmaku.setUserId(userId);
        danmaku.setContent(request.getContent().trim());
        danmaku.setAppearTime(request.getAppearTime().setScale(TIME_SCALE, RoundingMode.HALF_UP));
        danmaku.setColor(isBlank(request.getColor()) ? DEFAULT_COLOR : request.getColor().toUpperCase());
        danmaku.setFontSize(request.getFontSize() == null ? DEFAULT_FONT_SIZE : request.getFontSize());
        danmaku.setPosition(request.getPosition() == null ? DEFAULT_POSITION : request.getPosition());
        danmaku.setStatus(STATUS_NORMAL);
        save(danmaku);
        return danmaku;
    }

    @Override
    public List<Danmaku> listByVideo(Long videoId, BigDecimal fromTime, BigDecimal toTime, int limit) {
        if (videoId == null) {
            throw new BizException(ResultCode.VALIDATE_FAILED, "videoId 不能为空");
        }
        if (fromTime != null && toTime != null && fromTime.compareTo(toTime) > 0) {
            throw new BizException(ResultCode.VALIDATE_FAILED, "时间窗口起点不能晚于终点");
        }
        return lambdaQuery()
                .eq(Danmaku::getVideoId, videoId)
                .eq(Danmaku::getStatus, STATUS_NORMAL)
                .ge(fromTime != null, Danmaku::getAppearTime, fromTime)
                .le(toTime != null, Danmaku::getAppearTime, toTime)
                // 走 idx_video_time(video_id, appear_time)：过滤和排序都用同一个索引，不用额外排序
                .orderByAsc(Danmaku::getAppearTime)
                .orderByAsc(Danmaku::getId)
                .last("LIMIT " + Math.min(Math.max(limit, 1), MAX_PER_LOAD))
                .list();
    }

    @Override
    public void setStatus(Long danmakuId, int status) {
        if (status != STATUS_BLOCKED && status != STATUS_NORMAL) {
            throw new BizException(ResultCode.VALIDATE_FAILED, "status 只能是 0-屏蔽 或 1-正常");
        }
        boolean updated = lambdaUpdate()
                .set(Danmaku::getStatus, status)
                .eq(Danmaku::getId, danmakuId)
                .ne(Danmaku::getStatus, status)
                .update();
        if (!updated && getById(danmakuId) == null) {
            throw new BizException(ResultCode.NOT_FOUND, "弹幕不存在：" + danmakuId);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
