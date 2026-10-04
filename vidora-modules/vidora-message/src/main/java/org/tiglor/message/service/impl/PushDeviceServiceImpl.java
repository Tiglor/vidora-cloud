package org.tiglor.message.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tiglor.common.core.BizException;
import org.tiglor.common.core.ResultCode;
import org.tiglor.message.dto.PushDeviceRequest;
import org.tiglor.message.entity.PushDevice;
import org.tiglor.message.mapper.PushDeviceMapper;
import org.tiglor.message.service.PushDeviceService;

import java.util.List;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PushDeviceServiceImpl extends ServiceImpl<PushDeviceMapper, PushDevice> implements PushDeviceService {

    private static final int STATUS_VALID = 1;

    /**
     * deviceType → 默认厂商。
     * <p>
     * 不是为了少写一个参数：{@code uk_user_device} 里的 vendor 列可空，而 MySQL 唯一索引
     * 把 NULL 当作互不相等，真写 NULL 进去这个唯一键就形同虚设，
     * 同一台设备每次重新绑定都会多出一行。补一个默认值就把 NULL 从这条路径上彻底消掉了。
     * </p>
     */
    private static final Map<String, String> DEFAULT_VENDOR = Map.of(
            "ios", "apns",
            "android", "fcm",
            "harmony", "huawei");

    @Override
    @Transactional
    public PushDevice bind(PushDeviceRequest request, Long userId) {
        requireUserId(userId);
        String deviceType = normalize(request.getDeviceType());
        String vendor = isBlank(request.getVendor())
                ? defaultVendor(deviceType)
                : normalize(request.getVendor());
        String token = request.getPushToken().trim();

        // 先失效别人名下的同一条 token，再绑到自己名下。
        // 顺序反了的话，两条语句之间这台设备同时属于两个用户，推送会串号
        int stolen = baseMapper.invalidateTokenOfOtherUsers(token, userId);
        if (stolen > 0) {
            log.info("推送 token 换绑：{} 个旧绑定已失效, userId={}", stolen, userId);
        }
        baseMapper.upsert(userId, deviceType, token, vendor);
        return lambdaQuery()
                .eq(PushDevice::getUserId, userId)
                .eq(PushDevice::getDeviceType, deviceType)
                .eq(PushDevice::getVendor, vendor)
                .one();
    }

    @Override
    public int unbind(Long userId, String deviceType, String vendor) {
        requireUserId(userId);
        return baseMapper.unbind(userId,
                isBlank(deviceType) ? null : normalize(deviceType),
                isBlank(vendor) ? null : normalize(vendor));
    }

    @Override
    public List<PushDevice> listMine(Long userId) {
        requireUserId(userId);
        // 一个用户最多 3 种 deviceType × 4 种 vendor = 12 行，唯一键天然封了顶，不需要分页
        return lambdaQuery()
                .eq(PushDevice::getUserId, userId)
                .eq(PushDevice::getStatus, STATUS_VALID)
                .orderByDesc(PushDevice::getUpdateTime)
                .list();
    }

    private static String defaultVendor(String deviceType) {
        String vendor = DEFAULT_VENDOR.get(deviceType);
        if (vendor == null) {
            throw new BizException(ResultCode.VALIDATE_FAILED,
                    "无法为 deviceType 推断推送厂商，请显式指定 vendor：" + deviceType);
        }
        return vendor;
    }

    private static String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static void requireUserId(Long userId) {
        if (userId == null) {
            throw new BizException(ResultCode.UNAUTHORIZED, "未登录");
        }
    }
}
