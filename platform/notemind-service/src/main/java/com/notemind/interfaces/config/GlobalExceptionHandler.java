package com.notemind.interfaces.config;

import com.notemind.common.result.Result;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/**
 * 全局异常处理：将业务/系统异常统一包装为 Result 响应。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理带 HTTP 状态的业务异常（如 400/401/403/404）。
     *
     * @param ex ResponseStatusException
     * @return 对应状态码与失败 Result
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Result<Void>> handleStatus(ResponseStatusException ex) {
        int code = ex.getStatusCode().value();
        // 优先使用 reason 作为对用户可读的错误信息
        String msg = ex.getReason() == null ? ex.getMessage() : ex.getReason();
        return ResponseEntity.status(ex.getStatusCode()).body(Result.fail(code, msg));
    }

    /**
     * 处理未预期的系统异常，统一返回 500。
     *
     * @param ex 任意 Exception
     * @return 500 与失败 Result
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleOther(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.fail(500, ex.getMessage() == null ? "internal error" : ex.getMessage()));
    }
}
