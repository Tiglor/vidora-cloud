package org.tiglor.video.client.dto;

import lombok.Data;

/** 跨服务传输的最小用户视图，不复用 system-service 的数据库实体。 */
@Data
public class RemoteUserDTO {

    private Long id;
    private String nickname;
    private String avatarUrl;
    private Integer status;
}
