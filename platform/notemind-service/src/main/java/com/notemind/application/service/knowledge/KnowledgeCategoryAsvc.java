package com.notemind.application.service.knowledge;

import com.notemind.common.result.PageResult;
import com.notemind.common.util.DateTimes;
import com.notemind.interfaces.knowledge.vo.KnowledgeCategorySaveRequest;
import com.notemind.interfaces.knowledge.vo.KnowledgeCategoryVo;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 知识库分类应用服务：启用列表、分页 CRUD，改编码时同步知识库引用。
 */

@Service
public class KnowledgeCategoryAsvc {

    private static final Pattern CODE_PATTERN = Pattern.compile("^[a-zA-Z][a-zA-Z0-9_-]{0,63}$");

    private final JdbcTemplate jdbcTemplate;

    /**
     * 注入 JDBC 模板。
     *
     * @param jdbcTemplate Spring JdbcTemplate
     */
    public KnowledgeCategoryAsvc(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 查询启用中的分类（知识库表单下拉）。
     *
     * @return 启用分类列表（含引用数）
     */
    public List<KnowledgeCategoryVo> listEnabled() {
        // 查询启用分类及被知识库引用次数
        return jdbcTemplate.query(
                """
                SELECT id, code, name, sort_no, status, create_time,
                       (SELECT COUNT(1) FROM t_knowledge_base kb
                         WHERE kb.deleted = 0 AND kb.category = c.code) AS ref_count
                FROM t_knowledge_category c
                WHERE c.deleted = 0 AND c.status = 1
                ORDER BY c.sort_no ASC, c.name ASC
                """,
                mapper());
    }

    /**
     * 分页查询分类。
     *
     * @param keyword  名称/编码模糊关键字，可空
     * @param status   状态过滤，可空
     * @param page     页码（从 1 起）
     * @param pageSize 每页条数
     * @return 分页结果
     */
    public PageResult<KnowledgeCategoryVo> page(String keyword, Integer status, int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(pageSize, 1), 100);
        StringBuilder where = new StringBuilder(" WHERE c.deleted = 0");
        ArrayList<Object> args = new ArrayList<>();
        // 按名称或编码模糊匹配
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND (c.name LIKE ? OR c.code LIKE ?)");
            String like = "%" + keyword.trim() + "%";
            args.add(like);
            args.add(like);
        }
        // 按启用状态过滤
        if (status != null) {
            where.append(" AND c.status = ?");
            args.add(status);
        }

