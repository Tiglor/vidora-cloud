package org.tiglor.api.system.dto;

import java.io.Serializable;
import lombok.Data;

/**
 * 跨服务传输的最小用户视图，不复用 system-service 的数据库实体
 * （{@code sys_user} 里的 phone / email / bio 等字段不该随每次调用扩散）。
 * <p>
 * 字段是 {@code org.tiglor.common.user.entity.User} 的**显式子集**，由 system-service 侧负责保持对齐。
 */
@Data
public class RemoteUserDTO implements Serializable {

    /** Dubbo 的 hessian2 序列化要求可序列化；HTTP/JSON 侧不需要，声明了也无成本。 */
    private static final long serialVersionUID = 1L;

    private Long id;
    private String nickname;
    private String avatarUrl;
    private Integer status;
}
