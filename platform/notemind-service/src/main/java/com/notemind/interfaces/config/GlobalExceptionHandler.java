package com.notemind.interfaces.config;

import com.notemind.common.result.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Result<Void>> handleStatus(ResponseStatusException ex) {
        int code = ex.getStatusCode().value();
        String msg = ex.getReason() == null ? ex.getStatusCode().toString() : ex.getReason();
        return ResponseEntity.status(ex.getStatusCode()).body(Result.fail(code, sanitize(msg)));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleOther(Exception ex) {
        log.error("unhandled error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.fail(500, "服务内部错误，请稍后重试"));
    }

    private static String sanitize(String msg) {
        if (msg == null || msg.isBlank()) {
            return "请求失败";
        }
        String m = msg.trim();
        if (m.length() > 200) {
            m = m.substring(0, 200);
        }
        // 避免把本机绝对路径回给浏览器
        m = m.replaceAll("[A-Za-z]:\\\\[^\\s]+", "[path]");
        m = m.replaceAll("/home/[^\\s]+", "[path]");
        return m;
    }
}
