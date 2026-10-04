package org.tiglor.message.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 绑定推送设备。
 * <p>
 * {@code vendor} 可以省略，服务端按 {@code deviceType} 推断一个默认值。
 * 不是图方便：{@code uk_user_device} 里的 vendor 列在 DDL 上可空，而 MySQL 唯一索引
 * 把 NULL 当作互不相等，真写 NULL 进去这个唯一键就形同虚设。
 * </p>
 */
@Data
public class PushDeviceRequest {

    @NotBlank(message = "deviceType 不能为空")
    @Pattern(regexp = "^(?i)(ios|android|harmony)$", message = "deviceType 只能是 ios / android / harmony")
    private String deviceType;

    @NotBlank(message = "pushToken 不能为空")
    @Size(max = 255, message = "pushToken 不能超过 255 字")
    private String pushToken;

    @Pattern(regexp = "^(?i)(apns|fcm|huawei|xiaomi)$", message = "vendor 只能是 apns / fcm / huawei / xiaomi")
    private String vendor;
}
