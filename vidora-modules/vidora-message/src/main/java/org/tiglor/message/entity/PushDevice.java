package org.tiglor.message.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 厂商推送的设备绑定，一行 = 一个用户在一个平台上的一套推送通道。
 * <p>
 * {@code uk_user_device(user_id, device_type, vendor)} 里的 {@code vendor} 在 DDL 上可空，
 * 但 MySQL 的唯一索引把 NULL 当作互不相等——真让它为空，同一个用户同一台设备就能反复插出无数行。
 * 所以服务层**永远不给 vendor 写 NULL**：请求没带就按 deviceType 推断一个默认厂商，
 * 见 {@link org.tiglor.message.service.PushDeviceService}。
 * </p>
 * <p>
 * 不继承 {@code BaseEntity}：表上没有 {@code is_deleted}，失效靠 {@code status}。
 * </p>
 */
@Data
@TableName("message_push_device")
public class PushDevice implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 绑定记录主键，数据库自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 这条通道属于谁，取自登录上下文，客户端无法指定 */
    private Long userId;

    /** 设备类型：ios / android / harmony */
    private String deviceType;

    /**
     * 厂商下发的推送 token，真正往设备上投消息就靠它。
     * <p>
     * 它会随重装 App、系统升级、换账号登录而变，所以同一行上的值会被覆盖；
     * 同一条 token 同一时刻只属于一个用户——换绑时旧绑定先置失效。
     * </p>
     */
    private String pushToken;

    /** 推送厂商：apns / fcm / huawei / xiaomi */
    private String vendor;

    /** 状态：0-失效 1-有效 */
    private Integer status;

    /** 首次绑定时间，插入时自动填充，调用方传进来的值不算数 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 最后一次绑定或解绑的时间，插入与更新时都自动填充；「我的通道」列表按它倒序 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
