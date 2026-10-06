package org.tiglor.common.core;

import lombok.Data;

import java.io.Serializable;

/**
 * 统一 API 响应结构
 */
@Data
public class ApiResult<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 业务码，取值与 HTTP 状态码同源（见 {@link ResultCode}），成功固定 200 */
    private Integer code;
    /** 失败时可直接展示给用户的提示；成功时固定是 {@code success}，别拿它当判断依据 */
    private String message;
    /** 业务数据。失败时是 null，成功但无返回体的写接口（删除、改状态）同样是 null —— 所以成败只看 code */
    private T data;

    public static <T> ApiResult<T> ok() {
        return ok(null);
    }

    public static <T> ApiResult<T> ok(T data) {
        ApiResult<T> result = new ApiResult<>();
        result.setCode(200);
        result.setMessage("success");
        result.setData(data);
        return result;
    }

    public static <T> ApiResult<T> error(String message) {
        return error(500, message);
    }

    public static <T> ApiResult<T> error(Integer code, String message) {
        ApiResult<T> result = new ApiResult<>();
        result.setCode(code);
        result.setMessage(message);
        return result;
    }
}
