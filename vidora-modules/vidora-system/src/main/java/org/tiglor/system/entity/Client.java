package org.tiglor.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.tiglor.common.core.BaseEntity;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_client")
public class Client extends BaseEntity {

    /** 客户端标识，三端打包时写死在自己的环境变量里（如 {@code vidora-web-2024}），随登录请求上送；唯一键，改了就要发新版客户端 */
    private String clientId;

    /**
     * 端的短名：web / mobile / admin。签进 JWT 并由网关透传成 X-Client-Key，
     * {@code /api/**} 管理前缀只放行 admin，操作与登录审计里的「发起端」也取这个值；同样有唯一键。
     */
    private String clientKey;

    /** 设备类型：pc / app。仅作登记，auth 与 gateway 都不读它，改这个值不影响任何鉴权或过期行为 */
    private String deviceType;

    /** 该端允许的认证方式，逗号分隔（种子数据三端都是 password）。登录侧只做「是否包含 password」，写成别的值等于关掉密码登录 */
    private String grantType;

    /** token 有效期（秒），签发 JWT 时直接当过期时间用，只对之后新签发的 token 生效；登录侧不做判空兜底，这一列留空会让该端登录直接抛异常 */
    private Integer timeout;

    /** 启用状态：0-停用 1-启用。非 1 时该端登录直接被拒（「客户端未注册或已停用」），效果与删掉这行一样 */
    private Integer status;
}
