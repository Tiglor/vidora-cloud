package org.tiglor.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 修改当前登录用户的主题。
 * <p>
 * 刻意不校验「是不是一个已知主题」：新增主题包应该只是前端的事，不该逼着后端改代码重新发版。
 * 认不出的 key 前端渲染时会自己落回默认主题，存进去也无害。
 * 这里只约束长度和字符集，保证它塞进 {@code data-theme} 属性和 VARCHAR(32) 都是安全的。
 * </p>
 */
@Data
public class UpdateThemeDTO {

    /** 主题标识，正则已限定为小写字母开头的字母数字连字符串；服务端不校验它是不是一个已知主题包，认不出的值前端会自己落回默认色。只写到当前登录用户名下，不能指定别人 */
    @NotBlank(message = "主题标识不能为空")
    @Size(max = 32, message = "主题标识过长")
    @Pattern(regexp = "^[a-z][a-z0-9-]{0,31}$", message = "主题标识只能包含小写字母、数字和连字符")
    private String themeKey;
}
