package com.notemind.interfaces.model.vo;

/**
 * AI 模型连通性测试结果。
 */
public class AiModelConfigTestResult {
    private boolean ok;
    private String message;
    private Integer httpStatus;
    private Long elapsedMs;

    /** success 方法 */
    public static AiModelConfigTestResult success(String message, Integer httpStatus, long elapsedMs) {
        AiModelConfigTestResult r = new AiModelConfigTestResult();
        r.ok = true;
        r.message = message;
        r.httpStatus = httpStatus;
        r.elapsedMs = elapsedMs;
        return r;
    }

    /** fail 方法 */
    public static AiModelConfigTestResult fail(String message, Integer httpStatus, long elapsedMs) {
        AiModelConfigTestResult r = new AiModelConfigTestResult();
        r.ok = false;
        r.message = message;
        r.httpStatus = httpStatus;
        r.elapsedMs = elapsedMs;
        return r;
    }

    /** 获取ok */
    public boolean isOk() { return ok; }
    /** 设置ok */
    public void setOk(boolean ok) { this.ok = ok; }
    /** 获取消息 */
    public String getMessage() { return message; }
    /** 设置消息 */
    public void setMessage(String message) { this.message = message; }
    /** 获取http Status */
    public Integer getHttpStatus() { return httpStatus; }
    /** 设置http Status */
    public void setHttpStatus(Integer httpStatus) { this.httpStatus = httpStatus; }
    /** 获取elapsed Ms */
    public Long getElapsedMs() { return elapsedMs; }
    /** 设置elapsed Ms */
    public void setElapsedMs(Long elapsedMs) { this.elapsedMs = elapsedMs; }
}
