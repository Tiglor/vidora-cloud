package org.tiglor.common.log.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import org.tiglor.common.core.BaseEntity;

/**
 * 操作审计日志（user_service 库 sys_oper_log）。
 * <p>
 * 表结构跟着 common-log 走，而不是塞进 common-user：这张表是日志设施的落库形态，
 * 生产方（各业务服务的切面）和消费方（system-service 的查询接口）用的是同一个类，
 * 少一份 DTO 就少一处字段漂移。
 */
@Getter
@Setter
@TableName("sys_oper_log")
public class OperLogEntity extends BaseEntity {

    /** 链路追踪ID，与日志文件里的 traceId 同一个值 */
    @TableField("trace_id")
    private String traceId;

    /** 模块标题，取自 @OperLog(title = "用户管理") */
    private String title;

    /** 业务类型，取值见 BusinessType */
    @TableField("business_type")
    private Integer businessType;

    /** 目标方法，形如 UserController.delete */
    private String method;

    @TableField("request_method")
    private String requestMethod;

    /** 操作人用户ID。昵称不落库：查询侧就在同一个库里，现场批量补更准 */
    @TableField("oper_user_id")
    private Long operUserId;

    /** 发起端：web / mobile / admin */
    @TableField("oper_client_key")
    private String operClientKey;

    @TableField("oper_ip")
    private String operIp;

    @TableField("oper_url")
    private String operUrl;

    /** 请求参数 JSON，已截断；标了 saveParam=false 的接口不落 */
    @TableField("oper_param")
    private String operParam;

    /** 返回结果 JSON，默认不落，只有明确要审计返回值时才开 */
    @TableField("json_result")
    private String jsonResult;

    /** 操作结果：0-失败 1-成功 */
    private Integer status;

    @TableField("error_msg")
    private String errorMsg;

    /** 耗时（毫秒） */
    @TableField("cost_time")
    private Long costTime;
}
