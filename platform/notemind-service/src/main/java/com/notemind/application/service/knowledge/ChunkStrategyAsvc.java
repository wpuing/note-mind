package com.notemind.application.service.knowledge;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.notemind.common.result.PageResult;
import com.notemind.common.util.DateTimes;
import com.notemind.interfaces.knowledge.vo.ChunkStrategySaveRequest;
import com.notemind.interfaces.knowledge.vo.ChunkStrategyVo;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * 切分策略应用服务：启用列表、分页 CRUD、设默认与批量删除。
 */

@Service
public class ChunkStrategyAsvc {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 注入 JDBC 与 JSON 工具。
     *
     * @param jdbcTemplate Spring JdbcTemplate
     * @param objectMapper Jackson ObjectMapper
     */
    public ChunkStrategyAsvc(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 查询启用中的切分策略（含知识库引用数）。
     *
     * @return 启用策略列表
     */
    public List<ChunkStrategyVo> listEnabled() {
        // 查询启用切分策略及引用数
        return jdbcTemplate.query(
                """
                SELECT c.*,
                       (SELECT COUNT(1) FROM t_knowledge_base kb
                         WHERE kb.deleted = 0 AND kb.chunk_strategy_id = c.id) AS ref_count
                FROM t_chunk_strategy c
                WHERE c.deleted = 0 AND c.enabled = 1
                ORDER BY c.is_default DESC, c.name ASC
                """,
                mapper());
    }

    /**
     * 分页查询切分策略。
     *
     * @param name         名称模糊，可空
     * @param strategyType 策略类型，可空
     * @param page         页码
     * @param pageSize     每页条数
     * @return 分页结果
     */
    public PageResult<ChunkStrategyVo> page(String name, String strategyType, int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(pageSize, 1), 100);
        StringBuilder where = new StringBuilder(" WHERE c.deleted = 0");
        ArrayList<Object> args = new ArrayList<>();
        // 按名称模糊过滤
        if (name != null && !name.isBlank()) {
            where.append(" AND c.name LIKE ?");
            args.add("%" + name.trim() + "%");
        }
        // 按策略类型过滤
        if (strategyType != null && !strategyType.isBlank()) {
            where.append(" AND c.strategy_type = ?");
            args.add(strategyType.trim().toUpperCase(Locale.ROOT));
        }
        // 统计总数
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM t_chunk_strategy c" + where, Long.class, args.toArray());
        ArrayList<Object> listArgs = new ArrayList<>(args);
        listArgs.add(safeSize);
        listArgs.add((safePage - 1) * safeSize);
        // 分页查询策略列表
        List<ChunkStrategyVo> records = jdbcTemplate.query(
                """
                SELECT c.*,
                       (SELECT COUNT(1) FROM t_knowledge_base kb
                         WHERE kb.deleted = 0 AND kb.chunk_strategy_id = c.id) AS ref_count
                FROM t_chunk_strategy c
                """
                        + where
                        + " ORDER BY c.is_default DESC, c.create_time DESC LIMIT ? OFFSET ?",
                mapper(),
                listArgs.toArray());
        return PageResult.of(total == null ? 0L : total, safePage, safeSize, records);
    }

