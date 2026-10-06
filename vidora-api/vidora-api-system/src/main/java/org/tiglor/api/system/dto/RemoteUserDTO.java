package org.tiglor.api.system.dto;

import java.io.Serializable;
import lombok.Data;

/**
 * 跨服务传输的最小用户视图，不复用 system-service 的数据库实体
 * （{@code sys_user} 里的 phone / email / bio 等字段不该随每次调用扩散）。
 * <p>
 * 字段是 {@code org.tiglor.system.entity.User} 的「显式子集」，由 system-service 侧负责保持对齐。
 * <p>
 * 这是 <b>HTTP 出口</b>（{@code UserApi}）的视图，会随 JSON 发到调用方乃至前端。
 * 登录链路用的是 {@link RemoteLoginUserDTO}——那个带密码哈希，只该走内网 RPC。两者不要互相顶替。
 */
@Data
public class RemoteUserDTO implements Serializable {

    /** Dubbo 的 hessian2 序列化要求可序列化；HTTP/JSON 侧不需要，声明了也无成本。 */
    private static final long serialVersionUID = 1L;

    /** 用户 id；provider 侧查不到人时整个响应体是 {@code code:200 + data:null}，调用方必须自己判空 */
    private Long id;
    /** 昵称，用于展示投稿者；库里这一列不允许为空，但账号也可能是管理端直接建的、昵称就是手机号 */
    private String nickname;
    /** 头像地址，未设置过时为 null，前端需自备占位图 */
    private String avatarUrl;
    /** 账号状态：0-禁用 1-正常。这一层不替你过滤已禁用的投稿者，要展示「已封禁」就得自己看它 */
    private Integer status;
}
