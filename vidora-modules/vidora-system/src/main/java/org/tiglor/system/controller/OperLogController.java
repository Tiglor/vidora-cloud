package org.tiglor.system.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.tiglor.common.core.ApiResult;
import org.tiglor.system.service.OperLogService;
import org.tiglor.system.vo.OperLogVO;

import java.time.LocalDateTime;

/**
 * 操作审计日志查询。
 * <p>
 * 刻意没有删除与清空接口：能被人随手清空的审计表等于没有审计表，
 * 「谁删了视频」和「谁删了那条记录」必须是两件事。保留期由归档任务管，不归管理端按钮管。
 */
@RestController
@RequestMapping("/oper-logs")
@RequiredArgsConstructor
public class OperLogController {

    private final OperLogService operLogService;

    /**
     * 操作日志分页
     *
     * <p>管理端查操作审计，可按模块标题、业务类型、操作人、结果与时间区间筛，最新的排最前。</p>
     * <p>
     * 列表刻意不查 {@code operParam} 与 {@code jsonResult} 这两个 2000 字符级别的大字段，要看内容请走详情接口；
     * 返回里的 {@code operUserName} 是查询侧现补的昵称，查不到人的兜底显示为「用户#ID」，没有操作人则是「系统」。
     *
     * @param businessType 业务类型：0-其他 1-新增 2-修改 3-删除 4-授权 5-导出 6-导入 7-清空 8-审核 9-状态变更
     * @param status       执行结果：0-失败，1-成功；失败那条的根因在 {@code errorMsg}
     * @param beginTime    起始时间（含），ISO-8601 日期时间格式，如 2024-06-01T00:00:00
     * @param endTime      截止时间（含），格式同上；两端可单独使用
     *
     * @return 分页对象，records 为大字段置空后的日志行
     */
    @GetMapping("/page")
    @PreAuthorize("hasAuthority('operlog:list')")
    public ApiResult<Page<OperLogVO>> page(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) Integer businessType,
            @RequestParam(required = false) Long operUserId,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime beginTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        return ApiResult.ok(operLogService.pageLogs(current, size, title, businessType,
                operUserId, status, beginTime, endTime));
    }

    /**
     * 查询操作日志详情
     *
     * <p>列表为了不把几千字的请求参数整页拖过来，故意不查大字段，要看内容走这里。</p>
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('operlog:list')")
    public ApiResult<OperLogVO> detail(@PathVariable Long id) {
        return ApiResult.ok(operLogService.detail(id));
    }
}
