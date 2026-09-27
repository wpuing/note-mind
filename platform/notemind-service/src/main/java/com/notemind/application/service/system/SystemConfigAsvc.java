package com.notemind.application.service.system;

import com.notemind.common.util.DateTimes;
import com.notemind.infrastructure.config.NoteMindDefaultsProperties;
import com.notemind.interfaces.system.vo.SystemConfigBatchSaveRequest;
import com.notemind.interfaces.system.vo.SystemConfigVo;
import jakarta.annotation.PostConstruct;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * 系统配置：DB 可改 + 内存缓存；未配置时回落 {@link NoteMindDefaultsProperties}。
 */
@Service
public class SystemConfigAsvc {

    private static final int PAGE_SIZE_HARD_MAX = 500;
    private static final int SESSION_LIST_HARD_MAX = 500;
    private static final int MESSAGE_LIST_HARD_MAX = 2000;
    private static final int TOP_K_HARD_MAX = 200;
    private static final double TEMPERATURE_HARD_MAX = 2.0;

    private final JdbcTemplate jdbcTemplate;
    private final NoteMindDefaultsProperties ymlDefaults;
    private final AtomicBoolean seedsReady = new AtomicBoolean(false);
    /** 整体替换，避免 reload 时空窗回落 yml */
    private volatile ConcurrentHashMap<String, String> cache = new ConcurrentHashMap<>();

    /**
     * 构造 SystemConfigAsvc 并注入依赖。
     * @param jdbcTemplate 参数 jdbcTemplate
     * @param ymlDefaults 参数 ymlDefaults
     */
    public SystemConfigAsvc(JdbcTemplate jdbcTemplate, NoteMindDefaultsProperties ymlDefaults) {
        this.jdbcTemplate = jdbcTemplate;
        this.ymlDefaults = ymlDefaults;
    }

    /**
     * init：业务处理。
     */
    @PostConstruct
    public void init() {
        // 尝试执行
        try {
            ensureTableAndSeeds();
            reload();
        } catch (Exception ex) {
            // 表未建时仍可用 yml 默认；启动不阻断
            System.err.println("[system-config] init skipped: " + ex.getMessage());
        }
    }

    /**
     * reload：业务处理。
     */
    public void reload() {
        // 调用 jdbcTemplate.query
        List<SystemConfigVo> rows = jdbcTemplate.query(
                """
                SELECT id, config_group, config_key, config_value, value_type, label, description,
                       sort_order, editable, update_time
                FROM t_system_config
                WHERE deleted = 0
                ORDER BY config_group, sort_order, config_key
                """,
                mapper());
        ConcurrentHashMap<String, String> next = new ConcurrentHashMap<>();
        // 遍历处理
        for (SystemConfigVo row : rows) {
            // 条件判断
            if (row.getKey() != null) {
                next.put(row.getKey(), row.getValue() == null ? "" : row.getValue());
            }
        }
        this.cache = next;
    }

    /**
     * listAll：业务处理。
     * @return 返回结果
     */
    public List<SystemConfigVo> listAll() {
        ensureTableAndSeeds();
        // 调用 jdbcTemplate.query
        return jdbcTemplate.query(
                """
                SELECT id, config_group, config_key, config_value, value_type, label, description,
                       sort_order, editable, update_time
                FROM t_system_config
                WHERE deleted = 0
                ORDER BY config_group, sort_order, config_key
                """,
                mapper());
    }

