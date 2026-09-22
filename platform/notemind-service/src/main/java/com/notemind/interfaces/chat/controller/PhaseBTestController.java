package com.notemind.interfaces.chat.controller;

import com.notemind.application.service.chat.IntentRouteAsvc;
import com.notemind.application.service.chat.SemanticCacheAsvc;
import com.notemind.common.result.Result;
import com.notemind.interfaces.chat.vo.IntentClassifyRequest;
import com.notemind.interfaces.chat.vo.SemanticCacheRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Phase B 测试接口：意图路由、语义缓存。
 * 手工写入默认关闭（NOTEMIND_ALLOW_MANUAL_CACHE_WRITE），且仅 ADMIN 可调。
 */
@RestController
@RequestMapping("/api/v1/ai")
public class PhaseBTestController {

    private static final int MAX_ANSWER_LEN = 8000;

    private final IntentRouteAsvc intentRouteAsvc;
    private final SemanticCacheAsvc semanticCacheAsvc;
    private final boolean allowManualCacheWrite;

    public PhaseBTestController(
            IntentRouteAsvc intentRouteAsvc,
            SemanticCacheAsvc semanticCacheAsvc,
            @Value("${notemind.phaseb.allow-manual-cache-write:false}") boolean allowManualCacheWrite) {
        this.intentRouteAsvc = intentRouteAsvc;
        this.semanticCacheAsvc = semanticCacheAsvc;
        this.allowManualCacheWrite = allowManualCacheWrite;
    }

    @PostMapping("/intent/classify")
    public Result<Map<String, Object>> classify(@RequestBody IntentClassifyRequest body) {
        String question = body == null ? null : body.getQuestion();
        IntentRouteAsvc.Result r = intentRouteAsvc.classify(question);
        Map<String, Object> data = new LinkedHashMap<>(intentRouteAsvc.toMeta(r));
        data.put("question", question);
        return Result.ok(data);
    }

    @PostMapping("/cache/lookup")
    public Result<Map<String, Object>> lookup(@RequestBody SemanticCacheRequest body) {
        String appId = body == null ? "app_default" : blank(body.getAppId(), "app_default");
        String kbId = body == null ? "kb_default" : blank(body.getKnowledgeBaseId(), "kb_default");
        String question = body == null ? "" : blank(body.getQuestion(), "");
        SemanticCacheAsvc.Hit hit = semanticCacheAsvc.lookup(requireUserId(), appId, kbId, question);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("hit", hit.hit());
        data.put("similarity", hit.similarity());
        data.put("mode", hit.mode());
        data.put("matchedQuestion", hit.matchedQuestion());
        data.put("answer", hit.answer());
        data.put("sources", hit.sources());
        data.put("stats", semanticCacheAsvc.stats());
        return Result.ok(data);
    }

    @PostMapping("/cache/store")
    public Result<Map<String, Object>> store(@RequestBody SemanticCacheRequest body) {
        requireAdmin();
        if (!allowManualCacheWrite) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "手工写入语义缓存已禁用（仅允许问答链路落缓存；本地需 NOTEMIND_ALLOW_MANUAL_CACHE_WRITE=true）");
        }
        if (body == null || blank(body.getQuestion(), "").isEmpty() || blank(body.getAnswer(), "").isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "question/answer required");
        }
        String answer = body.getAnswer().trim();
        if (answer.length() > MAX_ANSWER_LEN) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "answer 过长（上限 " + MAX_ANSWER_LEN + "）");
        }
        String appId = blank(body.getAppId(), "app_default");
        String kbId = blank(body.getKnowledgeBaseId(), "kb_default");
        semanticCacheAsvc.store(
                requireUserId(), appId, kbId, body.getQuestion(), answer, java.util.List.of());
        return Result.ok(Map.of("ok", true, "stats", semanticCacheAsvc.stats()));
    }

    @PostMapping("/cache/clear")
    public Result<Map<String, Object>> clear(@RequestBody(required = false) SemanticCacheRequest body) {
        String appId = body == null ? null : body.getAppId();
        String kbId = body == null ? null : body.getKnowledgeBaseId();
        int n = semanticCacheAsvc.clear(requireUserId(), appId, kbId);
        return Result.ok(Map.of("deleted", n, "stats", semanticCacheAsvc.stats()));
    }

    @GetMapping("/cache/stats")
    public Result<Map<String, Object>> stats() {
        Map<String, Object> data = new LinkedHashMap<>(semanticCacheAsvc.stats());
        data.put("allowManualCacheWrite", allowManualCacheWrite);
        return Result.ok(data);
    }

    private static String requireUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() != null) {
            String p = String.valueOf(auth.getPrincipal());
            if (!p.isBlank() && !"anonymousUser".equals(p)) {
                return p;
            }
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "未登录");
    }

    private static void requireAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "未登录");
        }
        boolean admin = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(a -> "ROLE_ADMIN".equals(a) || "ADMIN".equals(a));
        if (!admin) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅管理员可手工写入语义缓存");
        }
    }

    private static String blank(String v, String dft) {
        return v == null || v.isBlank() ? dft : v.trim();
    }
}
