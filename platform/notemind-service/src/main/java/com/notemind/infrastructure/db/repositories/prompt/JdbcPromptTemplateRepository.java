package com.notemind.infrastructure.db.repositories.prompt;

import com.notemind.common.util.DateTimes;
import com.notemind.domain.prompt.entity.PromptTemplate;
import com.notemind.domain.prompt.repositories.PromptTemplateRepository;
import com.notemind.domain.shared.PageData;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Prompt 模板仓储 JDBC 实现：对 {@code t_prompt_template} 做分页查询与软删 CRUD。
 */
@Repository
public class JdbcPromptTemplateRepository implements PromptTemplateRepository {

    /** Spring JDBC 模板，执行 SQL */
    private final JdbcTemplate jdbcTemplate;

    /**
     * 注入 JdbcTemplate。
     *
     * @param jdbcTemplate JDBC 模板
     */
    public JdbcPromptTemplateRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 按名称模糊、场景精确筛选分页查询未删除模板。
     *
     * @param name     名称关键字（可空）
     * @param scenario 场景（可空）
     * @param page     页码（从 1）
     * @param pageSize 每页条数
     * @return 分页数据
     */
    @Override
    public PageData<PromptTemplate> page(String name, String scenario, int page, int pageSize) {
        StringBuilder where = new StringBuilder(" WHERE deleted = 0");
        ArrayList<Object> args = new ArrayList<>();
        // 名称模糊匹配
        if (name != null && !name.isBlank()) {
            where.append(" AND name LIKE ?");
            args.add("%" + name.trim() + "%");
        }
        // 场景精确匹配
        if (scenario != null && !scenario.isBlank()) {
            where.append(" AND scenario = ?");
            args.add(scenario.trim());
        }
        // 通过 JdbcTemplate 统计总数
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM t_prompt_template" + where, Long.class, args.toArray());
        ArrayList<Object> listArgs = new ArrayList<>(args);
        listArgs.add(pageSize);
        listArgs.add((page - 1) * pageSize);
        // 通过 JdbcTemplate 查询当前页记录
        List<PromptTemplate> records = jdbcTemplate.query(
                "SELECT * FROM t_prompt_template"
                        + where
                        + " ORDER BY create_time DESC LIMIT ? OFFSET ?",
                mapper(),
                listArgs.toArray());
        return new PageData<>(total == null ? 0L : total, page, pageSize, records);
    }

    /**
     * 按主键查询未删除模板。
     *
     * @param id 模板 ID
     * @return 存在则 Optional 包装实体，否则 empty
     */
    @Override
    public Optional<PromptTemplate> findById(String id) {
        // 通过 JdbcTemplate 按 id 查询
        List<PromptTemplate> rows = jdbcTemplate.query(
                "SELECT * FROM t_prompt_template WHERE deleted = 0 AND id = ?", mapper(), id);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    /**
     * 插入新 Prompt 模板行。
     *
     * @param entity 领域实体
     */
    @Override
    public void insert(PromptTemplate entity) {
        LocalDateTime now = LocalDateTime.now();
        // 通过 JdbcTemplate 执行 INSERT
        jdbcTemplate.update(
                """
                INSERT INTO t_prompt_template (
                  id, create_time, update_time, deleted,
                  code, name, scenario, content, variables_json, enabled, remark
                ) VALUES (?, ?, ?, 0, ?, ?, ?, ?, ?, ?, ?)
                """,
                entity.getId(),
                Timestamp.valueOf(now),
                Timestamp.valueOf(now),
                entity.getCode(),
                entity.getName(),
                entity.getScenario(),
                entity.getContent(),
                entity.getVariablesJson(),
                entity.getEnabled(),
                entity.getRemark());
    }

    /**
     * 更新未删除模板的业务字段。
     *
     * @param entity 含 id 的实体
     * @return 是否更新到行
     */
    @Override
    public boolean update(PromptTemplate entity) {
        // 通过 JdbcTemplate 执行 UPDATE
        int n = jdbcTemplate.update(
                """
                UPDATE t_prompt_template
                SET code = ?, name = ?, scenario = ?, content = ?,
                    variables_json = ?, enabled = ?, remark = ?, update_time = ?
                WHERE id = ? AND deleted = 0
                """,
                entity.getCode(),
                entity.getName(),
                entity.getScenario(),
                entity.getContent(),
                entity.getVariablesJson(),
                entity.getEnabled(),
                entity.getRemark(),
                Timestamp.valueOf(LocalDateTime.now()),
                entity.getId());
        return n > 0;
    }

    /**
     * 单条软删除。
     *
     * @param id 模板 ID
     * @return 是否删除到行
     */
    @Override
    public boolean softDelete(String id) {
        LocalDateTime now = LocalDateTime.now();
        // 通过 JdbcTemplate 标记 deleted=1
        int n = jdbcTemplate.update(
                """
                UPDATE t_prompt_template
                SET deleted = 1, delete_time = ?, update_time = ?
                WHERE id = ? AND deleted = 0
                """,
                Timestamp.valueOf(now),
                Timestamp.valueOf(now),
                id);
        return n > 0;
    }

    /**
     * 批量软删除；跳过空白 id。
     *
     * @param ids 模板 ID 列表
     * @return 实际删除行数合计
     */
    @Override
    public int softDeleteBatch(List<String> ids) {
        LocalDateTime now = LocalDateTime.now();
        Timestamp ts = Timestamp.valueOf(now);
        int deleted = 0;
        // 逐个 id 执行软删
        for (String raw : ids) {
            // 跳过空 id
            if (raw == null || raw.isBlank()) continue;
            // 通过 JdbcTemplate 软删单条
            deleted += jdbcTemplate.update(
                    """
                    UPDATE t_prompt_template
                    SET deleted = 1, delete_time = ?, update_time = ?
                    WHERE id = ? AND deleted = 0
                    """,
                    ts,
                    ts,
                    raw.trim());
        }
        return deleted;
    }

    /**
     * 构造 ResultSet → PromptTemplate 的行映射器；兼容缺列（scenario/variables_json）。
     *
     * @return RowMapper
     */
    private RowMapper<PromptTemplate> mapper() {
        return (rs, rowNum) -> {
            PromptTemplate e = new PromptTemplate();
            e.setId(rs.getString("id"));
            e.setCode(rs.getString("code"));
            e.setName(rs.getString("name"));
            // 旧库可能无 scenario 列
            try {
                e.setScenario(rs.getString("scenario"));
            // 缺列时置空
            } catch (Exception ignored) {
                e.setScenario(null);
            }
            e.setContent(rs.getString("content"));
            // 旧库可能无 variables_json 列
            try {
                e.setVariablesJson(rs.getString("variables_json"));
            // 缺列时置空
            } catch (Exception ignored) {
                e.setVariablesJson(null);
            }
            e.setEnabled(rs.getInt("enabled"));
            e.setRemark(rs.getString("remark"));
            e.setCreateTime(DateTimes.format(rs.getTimestamp("create_time")));
            return e;
        };
    }
}