    /**
     * saveBatch：业务处理。
     * @param request 参数 request
     * @return 返回结果
     */
    @Transactional
    public List<SystemConfigVo> saveBatch(SystemConfigBatchSaveRequest request) {
        // 条件判断
        if (request == null || request.getItems() == null || request.getItems().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "items required");
        }
        ensureTableAndSeeds();
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        ConcurrentHashMap<String, String> snapshot = new ConcurrentHashMap<>(cache);
        // 遍历处理
        for (SystemConfigBatchSaveRequest.Item item : request.getItems()) {
            // 条件判断
            if (item == null || item.getKey() == null || item.getKey().isBlank()) {
                continue;
            }
            String key = item.getKey().trim();
            SystemConfigVo meta = findByKey(key);
            // 条件判断
            if (meta == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "unknown config key: " + key);
            }
            // 条件判断
            if (meta.getEditable() != null && meta.getEditable() == 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "config not editable: " + key);
            }
            String value = item.getValue() == null ? "" : item.getValue().trim();
            validateValue(meta.getValueType(), value, key);
            // 调用 jdbcTemplate.update
            jdbcTemplate.update(
                    """
                    UPDATE t_system_config
                    SET config_value = ?, update_time = ?
                    WHERE config_key = ? AND deleted = 0
                    """,
                    value,
                    now,
                    key);
            snapshot.put(key, value);
        }
        this.cache = snapshot;
        return listAll();
    }

    // —— 运行时读取（业务侧） ——

    /**
     * getString：业务处理。
     * @param key 参数 key
     * @param fallback 参数 fallback
     * @return 返回结果
     */
    public String getString(String key, String fallback) {
        String v = cache.get(key);
        // 条件判断
        if (v == null || v.isBlank()) {
            return fallback;
        }
        return v.trim();
    }

    /**
     * getInt：业务处理。
     * @param key 参数 key
     * @param fallback 参数 fallback
     * @return 返回结果
     */
    public int getInt(String key, int fallback) {
        String v = getString(key, null);
        // 条件判断
        if (v == null || v.isBlank()) return fallback;
        // 尝试执行
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /**
     * getDouble：业务处理。
     * @param key 参数 key
     * @param fallback 参数 fallback
     * @return 返回结果
     */
    public double getDouble(String key, double fallback) {
        String v = getString(key, null);
        // 条件判断
        if (v == null || v.isBlank()) return fallback;
        // 尝试执行
        try {
            double d = Double.parseDouble(v.trim());
            return Double.isFinite(d) ? d : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /**
     * getCsv：业务处理。
     * @param key 参数 key
     * @param fallback 参数 fallback
     * @return 返回结果
     */
    public List<String> getCsv(String key, List<String> fallback) {
        String v = getString(key, null);
        // 条件判断
        if (v == null || v.isBlank()) {
            return fallback == null ? List.of() : List.copyOf(fallback);
        }
        return Arrays.stream(v.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    /**
     * knowledgeBaseId：业务处理。
     * @return 返回结果
     */
    public String knowledgeBaseId() {
        // 调用 ymlDefaults.getKnowledgeBaseId
        return getString(SystemConfigKeys.DEFAULT_KNOWLEDGE_BASE_ID, ymlDefaults.getKnowledgeBaseId());
    }

    /**
     * 解析知识库 ID（空则用默认）。
     * @param knowledgeBaseId 参数 knowledgeBaseId
     * @return 返回结果
     */
    public String resolveKnowledgeBaseId(String knowledgeBaseId) {
        // 条件判断
        if (knowledgeBaseId == null || knowledgeBaseId.isBlank() || "default".equals(knowledgeBaseId.trim())) {
            return knowledgeBaseId();
        }
        return knowledgeBaseId.trim();
    }

    /**
     * embeddingModelId：业务处理。
     * @return 返回结果
     */
    public String embeddingModelId() {
        // 调用 ymlDefaults.getEmbeddingModelId
        return getString(SystemConfigKeys.DEFAULT_EMBEDDING_MODEL_ID, ymlDefaults.getEmbeddingModelId());
    }

    /**
     * chunkStrategyId：业务处理。
     * @return 返回结果
     */
    public String chunkStrategyId() {
        // 调用 ymlDefaults.getChunkStrategyId
        return getString(SystemConfigKeys.DEFAULT_CHUNK_STRATEGY_ID, ymlDefaults.getChunkStrategyId());
    }

    /**
     * retrievalStrategyId：业务处理。
     * @return 返回结果
     */
    public String retrievalStrategyId() {
        // 调用 ymlDefaults.getRetrievalStrategyId
        return getString(SystemConfigKeys.DEFAULT_RETRIEVAL_STRATEGY_ID, ymlDefaults.getRetrievalStrategyId());
    }

    /**
     * maxPageSize：业务处理。
     * @return 返回结果
     */
    public int maxPageSize() {
        // 调用 ymlDefaults.getMaxPageSize
        return clamp(getInt(SystemConfigKeys.PAGE_MAX_SIZE, ymlDefaults.getMaxPageSize()), 1, PAGE_SIZE_HARD_MAX);
    }

    /**
     * clampPageSize：业务处理。
     * @param pageSize 参数 pageSize
     * @return 返回结果
     */
    public int clampPageSize(int pageSize) {
        int max = maxPageSize();
        return Math.min(Math.max(pageSize, 1), max);
    }

    /**
     * promptScenarios：业务处理。
     * @return 返回结果
     */
    public List<String> promptScenarios() {
        // 调用 ymlDefaults.getPromptScenarios
        return getCsv(SystemConfigKeys.PROMPT_SCENARIOS, ymlDefaults.getPromptScenarios());
    }

    /**
     * uploadExtensions：业务处理。
     * @return 返回结果
     */
    public List<String> uploadExtensions() {
        // 调用 ymlDefaults.getUploadExtensions
        return getCsv(SystemConfigKeys.UPLOAD_EXTENSIONS, ymlDefaults.getUploadExtensions());
    }

    /**
     * sessionListLimit：业务处理。
     * @return 返回结果
     */
    public int sessionListLimit() {
        return clamp(getInt(SystemConfigKeys.CHAT_SESSION_LIST_LIMIT, 100), 1, SESSION_LIST_HARD_MAX);
    }

    /**
     * messageListLimit：业务处理。
     * @return 返回结果
     */
    public int messageListLimit() {
        return clamp(getInt(SystemConfigKeys.CHAT_MESSAGE_LIST_LIMIT, 500), 1, MESSAGE_LIST_HARD_MAX);
    }

    /**
     * 读取系统默认 TopK。
     * @return 返回结果
     */
    public int defaultTopK() {
        return clamp(getInt(SystemConfigKeys.RETRIEVAL_DEFAULT_TOP_K, 5), 1, maxTopK());
    }

    /**
     * maxTopK：业务处理。
     * @return 返回结果
     */
    public int maxTopK() {
        return clamp(getInt(SystemConfigKeys.RETRIEVAL_MAX_TOP_K, 50), 1, TOP_K_HARD_MAX);
    }

    /**
     * 钳制 TopK 到系统上下限。
     * @param topK 参数 topK
     * @return 返回结果
     */
    public int clampTopK(Integer topK) {
        int def = defaultTopK();
        int v = topK == null || topK <= 0 ? def : topK;
        return clamp(v, 1, maxTopK());
    }

    /**
     * chatTemperature：业务处理。
     * @return 返回结果
     */
    public double chatTemperature() {
        return clampTemperature(getDouble(SystemConfigKeys.LLM_CHAT_TEMPERATURE, 0.3), 0.3);
    }

    /**
     * judgeTemperature：业务处理。
     * @return 返回结果
     */
    public double judgeTemperature() {
        return clampTemperature(getDouble(SystemConfigKeys.LLM_JUDGE_TEMPERATURE, 0.0), 0.0);
    }

    /** 供前端下拉：按分组聚合 */
    public Map<String, List<SystemConfigVo>> listGrouped() {
        Map<String, List<SystemConfigVo>> map = new LinkedHashMap<>();
        // 遍历处理
        for (SystemConfigVo vo : listAll()) {
            map.computeIfAbsent(vo.getGroup() == null ? "general" : vo.getGroup(), k -> new ArrayList<>())
                    .add(vo);
        }
        return map;
    }

    /**
     * findByKey：业务处理。
     * @param key 参数 key
     * @return 返回结果
     */
    private SystemConfigVo findByKey(String key) {
        // 调用 jdbcTemplate.query
        List<SystemConfigVo> rows = jdbcTemplate.query(
                """
                SELECT id, config_group, config_key, config_value, value_type, label, description,
                       sort_order, editable, update_time
                FROM t_system_config
                WHERE deleted = 0 AND config_key = ?
                LIMIT 1
                """,
                mapper(),
                key);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /**
     * validateValue：业务处理。
     * @param valueType 参数 valueType
     * @param value 参数 value
     * @param key 参数 key
     */
    private void validateValue(String valueType, String value, String key) {
        String type = valueType == null ? "string" : valueType.toLowerCase(Locale.ROOT);
        // 条件判断
        if ("int".equals(type)) {
            int n;
            // 尝试执行
            try {
                n = Integer.parseInt(value);
            } catch (Exception e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, key + " must be int");
            }
            int[] bounds = intBounds(key);
            // 条件判断
            if (n < bounds[0] || n > bounds[1]) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        key + " must be in [" + bounds[0] + "," + bounds[1] + "]");
            }
        } else if ("decimal".equals(type)) {
            double d;
            // 尝试执行
            try {
                d = Double.parseDouble(value);
            } catch (Exception e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, key + " must be decimal");
            }
            // 条件判断
            if (!Double.isFinite(d)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, key + " must be finite decimal");
            }
            // 条件判断
            if (key != null && key.contains("temperature") && (d < 0.0 || d > TEMPERATURE_HARD_MAX)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, key + " must be in [0," + TEMPERATURE_HARD_MAX + "]");
            }
        } else if ("bool".equals(type)) {
            // 条件判断
            if (!"0".equals(value) && !"1".equals(value)
                    && !"true".equalsIgnoreCase(value)
                    && !"false".equalsIgnoreCase(value)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, key + " must be bool");
            }
        } else if ("csv".equals(type)) {
            // 条件判断
            if (value.length() > 2000) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, key + " csv too long");
            }
        } else if ("string".equals(type) && value.length() > 512) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, key + " string too long");
        }
    }

    /**
     * intBounds：业务处理。
     * @param key 参数 key
     * @return 返回结果
     */
    private int[] intBounds(String key) {
        // 条件判断
        if (SystemConfigKeys.PAGE_MAX_SIZE.equals(key)) {
            return new int[] {1, PAGE_SIZE_HARD_MAX};
        }
        // 条件判断
        if (SystemConfigKeys.CHAT_SESSION_LIST_LIMIT.equals(key)) {
            return new int[] {1, SESSION_LIST_HARD_MAX};
        }
        // 条件判断
        if (SystemConfigKeys.CHAT_MESSAGE_LIST_LIMIT.equals(key)) {
            return new int[] {1, MESSAGE_LIST_HARD_MAX};
        }
        // 条件判断
        if (SystemConfigKeys.RETRIEVAL_DEFAULT_TOP_K.equals(key)
                || SystemConfigKeys.RETRIEVAL_MAX_TOP_K.equals(key)) {
            return new int[] {1, TOP_K_HARD_MAX};
        }
        return new int[] {1, Integer.MAX_VALUE};
    }

    /**
     * ensureTableAndSeeds：业务处理。
     */
    private void ensureTableAndSeeds() {
        // 条件判断
        if (seedsReady.get()) {
            return;
        }
        synchronized (this) {
            // 条件判断
            if (seedsReady.get()) {
                return;
            }
            doEnsureTableAndSeeds();
            seedsReady.set(true);
        }
    }

    /**
     * doEnsureTableAndSeeds：业务处理。
     */
    private void doEnsureTableAndSeeds() {
        // 调用 jdbcTemplate.execute
        jdbcTemplate.execute(
                """
                CREATE TABLE IF NOT EXISTS t_system_config (
                    id               VARCHAR(32)  NOT NULL,
                    create_time      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                    update_time      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
                    deleted          TINYINT(1)   NOT NULL DEFAULT 0,
                    delete_time      DATETIME(3)  NULL,
                    config_group     VARCHAR(64)  NOT NULL DEFAULT 'general',
                    config_key       VARCHAR(128) NOT NULL,
                    config_value     TEXT         NULL,
                    value_type       VARCHAR(32)  NOT NULL DEFAULT 'string',
                    label            VARCHAR(128) NOT NULL DEFAULT '',
                    description      VARCHAR(512) NULL,
                    sort_order       INT          NOT NULL DEFAULT 0,
                    editable         TINYINT      NOT NULL DEFAULT 1,
                    PRIMARY KEY (id),
                    UNIQUE KEY uk_system_config_key (config_key)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
        seedIfAbsent(
                "sc_kb",
                "knowledge",
                SystemConfigKeys.DEFAULT_KNOWLEDGE_BASE_ID,
                // 调用 ymlDefaults.getKnowledgeBaseId
                ymlDefaults.getKnowledgeBaseId(),
                "string",
                "默认知识库 ID",
                "上传/检索/Agent 未指定知识库时使用",
                10);
        seedIfAbsent(
                "sc_embed",
                "knowledge",
                SystemConfigKeys.DEFAULT_EMBEDDING_MODEL_ID,
                // 调用 ymlDefaults.getEmbeddingModelId
                ymlDefaults.getEmbeddingModelId(),
                "string",
                "默认向量模型 ID",
                "新建知识库未指定 embedding 时使用",
                20);
        seedIfAbsent(
                "sc_chunk",
                "knowledge",
                SystemConfigKeys.DEFAULT_CHUNK_STRATEGY_ID,
                // 调用 ymlDefaults.getChunkStrategyId
                ymlDefaults.getChunkStrategyId(),
                "string",
                "默认切分策略 ID",
                "新建知识库未指定切分策略时使用",
                30);
        seedIfAbsent(
                "sc_rs",
                "knowledge",
                SystemConfigKeys.DEFAULT_RETRIEVAL_STRATEGY_ID,
                // 调用 ymlDefaults.getRetrievalStrategyId
                ymlDefaults.getRetrievalStrategyId(),
                "string",
                "默认检索策略 ID",
                "新建知识库未指定检索策略时使用",
                40);
        seedIfAbsent(
                "sc_page",
                "knowledge",
                SystemConfigKeys.PAGE_MAX_SIZE,
                // 调用 ymlDefaults.getMaxPageSize
                String.valueOf(ymlDefaults.getMaxPageSize()),
                "int",
                "列表分页上限",
                "各分页接口 pageSize 最大值",
                50);
        seedIfAbsent(
                "sc_ext",
                "upload",
                SystemConfigKeys.UPLOAD_EXTENSIONS,
                // 调用 ymlDefaults.getUploadExtensions
                String.join(",", ymlDefaults.getUploadExtensions()),
                "csv",
                "允许上传扩展名",
                "逗号分隔，不含点；小写",
                10);
        seedIfAbsent(
                "sc_scene",
                "prompt",
                SystemConfigKeys.PROMPT_SCENARIOS,
                // 调用 ymlDefaults.getPromptScenarios
                String.join(",", ymlDefaults.getPromptScenarios()),
                "csv",
                "Prompt 场景枚举",
                "逗号分隔；保存 Prompt 时校验",
                10);
        seedIfAbsent(
                "sc_topk",
                "retrieval",
                SystemConfigKeys.RETRIEVAL_DEFAULT_TOP_K,
                "5",
                "int",
                "默认 TopK",
                "检索未传 top_k 时的默认值",
                10);
        seedIfAbsent(
                "sc_maxtop",
                "retrieval",
                SystemConfigKeys.RETRIEVAL_MAX_TOP_K,
                "50",
                "int",
                "TopK 上限",
                "检索 top_k 允许的最大值",
                20);
        seedIfAbsent(
                "sc_chat_t",
                "llm",
                SystemConfigKeys.LLM_CHAT_TEMPERATURE,
                "0.3",
                "decimal",
                "对话温度",
                "普通生成默认 temperature",
                10);
        seedIfAbsent(
                "sc_judge_t",
                "llm",
                SystemConfigKeys.LLM_JUDGE_TEMPERATURE,
                "0",
                "decimal",
                "Judge 温度",
                "Agent/评测 Judge 建议为 0",
                20);
        seedIfAbsent(
                "sc_sess",
                "chat",
                SystemConfigKeys.CHAT_SESSION_LIST_LIMIT,
                "100",
                "int",
                "会话列表上限",
                "用户端会话列表 LIMIT",
                10);
        seedIfAbsent(
                "sc_msg",
                "chat",
                SystemConfigKeys.CHAT_MESSAGE_LIST_LIMIT,
                "500",
                "int",
                "消息列表上限",
                "会话消息列表 LIMIT",
                20);
    }

    /**
     * seedIfAbsent：业务处理。
     * @param id 参数 id
     * @param group 参数 group
     * @param key 参数 key
     * @param value 参数 value
     * @param valueType 参数 valueType
     * @param label 参数 label
     * @param description 参数 description
     * @param sortOrder 参数 sortOrder
     */
    private void seedIfAbsent(
            String id,
            String group,
            String key,
            String value,
            String valueType,
            String label,
            String description,
            int sortOrder) {
        // 调用 jdbcTemplate.queryForObject
        Integer n = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM t_system_config WHERE config_key = ? AND deleted = 0",
                Integer.class,
                key);
        LocalDateTime now = LocalDateTime.now();
        // 条件判断
        if (n != null && n > 0) {
            // 调用 jdbcTemplate.queryForObject
            String existing = jdbcTemplate.queryForObject(
                    "SELECT config_value FROM t_system_config WHERE config_key = ? AND deleted = 0 LIMIT 1",
                    String.class,
                    key);
            // 种子含非 ASCII、现值无非 ASCII 且含连续 ?? → 历史乱码，回写 value；否则只刷展示字段
            boolean repairValue = looksGarbled(existing, value);
            // 条件判断
            if (repairValue) {
                // 调用 jdbcTemplate.update
                jdbcTemplate.update(
                        """
                        UPDATE t_system_config
                        SET config_group = ?, config_value = ?, value_type = ?, label = ?, description = ?,
                            sort_order = ?, editable = 1, update_time = ?
                        WHERE config_key = ? AND deleted = 0
                        """,
                        group,
                        value,
                        valueType,
                        label,
                        description,
                        sortOrder,
                        Timestamp.valueOf(now),
                        key);
            } else {
                // 调用 jdbcTemplate.update
                jdbcTemplate.update(
                        """
                        UPDATE t_system_config
                        SET config_group = ?, value_type = ?, label = ?, description = ?,
                            sort_order = ?, editable = 1, update_time = ?
                        WHERE config_key = ? AND deleted = 0
                        """,
                        group,
                        valueType,
                        label,
                        description,
                        sortOrder,
                        Timestamp.valueOf(now),
                        key);
            }
            return;
        }
        // 调用 jdbcTemplate.update
        jdbcTemplate.update(
                """
                INSERT INTO t_system_config (
                  id, create_time, update_time, deleted,
                  config_group, config_key, config_value, value_type, label, description, sort_order, editable
                ) VALUES (?, ?, ?, 0, ?, ?, ?, ?, ?, ?, ?, 1)
                """,
                id,
                Timestamp.valueOf(now),
                Timestamp.valueOf(now),
                group,
                key,
                value,
                valueType,
                label,
                description,
                sortOrder);
    }

    /**
     * 历史错误编码会把中文落成字面量 '?'。
     * 判定：种子含非 ASCII，现值无非 ASCII，且含连续 "??"（避免误伤合法单问号如 FAQ?）。
     */
    private static boolean looksGarbled(String existing, String seed) {
        // 条件判断
        if (existing == null || seed == null || !hasNonAscii(seed)) {
            return false;
        }
        return !hasNonAscii(existing) && existing.contains("??");
    }

    /**
     * hasNonAscii：业务处理。
     * @param value 参数 value
     * @return 返回结果
     */
    private static boolean hasNonAscii(String value) {
        // 条件判断
        if (value == null || value.isEmpty()) {
            return false;
        }
        // 遍历处理
        for (int i = 0; i < value.length(); i++) {
            // 条件判断
            if (value.charAt(i) > 127) {
                return true;
            }
        }
        return false;
    }

    /**
     * clamp：业务处理。
     * @param value 参数 value
     * @param lo 参数 lo
     * @param hi 参数 hi
     * @return 返回结果
     */
    private static int clamp(int value, int lo, int hi) {
        return Math.min(Math.max(value, lo), hi);
    }

    /**
     * clampTemperature：业务处理。
     * @param value 参数 value
     * @param fallback 参数 fallback
     * @return 返回结果
     */
    private static double clampTemperature(double value, double fallback) {
        // 条件判断
        if (!Double.isFinite(value)) {
            return fallback;
        }
        return Math.min(Math.max(value, 0.0), TEMPERATURE_HARD_MAX);
    }

    /**
     * 构建行映射器。
     * @return 返回结果
     */
    private RowMapper<SystemConfigVo> mapper() {
        return (rs, rowNum) -> {
            SystemConfigVo vo = new SystemConfigVo();
            vo.setId(rs.getString("id"));
            vo.setGroup(rs.getString("config_group"));
            vo.setKey(rs.getString("config_key"));
            vo.setValue(rs.getString("config_value"));
            vo.setValueType(rs.getString("value_type"));
            vo.setLabel(rs.getString("label"));
            vo.setDescription(rs.getString("description"));
            vo.setSortOrder(rs.getInt("sort_order"));
            vo.setEditable(rs.getInt("editable"));
            vo.setUpdateTime(DateTimes.format(rs.getTimestamp("update_time")));
            return vo;
        };
    }
}
