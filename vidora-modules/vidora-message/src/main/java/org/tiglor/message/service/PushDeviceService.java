package org.tiglor.message.service;

import org.tiglor.message.dto.PushDeviceRequest;
import org.tiglor.message.entity.PushDevice;

import java.util.List;

/**
 * 推送设备绑定表。
 * <p>
 * 这里只管「谁的哪台设备用哪条通道」，不负责真正调厂商 SDK 投递——
 * APNs / FCM / 华为 / 小米各家一套凭证和限流规则，应该由独立的推送网关进程消费 MQ 后再发，
 * 而不是塞在这个 CRUD 服务里。绑定表是那个网关的数据来源。
 * </p>
 */
public interface PushDeviceService {

    /**
     * 绑定或刷新一个推送通道。
     * <p>
     * 同一个 (userId, deviceType, vendor) 已存在就换 token 并重新置为有效——
     * token 会在重装 App、系统升级时变化，而唯一键决定了一个通道只能有一行。
     * 同时把这条 token 在别的用户名下的绑定置为失效，否则设备转手或换账号登录后，
     * 厂商推送会往同一条通道投两个账号的消息。
     * </p>
     */
    PushDevice bind(PushDeviceRequest request, Long userId);

    /**
     * 解绑。deviceType / vendor 传 null 表示解绑该用户的全部通道。
     *
     * @return 被解绑的通道数
     */
    int unbind(Long userId, String deviceType, String vendor);

    /** 我当前有效的推送通道 */
    List<PushDevice> listMine(Long userId);
}
