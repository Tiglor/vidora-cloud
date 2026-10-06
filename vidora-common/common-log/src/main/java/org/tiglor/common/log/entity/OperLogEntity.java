package org.tiglor.common.log.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import org.tiglor.common.core.BaseEntity;

/**
 * 操作审计日志（vidora_cloud 库审计节的 sys_oper_log）。
 * <p>
 * 表结构跟着 common-log 走，而不是塞进 system-service 的 entity 包（RBAC 那几张表住在那儿）：
 * 这张表是日志设施的落库形态，生产方（各业务服务的切面）和消费方（system-service 的查询接口）
 * 用的是同一个类，少一份 DTO 就少一处字段漂移。
 */
@Getter
@Setter
@TableName("sys_oper_log")
public class OperLogEntity extends BaseEntity {

    /** 链路追踪ID，与 {@code logs/<服务名>/app.log} 里同一次请求的是同一个值；拿它才能从审计行跳到应用日志看细节 */
    @TableField("trace_id")
    private String traceId;

    /** 模块标题，取自 {@code @OperLog(title = "用户管理")}，由代码写死而不是运营填的；管理端日志页按它模糊筛选 */
    private String title;

    /** 目标方法，形如 {@code UserController.delete}（类简名 + Java 方法名，不是 HTTP 路径） */
    private String method;

    /**
     * 业务类型，取自 {@code @OperLog(type = …)} 的枚举档位：
     * 0-其他 1-新增 2-修改 3-删除 4-授权 5-导出 6-导入 7-清空 8-审核 9-状态变更。
     * 其中 5/6（导出、导入）目前没有任何接口在用，管理端的筛选项选得到、但永远查不出记录。
     */
    @TableField("business_type")
    private Integer businessType;

    /** HTTP 方法：GET / POST / PUT / DELETE；取自切面当时能拿到的那个请求，非 Web 上下文触发的动作留空 */
    @TableField("request_method")
    private String requestMethod;

    /**
     * 操作人用户ID，取自网关透传的当前身份；没有登录态（如内网上报触发的动作）时为空，
     * 管理端会把它显示成「系统」。昵称不落库：查询侧就在同一个库里，现场批量补更准。
     */
    @TableField("oper_user_id")
    private Long operUserId;

    /** 发起端：web / mobile / admin，同样取自当前请求上下文，取不到就是空 */
    @TableField("oper_client_key")
    private String operClientKey;

    /** 来源IP：X-Forwarded-For 的第一跳 → X-Real-IP → 直连地址，并按列宽截断——宁可截短也不能让这条审计写不进库 */
    @TableField("oper_ip")
    private String operIp;

    /** 被调用的请求 URI（不含查询串），和 {@code method} 一起定位这次打到的是哪个接口 */
    @TableField("oper_url")
    private String operUrl;

    /**
     * 入参 JSON：单个参数写成对象、多个写成数组，ServletRequest/Response、上传文件、绑定结果这类
     * 无法或无价值序列化的类型会被剔掉；键名里带 password / passwd / secret / token 的值统一换成 {@code ***}，
     * 超长截断并在尾部标出原始字符数。目前所有打点接口都开着记参数，所以这列基本都有值。
     */
    @TableField("oper_param")
    private String operParam;

    /**
     * 返回值 JSON：需要接口显式打开 {@code saveResult} 才落（列表类返回值会把审计表撑爆），
     * 而全仓库目前没有一处打开，所以这一列现在恒为空。
     */
    @TableField("json_result")
    private String jsonResult;

    /** 操作结果：0-失败 1-成功。判据只是「这次调用抛没抛异常」，业务自己返回的错误码不算失败 */
    private Integer status;

    /** 失败原因：只有 {@code status=0} 才有值，取根因异常拼成「异常类名: 消息」，超长截断并在尾部标出原始字符数 */
    @TableField("error_msg")
    private String errorMsg;

    /** 接口耗时（毫秒）：从进切面到被拦方法返回或抛出，含其中的同步数据库与缓存操作，不含异步的审计上报 */
    @TableField("cost_time")
    private Long costTime;
}