        // 统计符合条件的总数
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM t_knowledge_category c" + where,
                Long.class,
                args.toArray());
        long totalVal = total == null ? 0L : total;

        String listSql =
                """
                SELECT c.id, c.code, c.name, c.sort_no, c.status, c.create_time,
                       (SELECT COUNT(1) FROM t_knowledge_base kb
                         WHERE kb.deleted = 0 AND kb.category = c.code) AS ref_count
                FROM t_knowledge_category c
                """
                        + where
                        + " ORDER BY c.sort_no ASC, c.create_time DESC LIMIT ? OFFSET ?";
        ArrayList<Object> listArgs = new ArrayList<>(args);
        listArgs.add(safeSize);
        listArgs.add((safePage - 1) * safeSize);
        // 分页查询分类列表
        List<KnowledgeCategoryVo> records = jdbcTemplate.query(listSql, mapper(), listArgs.toArray());
        return PageResult.of(totalVal, safePage, safeSize, records);
    }

    /**
     * 按 ID 查询分类详情。
     *
     * @param id 分类 ID
     * @return 分类 VO
     */
    public KnowledgeCategoryVo getById(String id) {
        // 按主键查询未删除分类
        List<KnowledgeCategoryVo> rows = jdbcTemplate.query(
                """
                SELECT c.id, c.code, c.name, c.sort_no, c.status, c.create_time,
                       (SELECT COUNT(1) FROM t_knowledge_base kb
                         WHERE kb.deleted = 0 AND kb.category = c.code) AS ref_count
                FROM t_knowledge_category c
                WHERE c.deleted = 0 AND c.id = ?
                """,
                mapper(),
                id);
        // 不存在则 404
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "category not found");
        }
        return rows.get(0);
    }

    /**
     * 新建分类。
     *
     * @param req 保存请求
     * @return 新建后的分类 VO
     */

    @Transactional
    public KnowledgeCategoryVo create(KnowledgeCategorySaveRequest req) {
        // 请求体必填
        if (req == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "body required");
        }
        String code = normalizeCode(req.getCode());
        String name = requireName(req.getName());
        assertCodeUnique(code, null);
        String id = "cat_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        LocalDateTime now = LocalDateTime.now();
        int sortNo = req.getSortNo() == null ? 0 : req.getSortNo();
        int status = req.getStatus() == null ? 1 : (req.getStatus() == 0 ? 0 : 1);
        // 插入分类记录
        jdbcTemplate.update(
                """
                INSERT INTO t_knowledge_category (
                  id, create_time, update_time, deleted, code, name, sort_no, status
                ) VALUES (?, ?, ?, 0, ?, ?, ?, ?)
                """,
                id,
                Timestamp.valueOf(now),
                Timestamp.valueOf(now),
                code,
                name,
                sortNo,
                status);
        return getById(id);
    }

    /**
     * 更新分类；若改编码且已被引用，同步更新知识库.category。
     *
     * @param id  分类 ID
     * @param req 保存请求
     * @return 更新后的分类 VO
     */

    @Transactional
    public KnowledgeCategoryVo update(String id, KnowledgeCategorySaveRequest req) {
        KnowledgeCategoryVo existing = getById(id);
        // 请求体必填
        if (req == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "body required");
        }
        String code = normalizeCode(req.getCode() != null ? req.getCode() : existing.getCode());
        String name = requireName(req.getName() != null ? req.getName() : existing.getName());
        assertCodeUnique(code, id);
        int sortNo = req.getSortNo() == null
                ? (existing.getSortNo() == null ? 0 : existing.getSortNo())
                : req.getSortNo();
        int status = req.getStatus() == null
                ? (existing.getStatus() == null ? 1 : existing.getStatus())
                : (req.getStatus() == 0 ? 0 : 1);

        // 若改 code 且已被引用，同步更新知识库.category（保持引用不断）
        if (!code.equals(existing.getCode())) {
            // 批量把知识库上的旧分类码改为新码
            jdbcTemplate.update(
                    """
                    UPDATE t_knowledge_base
                    SET category = ?, update_time = ?
                    WHERE deleted = 0 AND category = ?
                    """,
                    code,
                    Timestamp.valueOf(LocalDateTime.now()),
                    existing.getCode());
        }

        // 更新分类主表字段
        jdbcTemplate.update(
                """
                UPDATE t_knowledge_category
                SET code = ?, name = ?, sort_no = ?, status = ?, update_time = ?
                WHERE id = ? AND deleted = 0
                """,
                code,
                name,
                sortNo,
                status,
                Timestamp.valueOf(LocalDateTime.now()),
                id);
        return getById(id);
    }

    /**
     * 逻辑删除分类；仍有知识库引用时拒绝删除。
     *
     * @param id 分类 ID
     */

    @Transactional
    public void delete(String id) {
        KnowledgeCategoryVo existing = getById(id);
        // 统计仍引用该分类码的知识库数量
        Long refs = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(1) FROM t_knowledge_base
                WHERE deleted = 0 AND category = ?
                """,
                Long.class,
                existing.getCode());
        // 有引用则冲突，禁止删除
        if (refs != null && refs > 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "分类仍被 " + refs + " 个知识库引用，无法删除");
        }
        // 逻辑删除分类
        jdbcTemplate.update(
                """
                UPDATE t_knowledge_category
                SET deleted = 1, delete_time = ?, update_time = ?
                WHERE id = ? AND deleted = 0
                """,
                Timestamp.valueOf(LocalDateTime.now()),
                Timestamp.valueOf(LocalDateTime.now()),
                id);
    }

    /**
     * 断言分类编码唯一。
     *
     * @param code      分类编码
     * @param excludeId 更新时排除自身 ID，新建传 null
     */
    private void assertCodeUnique(String code, String excludeId) {
        Long n;
        // 新建：全局查重
        if (excludeId == null) {
            // 统计同编码未删除记录数
            n = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM t_knowledge_category WHERE deleted = 0 AND code = ?",
                    Long.class,
                    code);
        } else {
            // 更新：排除自身后再查重
            n = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM t_knowledge_category WHERE deleted = 0 AND code = ? AND id <> ?",
                    Long.class,
                    code,
                    excludeId);
        }
        // 已存在同编码则冲突
        if (n != null && n > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "分类编码已存在: " + code);
        }
    }

    /**
     * 规范化并校验分类编码。
     *
     * @param raw 原始编码
     * @return 小写规范化编码
     */
    private static String normalizeCode(String raw) {
        // 编码必填
        if (raw == null || raw.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "分类编码必填");
        }
        String code = raw.trim();
        // 校验编码格式
        if (!CODE_PATTERN.matcher(code).matches()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "分类编码须以字母开头，仅含字母数字_-，最长64");
        }
        return code.toLowerCase(Locale.ROOT);
    }

    /**
     * 校验并裁剪分类名称。
     *
     * @param raw 原始名称
     * @return 去空白后的名称
     */
    private static String requireName(String raw) {
        // 名称必填
        if (raw == null || raw.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "分类名称必填");
        }
        String name = raw.trim();
        // 名称长度上限
        if (name.length() > 128) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "分类名称过长");
        }
        return name;
    }

    /**
     * 构建分类行映射器。
     *
     * @return RowMapper
     */
    private RowMapper<KnowledgeCategoryVo> mapper() {
        return (rs, rowNum) -> {
            KnowledgeCategoryVo vo = new KnowledgeCategoryVo();
            vo.setId(rs.getString("id"));
            vo.setCode(rs.getString("code"));
            vo.setName(rs.getString("name"));
            vo.setSortNo(rs.getInt("sort_no"));
            vo.setStatus(rs.getInt("status"));
            // 尝试执行
            try {
                vo.setRefCount(rs.getLong("ref_count"));
            } catch (SQLException ignored) {
                // 部分查询未选 ref_count 时默认为 0
                vo.setRefCount(0L);
            }
            Timestamp ct = rs.getTimestamp("create_time");
            vo.setCreateTime(DateTimes.format(ct));
            return vo;
        };
    }
}

