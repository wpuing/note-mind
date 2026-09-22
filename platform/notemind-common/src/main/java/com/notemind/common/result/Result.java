package com.notemind.common.result;

import java.io.Serializable;

/**
 * 统一 API 响应包装：{@code code / message / data}。
 * <p>
 * {@code code == 0} 表示成功；非 0 为业务或系统错误码。
 *
 * @param <T> 业务数据类型
 */
public class Result<T> implements Serializable {
    /** 业务状态码，0 表示成功。 */
    private int code;
    /** 提示信息。 */
    private String message;
    /** 业务数据载荷。 */
    private T data;

    /**
     * 构造成功响应。
     *
     * @param data 业务数据
     * @param <T>  数据类型
     * @return code=0 的成功结果
     */
    public static <T> Result<T> ok(T data) {
        Result<T> r = new Result<>();
        r.code = 0;
        r.message = "ok";
        r.data = data;
        return r;
    }

    /**
     * 构造失败响应。
     *
     * @param code    错误码
     * @param message 错误说明
     * @param <T>     数据类型（通常无 data）
     * @return 失败结果
     */
    public static <T> Result<T> fail(int code, String message) {
        Result<T> r = new Result<>();
        r.code = code;
        r.message = message;
        return r;
    }

    /**
     * 获取状态码。
     *
     * @return 状态码
     */
    public int getCode() {
        return code;
    }

    /**
     * 获取提示信息。
     *
     * @return 消息
     */
    public String getMessage() {
        return message;
    }

    /**
     * 获取业务数据。
     *
     * @return data
     */
    public T getData() {
        return data;
    }
}