    /**
     * 按 ID 查询切分策略。
     *
     * @param id 策略 ID
     * @return 策略 VO
     */
    public ChunkStrategyVo getById(String id) {
        // 按主键查询
        List<ChunkStrategyVo> rows = jdbcTemplate.query(
                """
                SELECT c.*,
                       (SELECT COUNT(1) FROM t_knowledge_base kb
                         WHERE kb.deleted = 0 AND kb.chunk_strategy_id = c.id) AS ref_count
                FROM t_chunk_strategy c
                WHERE c.deleted = 0 AND c.id = ?
                """,
                mapper(),
                id);
        // 不存在则 404
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "chunk strategy not found");
        }
        return rows.get(0);
    }

    /**
     * 获取默认切分策略；无默认则回退到任意启用策略。
     *
     * @return 策略 VO
     */
    public ChunkStrategyVo getDefaultOrFallback() {
        // 优先查默认启用策略
        List<ChunkStrategyVo> rows = jdbcTemplate.query(
                """
                SELECT c.*, 0 AS ref_count
                FROM t_chunk_strategy c
                WHERE c.deleted = 0 AND c.enabled = 1 AND c.is_default = 1
                ORDER BY c.update_time DESC LIMIT 1
                """,
                mapper());
        // 有默认则直接返回
        if (!rows.isEmpty()) return rows.get(0);
        // 回退：任意启用策略
        List<ChunkStrategyVo> any = jdbcTemplate.query(
                """
                SELECT c.*, 0 AS ref_count
                FROM t_chunk_strategy c
                WHERE c.deleted = 0 AND c.enabled = 1
                ORDER BY c.name LIMIT 1
                """,
                mapper());
        // 仍无则 404
        if (any.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "no enabled chunk strategy");
        }
        return any.get(0);
    }

    /**
     * 新建切分策略；若设为默认则清除其它默认标记。
     *
     * @param req 保存请求
     * @return 新建后的 VO
     */

    @Transactional
    public ChunkStrategyVo create(ChunkStrategySaveRequest req) {
        validate(req, true);
        String id = "cs_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        LocalDateTime now = LocalDateTime.now();
        boolean makeDefault = req.getIsDefault() != null && req.getIsDefault() == 1;
        // 设为默认前先清空其它默认
        if (makeDefault) {
            // 清除全部默认标记
            jdbcTemplate.update("UPDATE t_chunk_strategy SET is_default = 0 WHERE deleted = 0");
        }
        // 插入新策略
        jdbcTemplate.update(
                """
                INSERT INTO t_chunk_strategy (
                  id, create_time, update_time, deleted, name, strategy_type,
                  chunk_size, chunk_overlap, parent_chunk_size, child_chunk_size, child_overlap,
                  separators_json, enabled, is_default, remark
                ) VALUES (?, ?, ?, 0, ?, ?, ?, ?, ?, ?, ?, CAST(? AS JSON), ?, ?, ?)
                """,
                id,
                Timestamp.valueOf(now),
                Timestamp.valueOf(now),
                req.getName().trim(),
                req.getStrategyType().trim().toUpperCase(Locale.ROOT),
                req.getChunkSize(),
                req.getChunkOverlap(),
                req.getParentChunkSize(),
                req.getChildChunkSize(),
                req.getChildOverlap(),
                toSeparatorsJson(req.getSeparators()),
                req.getEnabled() == null ? 1 : (req.getEnabled() == 0 ? 0 : 1),
                makeDefault ? 1 : 0,
                blankToNull(req.getRemark()));
        return getById(id);
    }

    /**
     * 更新切分策略。
     *
     * @param id  策略 ID
     * @param req 保存请求
     * @return 更新后的 VO
     */

    @Transactional
    public ChunkStrategyVo update(String id, ChunkStrategySaveRequest req) {
        ChunkStrategyVo existing = getById(id);
        validate(req, false);
        int enabled = req.getEnabled() == null
                ? (existing.getEnabled() == null ? 1 : existing.getEnabled())
                : (req.getEnabled() == 0 ? 0 : 1);
        int isDefault;
        // 未传 isDefault 则沿用原值
        if (req.getIsDefault() == null) {
            isDefault = existing.getIsDefault() == null ? 0 : existing.getIsDefault();
        } else {
            isDefault = req.getIsDefault() == 0 ? 0 : 1;
        }
        // 设为默认时清除其它默认
        if (isDefault == 1) {
            // 清除除自身外的默认标记
            jdbcTemplate.update("UPDATE t_chunk_strategy SET is_default = 0 WHERE deleted = 0 AND id <> ?", id);
        }
        // 更新策略字段
        jdbcTemplate.update(
                """
                UPDATE t_chunk_strategy
                SET name = ?, strategy_type = ?, chunk_size = ?, chunk_overlap = ?,
                    parent_chunk_size = ?, child_chunk_size = ?, child_overlap = ?,
                    separators_json = CAST(? AS JSON), enabled = ?, is_default = ?,
                    remark = ?, update_time = ?
                WHERE id = ? AND deleted = 0
                """,
                req.getName().trim(),
                req.getStrategyType().trim().toUpperCase(Locale.ROOT),
                req.getChunkSize(),
                req.getChunkOverlap(),
                req.getParentChunkSize(),
                req.getChildChunkSize(),
                req.getChildOverlap(),
                toSeparatorsJson(req.getSeparators()),
                enabled,
                isDefault,
                blankToNull(req.getRemark()),
                Timestamp.valueOf(LocalDateTime.now()),
                id);
        return getById(id);
    }

    /**
     * 逻辑删除切分策略；有引用或为默认时拒绝。
     *
     * @param id 策略 ID
     */

    @Transactional
    public void delete(String id) {
        ChunkStrategyVo existing = getById(id);
        // 统计知识库引用数
        Long refs = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM t_knowledge_base WHERE deleted = 0 AND chunk_strategy_id = ?",
                Long.class,
                id);
        // 仍被引用则冲突
        if (refs != null && refs > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "策略仍被 " + refs + " 个知识库引用，无法删除");
        }
        // 默认策略不可删
        if (existing.getIsDefault() != null && existing.getIsDefault() == 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "默认策略不可删除，请先指定其它默认策略");
        }
        // 逻辑删除
        jdbcTemplate.update(
                """
                UPDATE t_chunk_strategy
                SET deleted = 1, delete_time = ?, update_time = ?
                WHERE id = ? AND deleted = 0
                """,
                Timestamp.valueOf(LocalDateTime.now()),
                Timestamp.valueOf(LocalDateTime.now()),
                id);
    }

    /**
     * 批量删除切分策略；汇总不可删原因。
     *
     * @param ids 策略 ID 列表
     * @return 成功删除条数
     */

    @Transactional
    public int batchDelete(List<String> ids) {
        // ids 必填
        if (ids == null || ids.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ids required");
        }
        int n = 0;
        List<String> errors = new ArrayList<>();
        // 遍历处理
        for (String id : ids) {
            // 跳过空 ID
            if (id == null || id.isBlank()) continue;
            String trimmed = id.trim();
            // 尝试执行
            try {
                delete(trimmed);
                n++;
            } catch (ResponseStatusException ex) {
                // 不存在则跳过
                if (ex.getStatusCode() == HttpStatus.NOT_FOUND) {
                    continue;
                }
                String name;
                // 尝试执行
                try {
                    name = getById(trimmed).getName();
                } catch (Exception ignored) {
                    // 取名失败用 ID
                    name = trimmed;
                }
                errors.add(name + "：" + ex.getReason());
            }
        }
        // 全部失败则抛出汇总冲突
        if (n == 0 && !errors.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, String.join("；", errors));
        }
        return n;
    }

    /**
     * 校验保存请求字段合法性。
     *
     * @param req      保存请求
     * @param creating 是否新建
     */
    private void validate(ChunkStrategySaveRequest req, boolean creating) {
        // 请求体必填
        if (req == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "body required");
        }
        // 名称必填
        if (req.getName() == null || req.getName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "策略名称必填");
        }
        String type = req.getStrategyType() == null ? "" : req.getStrategyType().trim().toUpperCase(Locale.ROOT);
        // 类型枚举校验
        if (!"RECURSIVE".equals(type) && !"PARENT_CHILD".equals(type) && !"FIXED".equals(type)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "strategyType 须为 RECURSIVE / PARENT_CHILD / FIXED");
        }
        req.setStrategyType(type);
        int size = req.getChunkSize() == null ? 500 : req.getChunkSize();
        int overlap = req.getChunkOverlap() == null ? 50 : req.getChunkOverlap();
        // chunkSize 范围
        if (size < 50 || size > 8000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "chunkSize 范围 50~8000");
        }
        // overlap 须小于 size
        if (overlap < 0 || overlap >= size) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "chunkOverlap 须 < chunkSize");
        }
        req.setChunkSize(size);
        req.setChunkOverlap(overlap);
        // 父子分块额外校验
        if ("PARENT_CHILD".equals(type)) {
            int parent = req.getParentChunkSize() == null ? 1200 : req.getParentChunkSize();
            int child = req.getChildChunkSize() == null ? 300 : req.getChildChunkSize();
            int childOv = req.getChildOverlap() == null ? 40 : req.getChildOverlap();
            // 父块须不小于子块
            if (parent < child) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "父块长度须 ≥ 子块长度");
            }
            // 子块重叠须小于子块
            if (childOv >= child) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "子块重叠须 < 子块长度");
            }
            req.setParentChunkSize(parent);
            req.setChildChunkSize(child);
            req.setChildOverlap(childOv);
        }
        // 新建时占位分支（保持与原逻辑一致）
        if (creating && (req.getName() == null)) {
            // no-op
        }
    }

    /**
     * 分隔符列表序列化为 JSON；空则用默认中英分隔符。
     *
     * @param separators 分隔符列表
     * @return JSON 字符串
     */
    private String toSeparatorsJson(List<String> separators) {
        // 尝试执行
        try {
            List<String> list = separators == null || separators.isEmpty()
                    ? List.of("\n\n", "\n", "。", "！", "？", ".", "!", "?", " ", "")
                    : separators;
            // 调用 ObjectMapper 序列化
            return objectMapper.writeValueAsString(list);
        } catch (Exception ex) {
            // 序列化失败
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "separators invalid");
        }
    }

    /**
     * 空白字符串转 null。
     *
     * @param v 原字符串
     * @return trim 后非空或 null
     */
    private static String blankToNull(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }

    /**
     * 构建切分策略行映射器。
     *
     * @return RowMapper
     */
    private RowMapper<ChunkStrategyVo> mapper() {
        return (rs, rowNum) -> {
            ChunkStrategyVo vo = new ChunkStrategyVo();
            vo.setId(rs.getString("id"));
            vo.setName(rs.getString("name"));
            vo.setStrategyType(rs.getString("strategy_type"));
            vo.setChunkSize(rs.getInt("chunk_size"));
            vo.setChunkOverlap(rs.getInt("chunk_overlap"));
            int parent = rs.getInt("parent_chunk_size");
            vo.setParentChunkSize(rs.wasNull() ? null : parent);
            int child = rs.getInt("child_chunk_size");
            vo.setChildChunkSize(rs.wasNull() ? null : child);
            int childOv = rs.getInt("child_overlap");
            vo.setChildOverlap(rs.wasNull() ? null : childOv);
            vo.setSeparators(parseSeparators(rs.getString("separators_json")));
            vo.setEnabled(rs.getInt("enabled"));
            // 尝试执行
            try {
                vo.setIsDefault(rs.getInt("is_default"));
            } catch (Exception ignored) {
                // 无 is_default 列时默认 0
                vo.setIsDefault(0);
            }
            vo.setRemark(rs.getString("remark"));
            Timestamp ct = rs.getTimestamp("create_time");
            vo.setCreateTime(DateTimes.format(ct));
            // 尝试执行
            try {
                vo.setRefCount(rs.getLong("ref_count"));
            } catch (Exception ignored) {
                // 无 ref_count 列时默认 0
                vo.setRefCount(0L);
            }
            return vo;
        };
    }

    /**
     * 解析 separators_json 为列表。
     *
     * @param json JSON 数组字符串
     * @return 分隔符列表
     */
    private List<String> parseSeparators(String json) {
        // 空 JSON
        if (json == null || json.isBlank()) return List.of();
        // 尝试执行
        try {
            // 调用 ObjectMapper 反序列化
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception ex) {
            // 解析失败降级空列表
            return List.of();
        }
    }
}

