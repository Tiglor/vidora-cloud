package org.tiglor.common.log.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import org.tiglor.common.core.BaseEntity;

/**
 * 登录日志（user_service 库 sys_login_log）。
 * <p>
 * 登录成功与失败都要落：失败记录是爆破与账号异常的唯一现场，
 * 只记成功等于把最需要审计的那一半丢掉了。
 */
@Getter
@Setter
@TableName("sys_login_log")
public class LoginLogEntity extends BaseEntity {

    @TableField("trace_id")
    private String traceId;

    /** 登录账号（手机号），失败时用户可能根本不存在，所以按字符串记 */
    private String username;

    /** 命中的用户ID，登录失败为 null */
    @TableField("user_id")
    private Long userId;

    @TableField("client_key")
    private String clientKey;

    private String ip;

    /** User-Agent 原文，截断后存 */
    @TableField("user_agent")
    private String userAgent;

    /** 结果：0-失败 1-成功 */
    private Integer status;

    /** 提示消息：成功为空，失败写「密码错误」之类的原因 */
    private String msg;
}
