package com.notemind.application.service.chat;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 意图路由（Phase B）：L1 直线 / L2 Agentic / L3 拒答。
 * 当前以规则分类为主（低成本可测）；可扩展为 ROUTER 小模型。
 */
@Service
public class IntentRouteAsvc {

    public enum Level {
        L1_FACT,
        L2_REASONING,
        L3_REJECT
    }

    public record Result(Level level, double confidence, String reason, String pipeline) {
    }

    /**
     * 对用户问题做意图分级。
     */
    public Result classify(String question) {
        String q = question == null ? "" : question.trim();
        if (q.isEmpty()) {
            return new Result(Level.L3_REJECT, 1.0, "空问题", "reject");
        }
        String lower = q.toLowerCase(Locale.ROOT);
        if (looksLikeChatOrOffTopic(lower, q)) {
            return new Result(Level.L3_REJECT, 0.85, "闲聊或越权倾向", "reject");
        }
        if (looksComplex(q)) {
            return new Result(Level.L2_REASONING, 0.8, "对比/多跳/长问题", "agentic");
        }
        if (looksSimpleFact(q)) {
            return new Result(Level.L1_FACT, 0.82, "短事实问答", "linear");
        }
        // 默认偏 L2，避免复杂题被误压到直线
        return new Result(Level.L2_REASONING, 0.55, "默认复杂推理", "agentic");
    }

    public Map<String, Object> toMeta(Result r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("intentLevel", r.level().name());
        m.put("intentConfidence", r.confidence());
        m.put("intentReason", r.reason());
        m.put("pipelineHint", r.pipeline());
        return m;
    }

    private static boolean looksLikeChatOrOffTopic(String lower, String q) {
        if (q.length() <= 6 && (lower.contains("你好") || lower.contains("hello") || lower.contains("hi"))) {
            return true;
        }
        return lower.contains("讲个笑话")
                || lower.contains("忽略以上")
                || lower.contains("忽略之前")
                || lower.contains("jailbreak")
                || lower.contains("今天天气")
                || lower.contains("system prompt")
                || lower.contains("你现在是");
    }

    private static boolean looksComplex(String q) {
        return q.length() >= 36
                || q.contains("对比")
                || q.contains("比较")
                || q.contains("为什么")
                || q.contains("如何申请")
                || q.contains("并告诉")
                || q.contains("以及")
                || (q.contains("和") && (q.contains("差异") || q.contains("区别")))
                || q.chars().filter(ch -> ch == '？' || ch == '?').count() >= 2;
    }

    private static boolean looksSimpleFact(String q) {
        if (q.length() > 40) {
            return false;
        }
        return q.contains("多少")
                || q.contains("是什么")
                || q.contains("哪个部门")
                || q.contains("谁负责")
                || q.endsWith("？")
                || q.endsWith("?");
    }
}
