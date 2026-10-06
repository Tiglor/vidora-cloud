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

    /** 设备平台，大小写不敏感（入库前统一转小写，否则会被唯一索引当成两台设备） */
    @NotBlank(message = "deviceType 不能为空")
    @Pattern(regexp = "^(?i)(ios|android|harmony)$", message = "deviceType 只能是 ios / android / harmony")
    private String deviceType;

    /**
     * 厂商 SDK 取到的推送 token，首尾空格会被去掉。
     * <p>
     * 同一个 (deviceType, vendor) 通道再次绑定就是用它覆盖旧值——token 会随重装 App、系统升级而变化。
     * </p>
     */
    @NotBlank(message = "pushToken 不能为空")
    @Size(max = 255, message = "pushToken 不能超过 255 字")
    private String pushToken;

    /** 可空：省略时服务端按 deviceType 推断默认厂商（ios→apns、android→fcm、harmony→huawei），同样转小写入库 */
    @Pattern(regexp = "^(?i)(apns|fcm|huawei|xiaomi)$", message = "vendor 只能是 apns / fcm / huawei / xiaomi")
    private String vendor;
}
