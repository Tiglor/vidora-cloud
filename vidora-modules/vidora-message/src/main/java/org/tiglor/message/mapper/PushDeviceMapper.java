package org.tiglor.message.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import org.tiglor.message.entity.PushDevice;

@Mapper
public interface PushDeviceMapper extends BaseMapper<PushDevice> {

    /**
     * 绑定或刷新一个推送通道：同一 (user, deviceType, vendor) 已存在就换 token 并重新置为有效。
     * <p>
     * token 会在重装 App、系统升级、用户从登出到换账号登录时变化，
     * 而 {@code uk_user_device} 决定了一个通道只能有一行，所以这里必须是 upsert 而不是 insert。
     * </p>
     * <p>
     * <b>返回值不是行数</b>：MySQL 对 {@code ON DUPLICATE KEY UPDATE} 的约定是
     * 插入返回 1、更新返回 2、命中但值没变返回 0。调用方不要拿它判断成功与否。
     * </p>
     */
    @Insert("""
            INSERT INTO message_push_device (user_id, device_type, push_token, vendor, status)
            VALUES (#{userId}, #{deviceType}, #{pushToken}, #{vendor}, 1)
            ON DUPLICATE KEY UPDATE
                push_token = VALUES(push_token),
                status = 1
            """)
    int upsert(@Param("userId") Long userId,
               @Param("deviceType") String deviceType,
               @Param("pushToken") String pushToken,
               @Param("vendor") String vendor);

    /**
     * 把同一个 push_token 在「别的用户名下」的绑定置为失效。
     * <p>
     * 设备是会被转手和换账号登录的：不失效旧绑定的话，同一个 token 会同时挂在两个用户下，
     * 厂商推送会往同一条通道投两个账号的消息——A 的私信推给正在用这台设备的 B，是实打实的隐私泄露。
     * 走 {@code idx_push_token}。
     * </p>
     */
    @Update("""
            UPDATE message_push_device
               SET status = 0
             WHERE push_token = #{pushToken} AND user_id <> #{userId} AND status = 1
            """)
    int invalidateTokenOfOtherUsers(@Param("pushToken") String pushToken, @Param("userId") Long userId);

    /**
     * 解绑。{@code deviceType} / {@code vendor} 为 null 时不参与过滤（解绑该用户的全部通道）。
     * <p>
     * 置 status=0 而不是 DELETE：厂商推送是异步的，投递失败时需要能从「最近失效的绑定」里
     * 反查这条 token 属于谁；真删了行，日志里的 token 就成了孤儿。
     * </p>
     */
    @Update("""
            <script>
            UPDATE message_push_device
               SET status = 0
             WHERE user_id = #{userId}
               AND status = 1
               <if test="deviceType != null">AND device_type = #{deviceType}</if>
               <if test="vendor != null">AND vendor = #{vendor}</if>
            </script>
            """)
    int unbind(@Param("userId") Long userId,
               @Param("deviceType") String deviceType,
               @Param("vendor") String vendor);
}
