package com.notemind.application.service.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.notemind.infrastructure.security.ApiKeyCrypto;
import com.notemind.infrastructure.security.OutboundUrlGuard;
import com.notemind.common.result.PageResult;
import com.notemind.common.util.DateTimes;
import com.notemind.interfaces.model.vo.AiModelConfigSaveRequest;
import com.notemind.interfaces.model.vo.AiModelConfigTestResult;
import com.notemind.interfaces.model.vo.AiModelConfigVo;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * AI 模型配置应用服务：CRUD、密钥脱敏、连通性测试与批量删除。
 */
@Service
public class AiModelConfigAsvc {

    private static final Set<String> MODEL_TYPES = Set.of("CHAT", "EMBEDDING", "RERANK");
    private static final Set<String> API_STYLES = Set.of("openai_compatible", "dashscope_native");

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final ApiKeyCrypto apiKeyCrypto;
    private final boolean allowLocalHttp;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    public AiModelConfigAsvc(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            ApiKeyCrypto apiKeyCrypto,
            @org.springframework.beans.factory.annotation.Value("${notemind.outbound.allow-local-http:false}")
                    boolean allowLocalHttp) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.apiKeyCrypto = apiKeyCrypto;
        this.allowLocalHttp = allowLocalHttp;
    }

    private String safeBaseUrl(String raw, String dft) {
        String base = blankToDefault(raw, dft);
        return OutboundUrlGuard.requireSafeHttpUrl(base, allowLocalHttp);
    }

    /**
     * 查询启用中的列表。
     * @return 返回结果
     */
    public List<AiModelConfigVo> listEnabled() {
        // 调用 jdbcTemplate.query
        return jdbcTemplate.query(
                """
                SELECT * FROM t_ai_model_config
                WHERE deleted = 0 AND enabled = 1
                ORDER BY model_type, name
                """,
                mapper());
    }

    /**
     * 分页查询。
     * @param name 参数 name
     * @param modelType 参数 modelType
     * @param page 参数 page
     * @param pageSize 参数 pageSize
     * @return 返回结果
     */
    public PageResult<AiModelConfigVo> page(String name, String modelType, int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(pageSize, 1), 100);
        StringBuilder where = new StringBuilder(" WHERE deleted = 0");
        ArrayList<Object> args = new ArrayList<>();
        // 条件判断
        if (name != null && !name.isBlank()) {
            where.append(" AND name LIKE ?");
            args.add("%" + name.trim() + "%");
        }
        // 条件判断
        if (modelType != null && !modelType.isBlank()) {
            where.append(" AND model_type = ?");
            args.add(modelType.trim().toUpperCase(Locale.ROOT));
        }
        // 调用 jdbcTemplate.queryForObject
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM t_ai_model_config" + where, Long.class, args.toArray());
        ArrayList<Object> listArgs = new ArrayList<>(args);
        listArgs.add(safeSize);
        listArgs.add((safePage - 1) * safeSize);
        // 调用 jdbcTemplate.query
        List<AiModelConfigVo> records = jdbcTemplate.query(
                "SELECT * FROM t_ai_model_config"
                        + where
                        + " ORDER BY model_type, create_time DESC LIMIT ? OFFSET ?",
                mapper(),
                listArgs.toArray());
        return PageResult.of(total == null ? 0L : total, safePage, safeSize, records);
    }

    /**
     * 按 ID 查询详情。
     * @param id 参数 id
     * @return 返回结果
     */
    public AiModelConfigVo getById(String id) {
        // 调用 jdbcTemplate.query
        List<AiModelConfigVo> rows = jdbcTemplate.query(
                "SELECT * FROM t_ai_model_config WHERE deleted = 0 AND id = ?", mapper(), id);
        // 条件判断
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "model config not found");
        }
        return rows.get(0);
    }

    /**
     * 新建记录。
     * @param req 参数 req
     * @return 返回结果
     */
    @Transactional
    public AiModelConfigVo create(AiModelConfigSaveRequest req) {
        validate(req, true);
        String id = "m_" + UUID.randomUUID().toString().replace("-", "").substring(0, 14);
        LocalDateTime now = LocalDateTime.now();
        // 调用 jdbcTemplate.update
        jdbcTemplate.update(
                """
                INSERT INTO t_ai_model_config (
                  id, create_time, update_time, deleted,
                  name, model_type, provider, model_name, base_url, api_key_enc, api_style,
                  dimension, temperature, enabled, remark
                ) VALUES (?, ?, ?, 0, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                id,
                Timestamp.valueOf(now),
                Timestamp.valueOf(now),
                req.getName().trim(),
                req.getModelType().trim().toUpperCase(Locale.ROOT),
                blankToDefault(req.getProvider(), "dashscope"),
                req.getModelName().trim(),
                blankToNull(req.getBaseUrl()),
                // 调用 apiKeyCrypto.encryptForStorage
                apiKeyCrypto.encryptForStorage(blankToNull(req.getApiKey())),
                blankToDefault(req.getApiStyle(), "openai_compatible"),
                req.getDimension(),
                req.getTemperature(),
                req.getEnabled() == null ? 1 : req.getEnabled(),
                blankToNull(req.getRemark()));
        return getById(id);
    }

    /**
     * 更新记录。
     * @param id 参数 id
     * @param req 参数 req
     * @return 返回结果
     */
    @Transactional
    public AiModelConfigVo update(String id, AiModelConfigSaveRequest req) {
        getById(id);
        validate(req, false);
        LocalDateTime now = LocalDateTime.now();
        // 条件判断
        if (req.getApiKey() != null && !req.getApiKey().isBlank()) {
            // 调用 jdbcTemplate.update
            jdbcTemplate.update(
                    """
                    UPDATE t_ai_model_config
                    SET name = ?, model_type = ?, provider = ?, model_name = ?, base_url = ?,
                        api_key_enc = ?, api_style = ?, dimension = ?, temperature = ?,
                        enabled = ?, remark = ?, update_time = ?
                    WHERE id = ? AND deleted = 0
                    """,
                    req.getName().trim(),
                    req.getModelType().trim().toUpperCase(Locale.ROOT),
                    blankToDefault(req.getProvider(), "dashscope"),
                    req.getModelName().trim(),
                    blankToNull(req.getBaseUrl()),
                    // 调用 apiKeyCrypto.encryptForStorage
                    apiKeyCrypto.encryptForStorage(req.getApiKey().trim()),
                    blankToDefault(req.getApiStyle(), "openai_compatible"),
                    req.getDimension(),
                    req.getTemperature(),
                    req.getEnabled() == null ? 1 : req.getEnabled(),
                    blankToNull(req.getRemark()),
                    Timestamp.valueOf(now),
                    id);
        } else {
            // 调用 jdbcTemplate.update
            jdbcTemplate.update(
                    """
                    UPDATE t_ai_model_config
                    SET name = ?, model_type = ?, provider = ?, model_name = ?, base_url = ?,
                        api_style = ?, dimension = ?, temperature = ?,
                        enabled = ?, remark = ?, update_time = ?
                    WHERE id = ? AND deleted = 0
                    """,
                    req.getName().trim(),
                    req.getModelType().trim().toUpperCase(Locale.ROOT),
                    blankToDefault(req.getProvider(), "dashscope"),
                    req.getModelName().trim(),
                    blankToNull(req.getBaseUrl()),
                    blankToDefault(req.getApiStyle(), "openai_compatible"),
                    req.getDimension(),
                    req.getTemperature(),
                    req.getEnabled() == null ? 1 : req.getEnabled(),
                    blankToNull(req.getRemark()),
                    Timestamp.valueOf(now),
                    id);
        }
        return getById(id);
    }

    /**
     * 删除记录。
     * @param id 参数 id
     */
    @Transactional
    public void delete(String id) {
        getById(id);
        LocalDateTime now = LocalDateTime.now();
        // 调用 jdbcTemplate.update
        jdbcTemplate.update(
                """
                UPDATE t_ai_model_config
                SET deleted = 1, delete_time = ?, update_time = ?
                WHERE id = ? AND deleted = 0
                """,
                Timestamp.valueOf(now),
                Timestamp.valueOf(now),
                id);
    }

    /**
     * 批量删除。
     * @param ids 参数 ids
     * @return 返回结果
     */
    @Transactional
    public int batchDelete(List<String> ids) {
        return softDeleteByIds("t_ai_model_config", ids);
    }

    /**
     * softDeleteByIds：业务处理。
     * @param table 参数 table
     * @param ids 参数 ids
     * @return 返回结果
     */
    private int softDeleteByIds(String table, List<String> ids) {
        // 条件判断
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        LocalDateTime now = LocalDateTime.now();
        Timestamp ts = Timestamp.valueOf(now);
        int deleted = 0;
        // 遍历处理
        for (String raw : ids) {
            // 条件判断
            if (raw == null || raw.isBlank()) continue;
            // 调用 jdbcTemplate.update
            deleted += jdbcTemplate.update(
                    "UPDATE " + table + " SET deleted = 1, delete_time = ?, update_time = ? WHERE id = ? AND deleted = 0",
                    ts,
                    ts,
                    raw.trim());
        }
        return deleted;
    }

    /**
     * 执行连通性或功能测试。
     * @param id 参数 id
     * @return 返回结果
     */
    public AiModelConfigTestResult test(String id) {
        long t0 = System.currentTimeMillis();
        AiModelConfigVo vo = getById(id);
        String apiKey = resolveApiKey(id);
        // 条件判断
        if (apiKey == null || apiKey.isBlank()) {
            return AiModelConfigTestResult.fail(
                    "未配置 API Key（请在编辑中填写，或设置环境变量 DASHSCOPE_API_KEY）",
                    null,
                    System.currentTimeMillis() - t0);
        }
        String type = vo.getModelType() == null ? "" : vo.getModelType().toUpperCase(Locale.ROOT);
        // 尝试执行
        try {
            return switch (type) {
                // 匹配分支
                case "EMBEDDING" -> testEmbedding(vo, apiKey, t0);
                // 匹配分支
                case "CHAT" -> testChat(vo, apiKey, t0);
                // 匹配分支
                case "RERANK" -> testRerank(vo, apiKey, t0);
                default -> AiModelConfigTestResult.fail(
                        "不支持的模型类型: " + type, null, System.currentTimeMillis() - t0);
            };
        } catch (Exception ex) {
            return AiModelConfigTestResult.fail(
                    "测试失败: " + ex.getMessage(), null, System.currentTimeMillis() - t0);
        }
    }

    /**
     * testEmbedding：业务处理。
     * @param vo 参数 vo
     * @param apiKey 参数 apiKey
     * @param t0 参数 t0
     * @return 返回结果
     */
    private AiModelConfigTestResult testEmbedding(AiModelConfigVo vo, String apiKey, long t0)
            throws Exception {
        String base = safeBaseUrl(
                vo.getBaseUrl(), "https://dashscope.aliyuncs.com/compatible-mode/v1");
        // 调用 objectMapper.createObjectNode
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", vo.getModelName());
        ArrayNode input = body.putArray("input");
        input.add("ping");
        HttpResponse<String> resp = postJson(base + "/embeddings", apiKey, body.toString());
        long ms = System.currentTimeMillis() - t0;
        // 条件判断
        if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
            return AiModelConfigTestResult.success("向量接口连通正常", resp.statusCode(), ms);
        }
        return AiModelConfigTestResult.fail(
                "向量接口返回 HTTP " + resp.statusCode(),
                resp.statusCode(),
                ms);
    }

    /**
     * testChat：业务处理。
     * @param vo 参数 vo
     * @param apiKey 参数 apiKey
     * @param t0 参数 t0
     * @return 返回结果
     */
    private AiModelConfigTestResult testChat(AiModelConfigVo vo, String apiKey, long t0)
            throws Exception {
        String base = safeBaseUrl(
                vo.getBaseUrl(), "https://dashscope.aliyuncs.com/compatible-mode/v1");
        // 调用 objectMapper.createObjectNode
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", vo.getModelName());
        ArrayNode messages = body.putArray("messages");
        ObjectNode msg = messages.addObject();
        msg.put("role", "user");
        msg.put("content", "ping");
        body.put("max_tokens", 8);
        HttpResponse<String> resp = postJson(base + "/chat/completions", apiKey, body.toString());
        long ms = System.currentTimeMillis() - t0;
        // 条件判断
        if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
            return AiModelConfigTestResult.success("对话接口连通正常", resp.statusCode(), ms);
        }
        return AiModelConfigTestResult.fail(
                "对话接口返回 HTTP " + resp.statusCode(),
                resp.statusCode(),
                ms);
    }

    /**
     * testRerank：业务处理。
     * @param vo 参数 vo
     * @param apiKey 参数 apiKey
     * @param t0 参数 t0
     * @return 返回结果
     */
    private AiModelConfigTestResult testRerank(AiModelConfigVo vo, String apiKey, long t0)
            throws Exception {
        String url = "https://dashscope.aliyuncs.com/api/v1/services/rerank/text-rerank/text-rerank";
        // 调用 objectMapper.createObjectNode
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", vo.getModelName());
        ObjectNode input = body.putObject("input");
        input.put("query", "ping");
        ArrayNode docs = input.putArray("documents");
        docs.add("hello");
        docs.add("world");
        ObjectNode params = body.putObject("parameters");
        params.put("top_n", 1);
        params.put("return_documents", false);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> resp =
                // 调用 httpClient.send
                httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        long ms = System.currentTimeMillis() - t0;
        // 条件判断
        if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
            return AiModelConfigTestResult.success("重排接口连通正常", resp.statusCode(), ms);
        }
        return AiModelConfigTestResult.fail(
                "重排接口返回 HTTP " + resp.statusCode(),
                resp.statusCode(),
                ms);
    }

    /**
     * postJson：业务处理。
     * @param url 参数 url
     * @param apiKey 参数 apiKey
     * @param json 参数 json
     * @return 返回结果
     */
    private HttpResponse<String> postJson(String url, String apiKey, String json) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();
        // 调用 httpClient.send
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    /**
     * resolveApiKey：业务处理。
     * @param id 参数 id
     * @return 返回结果
     */
    private String resolveApiKey(String id) {
        // 调用 jdbcTemplate.query
        String stored = jdbcTemplate.query(
                "SELECT api_key_enc FROM t_ai_model_config WHERE id = ? AND deleted = 0",
                rs -> rs.next() ? rs.getString(1) : null,
                id);
        // 条件判断
        if (stored != null && !stored.isBlank()) {
            // 调用 apiKeyCrypto.decryptFromStorage
            return apiKeyCrypto.decryptFromStorage(stored);
        }
        String env = System.getenv("DASHSCOPE_API_KEY");
        // 条件判断
        if (env != null && !env.isBlank()) {
            return env.trim();
        }
        return null;
    }

    /** 供 Agent / 评测等读取已解密的明文 Key（勿记录日志）。 */
    public String getPlainApiKey(String id) {
        return resolveApiKey(id);
    }

    /**
     * 校验请求参数。
     * @param req 参数 req
     * @param creating 参数 creating
     */
    private void validate(AiModelConfigSaveRequest req, boolean creating) {
        // 条件判断
        if (req == null || req.getName() == null || req.getName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name required");
        }
        // 条件判断
        if (req.getModelType() == null || req.getModelType().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "modelType required");
        }
        String type = req.getModelType().trim().toUpperCase(Locale.ROOT);
        // 条件判断
        if (!MODEL_TYPES.contains(type)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "modelType invalid");
        }
        // 条件判断
        if (req.getModelName() == null || req.getModelName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "modelName required");
        }
        String style = blankToDefault(req.getApiStyle(), "openai_compatible");
        // 条件判断
        if (!API_STYLES.contains(style)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "apiStyle invalid");
        }
        // 条件判断
        if ("openai_compatible".equals(style)
                && (req.getBaseUrl() == null || req.getBaseUrl().isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "baseUrl required for openai_compatible");
        }
        if (req.getBaseUrl() != null && !req.getBaseUrl().isBlank()) {
            OutboundUrlGuard.requireSafeHttpUrl(req.getBaseUrl().trim(), allowLocalHttp);
        }
        // 条件判断
        if ("EMBEDDING".equals(type) && req.getDimension() != null && req.getDimension() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "dimension invalid");
        }
        // 条件判断
        if (req.getEnabled() != null && req.getEnabled() != 0 && req.getEnabled() != 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "enabled must be 0 or 1");
        }
        // 条件判断
        if (creating && (req.getApiKey() == null || req.getApiKey().isBlank())) {
            // 允许创建时不填，运行时可回落环境变量
        }
    }

    /**
     * 构建行映射器。
     * @return 返回结果
     */
    private RowMapper<AiModelConfigVo> mapper() {
        return (rs, rowNum) -> {
            AiModelConfigVo vo = new AiModelConfigVo();
            vo.setId(rs.getString("id"));
            vo.setName(rs.getString("name"));
            vo.setModelType(rs.getString("model_type"));
            vo.setProvider(rs.getString("provider"));
            vo.setModelName(rs.getString("model_name"));
            vo.setBaseUrl(rs.getString("base_url"));
            vo.setApiStyle(rs.getString("api_style"));
            String enc = rs.getString("api_key_enc");
            boolean has = enc != null && !enc.isBlank();
            vo.setHasApiKey(has);
            // 条件判断
            if (has) {
                // 尝试执行
                try {
                    // 调用 apiKeyCrypto.decryptFromStorage
                    vo.setApiKeyMasked(maskApiKey(apiKeyCrypto.decryptFromStorage(enc)));
                } catch (Exception e) {
                    vo.setApiKeyMasked("****（密钥损坏）");
                }
            } else {
                vo.setApiKeyMasked("—");
            }
            int dim = rs.getInt("dimension");
            vo.setDimension(rs.wasNull() ? null : dim);
            double temp = rs.getDouble("temperature");
            vo.setTemperature(rs.wasNull() ? null : temp);
            vo.setEnabled(rs.getInt("enabled"));
            vo.setRemark(rs.getString("remark"));
            Timestamp ct = rs.getTimestamp("create_time");
            vo.setCreateTime(DateTimes.format(ct));
            return vo;
        };
    }

    static String maskApiKey(String key) {
        // 条件判断
        if (key == null || key.isBlank()) return "—";
        String k = key.trim();
        // 条件判断
        if (k.length() <= 8) return "****";
        return k.substring(0, Math.min(4, k.length()))
                + "********"
                + k.substring(k.length() - Math.min(4, k.length()));
    }

    /**
     * blankToNull：业务处理。
     * @param v 参数 v
     * @return 返回结果
     */
    private static String blankToNull(String v) {
        // 条件判断
        if (v == null || v.isBlank()) return null;
        return v.trim();
    }

    /**
     * blankToDefault：业务处理。
     * @param v 参数 v
     * @param def 参数 def
     * @return 返回结果
     */
    private static String blankToDefault(String v, String def) {
        // 条件判断
        if (v == null || v.isBlank()) return def;
        return v.trim();
    }

    /**
     * trimSlash：业务处理。
     * @param url 参数 url
     * @return 返回结果
     */
    private static String trimSlash(String url) {
        // 条件判断
        if (url == null) return "";
        String u = url.trim();
        // 循环处理
        while (u.endsWith("/")) u = u.substring(0, u.length() - 1);
        return u;
    }

    /**
     * abbreviate：业务处理。
     * @param s 参数 s
     * @return 返回结果
     */
    private static String abbreviate(String s) {
        // 条件判断
        if (s == null) return "";
        String t = s.replaceAll("\\s+", " ").trim();
        return t.length() > 180 ? t.substring(0, 180) + "…" : t;
    }
}
