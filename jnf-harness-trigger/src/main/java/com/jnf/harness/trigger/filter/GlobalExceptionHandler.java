package com.jnf.harness.trigger.filter;

import com.jnf.harness.api.response.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理（修正项 R3：一套 code 约定，业务错误不进 HTTP 状态码）。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(IllegalArgumentException.class)
    public Response<Void> badArgument(IllegalArgumentException e) {
        return Response.invalidArgument(e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public Response<Void> unexpected(Exception e) {
        log.error("未处理异常", e);
        return Response.internalError("内部错误: " + e.getMessage());
    }
}
