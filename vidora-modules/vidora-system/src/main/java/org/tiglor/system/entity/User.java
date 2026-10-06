package org.tiglor.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.tiglor.common.core.BaseEntity;

import java.time.LocalDate;

/**
 * 用户，对应单库 vidora_cloud 的 sys_user 表，只归 system-service 所有。
 * <p>
 * auth-service 不再共享本实体（原 {@code vidora-common/common-user} 模块已删除）：
 * 它经 Dubbo 契约 {@code RemoteUserApi} 拿 {@code RemoteLoginUserDTO}。
 * 好处是这张表加减列不会连带编译进认证服务，坏处是登录链路上多一跳网络（见 .code/ARCHITECTURE.md 6.2.1）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_user")
public class User extends BaseEntity {

    /** 手机号，同时就是登录名：唯一键，注册与登录都按它查人 */
    private String phone;
    /** 邮箱。建了唯一键但后端没有任何写入或读取入口——注册不收、个人中心不改，落库的一律是空 */
    private String email;
    /** 对外显示的名字；注册时不填就由服务端拿手机号兜底（这列是必填的） */
    private String nickname;
    /** 头像 URL，指向已经传好的图片；后端不提供上传口，自助设置里也没有改头像这一项，只能由运营在管理端表单填 */
    private String avatarUrl;
    /**
     * 只挡 HTTP JSON 出口（管理端 {@code UserController} 直接返回本实体），永不随响应体输出。
     * Dubbo 侧是例外：{@code RemoteUserApi.getLoginUser} 刻意把哈希送给 auth-service 做
     * {@code PasswordEncoder.matches}——送哈希而不是送明文密码去对端校验，见 RemoteLoginUserDTO 的字段注释。
     */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String passwordHash;
    /** 账号状态：0-禁用 1-正常。禁用只拦后续登录（提示「账号已被禁用」），已签发的 token 不会失效，网关照放 */
    private Integer status;
    /** 性别：0-未知 1-男 2-女。后端不解释也不写入，只在管理端列表里原样带出，界面上也没有编辑入口 */
    private Integer gender;
    /** 生日；后端没有任何用户侧写入路径（注册不落、自助设置也没有这项），只能由运营在管理端表单填 */
    private LocalDate birthday;
    /** 个人简介，一段展示用的文本；后端不审核、不截断，也没有用户自助修改的口（自助设置里只有主题） */
    private String bio;
    /** 关注数。目前只有注册时写成 0，全后端没有任何加一减一的路径，也不会从 user_follow 表回算 */
    private Integer followCount;
    /** 粉丝数。同上：注册置 0 之后就是死数，手工改管理端表单只会盖掉它而不会变准 */
    private Integer followerCount;
    /** 地区，展示用的自由文本，不参与任何筛选或统计；后端也没有按它查用户的条件 */
    private String region;
    /** 主题标识，取值见前端主题包；后端只存不解释 */
    private String themeKey;
}
