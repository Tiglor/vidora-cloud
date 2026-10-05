package org.tiglor.system.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.IService;
import org.tiglor.common.log.entity.OperLogEntity;
import org.tiglor.system.vo.OperLogVO;

import java.time.LocalDateTime;

public interface OperLogService extends IService<OperLogEntity> {

    /**
     * 管理端分页。模块名模糊匹配，业务类型 / 操作人 / 结果精确匹配，时间按倒序翻。
     * <p>
     * 审计表只增不改，所以筛选条件必须够用：出事后靠「谁在什么时间段动了哪个模块」定位，
     * 少一个条件就是让人在几万行里手工翻。
     */
    Page<OperLogVO> pageLogs(long current, long size, String title, Integer businessType,
                             Long operUserId, Integer status, LocalDateTime beginTime, LocalDateTime endTime);

    /**
     * 单条详情，含列表刻意跳过的大字段；查不到返回 null。
     * <p>
     * 返回类型和列表保持一致：操作人昵称的兜底规则只能有一处，否则列表显示「用户#7」、
     * 详情却是空白，看的人会以为这条记录被人改过。
     */
    OperLogVO detail(Long id);
}
