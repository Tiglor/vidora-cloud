package org.tiglor.common.user.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.tiglor.common.core.BaseEntity;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_client")
public class Client extends BaseEntity {

    private String clientId;

    private String clientKey;

    private String deviceType;

    private String grantType;

    private Integer timeout;

    private Integer status;
}
