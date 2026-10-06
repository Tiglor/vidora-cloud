package org.tiglor.common.log.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import org.tiglor.common.core.BaseEntity;

/**
 * 登录日志（vidora_cloud 库审计节的 sys_login_log）。
 * <p>
 * 登录成功与失败都要落：失败记录是爆破与账号异常的唯一现场，
 * 只记成功等于把最需要审计的那一半丢掉了。
 */
@Getter
@Setter
@TableName("sys_login_log")
public class LoginLogEntity extends BaseEntity {

    /** 链路追踪ID，取自 MDC，与 {@code logs/<服务名>/app.log} 里同一次请求的是同一个值；拿不到时为空 */
    @TableField("trace_id")
    private String traceId;

    /** 登录账号（手机号），失败时用户可能根本不存在，所以按字符串记 */
    private String username;

    /** 命中的用户ID，登录失败为 null */
    @TableField("user_id")
    private Long userId;

    /**
     * 发起端：web / mobile / admin，取自登录时命中的那条客户端配置。
     * 失败记录里多半是空的——客户端未注册或被停用时根本走不到取值那一步。
     */
    @TableField("client_key")
    private String clientKey;

    /** 来源IP：X-Forwarded-For 的第一跳 → X-Real-IP → 直连地址，超长截断到列宽（宁可截短也不能让这条审计插不进去） */
    private String ip;

    /** User-Agent 原文，截断后存 */
    @TableField("user_agent")
    private String userAgent;

    /** 结果：0-失败 1-成功 */
    private Integer status;

    /** 提示消息：成功为空，失败写「密码错误」之类的原因 */
    private String msg;
}
