package com.notemind.application.service.chat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.notemind.application.service.model.AiModelConfigAsvc;
import com.notemind.application.service.system.SystemConfigAsvc;
import com.notemind.infrastructure.security.OutboundUrlGuard;
import com.notemind.interfaces.model.vo.AiModelConfigVo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 语义缓存（Phase B）：按 userId + appId + knowledgeBaseId 隔离；Redis 优先。
 */
@Service
public class SemanticCacheAsvc {

    private static final Logger log = LoggerFactory.getLogger(SemanticCacheAsvc.class);
    private static final String INDEX_KEY = "nm:scache:index";
    private static final String ENTRY_PREFIX = "nm:scache:e:";
    private static final int MAX_QUESTION_LEN = 2000;
    private static final int MAX_SCAN = 200;
    private static final Duration ENTRY_TTL = Duration.ofDays(7);
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final AiModelConfigAsvc aiModelConfigAsvc;
    private final SystemConfigAsvc systemConfig;
    private final boolean allowLocalHttp;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    private final ConcurrentHashMap<String, Map<String, Object>> local = new ConcurrentHashMap<>();

    public SemanticCacheAsvc(
            StringRedisTemplate redis,
            ObjectMapper objectMapper,
            AiModelConfigAsvc aiModelConfigAsvc,
            SystemConfigAsvc systemConfig,
            @Value("${notemind.outbound.allow-local-http:false}") boolean allowLocalHttp) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.aiModelConfigAsvc = aiModelConfigAsvc;
        this.systemConfig = systemConfig;
        this.allowLocalHttp = allowLocalHttp;
    }

    public record Hit(
            boolean hit,
            double similarity,
            String matchedQuestion,
            String answer,
            List<Map<String, Object>> sources,
            String mode) {
    }

    public Hit lookup(String userId, String appId, String knowledgeBaseId, String question) {
        String uid = blankToAnon(userId);
        String aid = blankToDefault(appId, "app_default");
        String kid = blankToDefault(knowledgeBaseId, "kb_default");
        String norm = normalize(question);
        if (norm.isEmpty()) {
            return new Hit(false, 0, null, null, List.of(), "empty");
        }
        if (norm.length() > MAX_QUESTION_LEN) {
            return new Hit(false, 0, null, null, List.of(), "too_long");
        }
        double threshold = threshold();
        try {
            String exactId = sha(uid + "|" + aid + "|" + kid + "|" + norm);
            Map<String, Object> exact = readEntry(exactId);
            if (exact != null && norm.equals(String.valueOf(exact.getOrDefault("norm", "")))) {
                return toHit(exact, 1.0, "exact");
            }
            float[] qEmb = embed(norm);
            if (qEmb == null) {
                return new Hit(false, 0, null, null, List.of(), "no_embedding");
            }
            Hit best = null;
            int scanned = 0;
            for (String id : listIds()) {
                if (scanned++ >= MAX_SCAN) {
                    break;
                }
                Map<String, Object> e = readEntry(id);
                if (e == null) {
                    continue;
                }
                if (!safeEq(uid, e.get("userId"))
                        || !safeEq(aid, e.get("appId"))
                        || !safeEq(kid, e.get("knowledgeBaseId"))) {
                    continue;
                }
                float[] emb = toFloatArray(e.get("embedding"));
                if (emb == null) {
                    continue;
                }
                double sim = cosine(qEmb, emb);
                if (sim >= threshold && (best == null || sim > best.similarity())) {
                    best = toHit(e, sim, "cosine");
                }
            }
            return best != null ? best : new Hit(false, 0, null, null, List.of(), "miss");
        } catch (Exception ex) {
            log.warn("semantic cache lookup failed: {}", ex.getMessage());
            return new Hit(false, 0, null, null, List.of(), "error");
        }
    }

    public void store(
            String userId,
            String appId,
            String knowledgeBaseId,
            String question,
            String answer,
            List<Map<String, Object>> sources) {
        String uid = blankToAnon(userId);
        String aid = blankToDefault(appId, "app_default");
        String kid = blankToDefault(knowledgeBaseId, "kb_default");
        String norm = normalize(question);
        if (norm.isEmpty() || norm.length() > MAX_QUESTION_LEN || answer == null || answer.isBlank()) {
            return;
        }
        try {
            float[] emb = embed(norm);
            String id = sha(uid + "|" + aid + "|" + kid + "|" + norm);
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", id);
            entry.put("userId", uid);
            entry.put("appId", aid);
            entry.put("knowledgeBaseId", kid);
            entry.put("question", question.trim());
            entry.put("norm", norm);
            entry.put("answer", answer);
            entry.put("sources", sources == null ? List.of() : sources);
            entry.put("embedding", emb);
            entry.put("ts", System.currentTimeMillis());
            writeEntry(id, entry);
            addIndex(id);
            trimIndex(MAX_SCAN);
        } catch (Exception ex) {
            log.warn("semantic cache store failed: {}", ex.getMessage());
        }
    }

    /**
     * 清理缓存：必须指定当前 userId，且至少指定 appId 或 knowledgeBaseId；禁止跨用户/无条件全清。
     */
    public int clear(String userId, String appId, String knowledgeBaseId) {
        if (userId == null || userId.isBlank() || "anonymous".equals(userId.trim())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "clear 必须指定有效 userId");
        }
        boolean hasApp = appId != null && !appId.isBlank();
        boolean hasKb = knowledgeBaseId != null && !knowledgeBaseId.isBlank();
        if (!hasApp && !hasKb) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "clear 必须指定 appId 或 knowledgeBaseId");
        }
        String uid = userId.trim();
        int n = 0;
        for (String id : new ArrayList<>(listIds())) {
            Map<String, Object> e = readEntry(id);
            if (e == null) {
                continue;
            }
            boolean matchUser = safeEq(uid, e.get("userId"));
            boolean matchApp = !hasApp || safeEq(appId, e.get("appId"));
            boolean matchKb = !hasKb || safeEq(knowledgeBaseId, e.get("knowledgeBaseId"));
            if (matchUser && matchApp && matchKb) {
                deleteEntry(id);
                n++;
            }
        }
        return n;
    }

    public Map<String, Object> stats() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("size", listIds().size());
        m.put("threshold", threshold());
        m.put("backend", redisAvailable() ? "redis" : "local");
        m.put("ttlDays", ENTRY_TTL.toDays());
        m.put("maxScan", MAX_SCAN);
        return m;
    }

    private Hit toHit(Map<String, Object> e, double sim, String mode) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> sources = e.get("sources") instanceof List<?> list
                ? (List<Map<String, Object>>) list
                : List.of();
        return new Hit(
                true,
                sim,
                String.valueOf(e.getOrDefault("question", "")),
                String.valueOf(e.getOrDefault("answer", "")),
                sources,
                mode);
    }

    private double threshold() {
        try {
            double v = systemConfig.getDouble("retrieval.semantic_cache_threshold", 0.95);
            if (v > 0 && v <= 1) {
                return v;
            }
        } catch (Exception ignored) {
        }
        return 0.95;
    }

    private float[] embed(String text) throws Exception {
        String modelId = systemConfig.getString("default.embedding_model_id", "m_emb_v4");
        AiModelConfigVo vo = aiModelConfigAsvc.getById(modelId);
        String apiKey = aiModelConfigAsvc.getPlainApiKey(modelId);
        if (apiKey == null || apiKey.isBlank()) {
            return null;
        }
        String modelName = vo.getModelName() == null || vo.getModelName().isBlank()
                ? "text-embedding-v4"
                : vo.getModelName().trim();
        String base = OutboundUrlGuard.requireSafeHttpUrl(
                vo.getBaseUrl() == null || vo.getBaseUrl().isBlank()
                        ? "https://dashscope.aliyuncs.com/compatible-mode/v1"
                        : vo.getBaseUrl().trim().replaceAll("/+$", ""),
                allowLocalHttp);
        String body = objectMapper.createObjectNode()
                .put("model", modelName)
                .set("input", objectMapper.createArrayNode().add(text))
                .toString();
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(base + "/embeddings"))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() >= 300) {
            throw new IllegalStateException("embed http " + resp.statusCode());
        }
        Map<String, Object> json = objectMapper.readValue(resp.body(), MAP_TYPE);
        Object data = json.get("data");
        if (!(data instanceof List<?> list) || list.isEmpty()) {
            return null;
        }
        Object first = list.get(0);
        if (!(first instanceof Map<?, ?> m)) {
            return null;
        }
        return toFloatArray(m.get("embedding"));
    }

    private boolean redisAvailable() {
        try {
            var factory = redis.getConnectionFactory();
            if (factory == null) {
                return false;
            }
            try (RedisConnection conn = factory.getConnection()) {
                String pong = conn.ping();
                return pong != null;
            }
        } catch (Exception e) {
            return false;
        }
    }

    private void writeEntry(String id, Map<String, Object> entry) throws Exception {
        String json = objectMapper.writeValueAsString(entry);
        local.put(id, entry);
        try {
            redis.opsForValue().set(ENTRY_PREFIX + id, json, ENTRY_TTL);
        } catch (Exception ex) {
            log.debug("redis set skip: {}", ex.getMessage());
        }
    }

    private Map<String, Object> readEntry(String id) {
        try {
            String json = redis.opsForValue().get(ENTRY_PREFIX + id);
            if (json != null && !json.isBlank()) {
                return objectMapper.readValue(json, MAP_TYPE);
            }
        } catch (Exception ignored) {
        }
        return local.get(id);
    }

    private void deleteEntry(String id) {
        local.remove(id);
        try {
            redis.delete(ENTRY_PREFIX + id);
            redis.opsForSet().remove(INDEX_KEY, id);
        } catch (Exception ignored) {
        }
    }

    private void addIndex(String id) {
        try {
            redis.opsForSet().add(INDEX_KEY, id);
        } catch (Exception ignored) {
        }
    }

    private List<String> listIds() {
        try {
            var members = redis.opsForSet().members(INDEX_KEY);
            if (members != null && !members.isEmpty()) {
                return new ArrayList<>(members);
            }
        } catch (Exception ignored) {
        }
        return new ArrayList<>(local.keySet());
    }

    private void trimIndex(int max) {
        List<String> ids = listIds();
        if (ids.size() <= max) {
            return;
        }
        for (int i = 0; i < ids.size() - max; i++) {
            deleteEntry(ids.get(i));
        }
    }

    private static String normalize(String q) {
        if (q == null) {
            return "";
        }
        return q.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private static String sha(String s) {
        try {
            var md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] dig = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : dig) {
                sb.append(String.format("%02x", b));
            }
            return sb.substring(0, 32);
        } catch (Exception e) {
            return UUID.nameUUIDFromBytes(s.getBytes(StandardCharsets.UTF_8)).toString().replace("-", "");
        }
    }

    private static boolean safeEq(String a, Object b) {
        return a != null && a.equals(String.valueOf(b));
    }

    private static String blankToAnon(String userId) {
        return userId == null || userId.isBlank() ? "anonymous" : userId.trim();
    }

    private static String blankToDefault(String v, String dft) {
        return v == null || v.isBlank() ? dft : v.trim();
    }

    private static float[] toFloatArray(Object raw) {
        if (raw instanceof float[] fa) {
            return fa;
        }
        if (raw instanceof List<?> list) {
            float[] out = new float[list.size()];
            for (int i = 0; i < list.size(); i++) {
                out[i] = Float.parseFloat(String.valueOf(list.get(i)));
            }
            return out;
        }
        return null;
    }

    private static double cosine(float[] a, float[] b) {
        if (a == null || b == null || a.length == 0 || b.length == 0 || a.length != b.length) {
            return 0;
        }
        int n = a.length;
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < n; i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        if (na == 0 || nb == 0) {
            return 0;
        }
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }
}
