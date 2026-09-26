package com.jnf.harness.api.response;

/**
 * 分页响应包（code 约定与 {@link Response} 一致，修正项 R3）。
 */
public record ResponsePage<T>(String code, String info, java.util.List<T> data, long total) {

    public static <T> ResponsePage<T> success(java.util.List<T> data, long total) {
        return new ResponsePage<>(Response.SUCCESS_CODE, "success", data, total);
    }

    public boolean ok() {
        return Response.SUCCESS_CODE.equals(code);
    }
}
