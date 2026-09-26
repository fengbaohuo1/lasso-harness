package com.jnf.harness.api.response;

/**
 * 统一响应包（修正项 R3：HTTP 恒 200，业务成败看 code）。
 * <p>code 约定（全接口唯一一套）：00000 成功 / 40000 参数错误 / 50000 内部错误。
 */
public record Response<T>(String code, String info, T data) {

    public static final String SUCCESS_CODE = "00000";
    public static final String INVALID_ARGUMENT_CODE = "40000";
    public static final String INTERNAL_ERROR_CODE = "50000";

    public static <T> Response<T> success(T data) {
        return new Response<>(SUCCESS_CODE, "success", data);
    }

    public static <T> Response<T> invalidArgument(String info) {
        return new Response<>(INVALID_ARGUMENT_CODE, info, null);
    }

    public static <T> Response<T> internalError(String info) {
        return new Response<>(INTERNAL_ERROR_CODE, info, null);
    }

    public boolean ok() {
        return SUCCESS_CODE.equals(code);
    }
}
