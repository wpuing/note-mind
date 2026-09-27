package com.notemind.domain.prompt.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.notemind.domain.prompt.entity.PromptTemplate;
import com.notemind.domain.prompt.repositories.PromptTemplateRepository;
import com.notemind.domain.shared.PageData;
import com.notemind.application.service.system.SystemConfigAsvc;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Prompt 模板领域服务：场景校验、变量抽取、编码规范化。
 * <p>负责模板 CRUD 业务规则，不触达 HTTP VO / AI 引擎。</p>
 */
@Service
public class PromptTemplateDsvc {

    /** 支持 {var} 与 {{var}} 两种占位符写法的正则 */
    private static final Pattern VAR_PATTERN =
            Pattern.compile("\\{\\{([a-zA-Z_][a-zA-Z0-9_]*)\\}\\}|\\{([a-zA-Z_][a-zA-Z0-9_]*)\\}");

    /** Prompt 模板仓储 */
    private final PromptTemplateRepository repository;
    /** JSON 序列化（变量列表落库） */
    private final ObjectMapper objectMapper;
    /** 系统配置（分页上限、允许的场景列表） */
    private final SystemConfigAsvc systemConfig;

    /**
     * 构造并注入仓储、序列化器与系统配置。
     *
     * @param repository   Prompt 模板仓储
     * @param objectMapper Jackson 对象映射器
     * @param systemConfig 系统配置应用服务
     */
    public PromptTemplateDsvc(
            PromptTemplateRepository repository,
            ObjectMapper objectMapper,
            SystemConfigAsvc systemConfig) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.systemConfig = systemConfig;
    }

    /**
     * 按名称/场景分页查询 Prompt 模板。
     *
     * @param name     名称模糊条件，可为 null
     * @param scenario 场景精确/过滤条件，可为 null
     * @param page     页码（会规范为 ≥1）
     * @param pageSize 每页条数（会按系统配置钳制）
     * @return 分页结果
     */
    public PageData<PromptTemplate> page(String name, String scenario, int page, int pageSize) {
        // 页码至少为 1，避免非法偏移
        int safePage = Math.max(page, 1);
        // 按系统配置钳制分页大小
        int safeSize = systemConfig.clampPageSize(pageSize);
        // 委托仓储执行分页查询
        return repository.page(name, scenario, safePage, safeSize);
    }

    /**
     * 按主键加载模板；不存在则抛出 404。
     *
     * @param id 模板主键
     * @return 存在的模板实体
     */
    public PromptTemplate requireById(String id) {
        // 仓储按 id 查询，缺失则 404
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "prompt template not found"));
    }

    /**
     * 新建 Prompt 模板：校验、规范化编码、抽取变量并落库。
     *
     * @param code     模板编码（将转大写并规范化）
     * @param name     显示名称
     * @param scenario 使用场景，可空
     * @param content  模板正文（含占位符）
     * @param enabled  启用标记，null 视为 1
     * @param remark   备注，可空
     * @return 持久化后的完整实体
     */
    public PromptTemplate create(String code, String name, String scenario, String content,
                                 Integer enabled, String remark) {
        // 校验必填项与场景、启用值
        validate(code, name, content, scenario, enabled);
        PromptTemplate entity = new PromptTemplate();
        // 生成短主键：p_ + 14 位无连字符 UUID 片段
        entity.setId("p_" + UUID.randomUUID().toString().replace("-", "").substring(0, 14));
        entity.setCode(normalizeCode(code));
        entity.setName(name.trim());
        entity.setScenario(blankToNull(scenario));
        entity.setContent(content);
        // 从正文抽取变量并序列化为 JSON 数组字符串
        entity.setVariablesJson(toJsonArray(extractVariables(content)));
        entity.setEnabled(enabled == null ? 1 : enabled);
        entity.setRemark(blankToNull(remark));
        try {
            // 插入仓储；编码冲突由唯一键约束捕获
            repository.insert(entity);
        } catch (DuplicateKeyException e) {
            // 编码重复 → 409
            throw new ResponseStatusException(HttpStatus.CONFLICT, "模板编码已存在");
        }
        // 回读保证返回库中最新行
        return requireById(entity.getId());
    }

    /**
     * 更新已有 Prompt 模板。
     *
     * @param id       模板主键
     * @param code     新编码
     * @param name     新名称
     * @param scenario 新场景
     * @param content  新正文
     * @param enabled  启用标记
     * @param remark   备注
     * @return 更新后的实体
     */
    public PromptTemplate update(String id, String code, String name, String scenario, String content,
                                 Integer enabled, String remark) {
        // 先确认记录存在
        requireById(id);
        // 校验入参
        validate(code, name, content, scenario, enabled);
        PromptTemplate entity = new PromptTemplate();
        entity.setId(id);
        entity.setCode(normalizeCode(code));
        entity.setName(name.trim());
        entity.setScenario(blankToNull(scenario));
        entity.setContent(content);
        // 重新抽取变量 JSON
        entity.setVariablesJson(toJsonArray(extractVariables(content)));
        entity.setEnabled(enabled == null ? 1 : enabled);
        entity.setRemark(blankToNull(remark));
        try {
            // 更新失败（影响行数为 0）视为未找到
            if (!repository.update(entity)) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "prompt template not found");
            }
        } catch (DuplicateKeyException e) {
            // 编码与其它记录冲突
            throw new ResponseStatusException(HttpStatus.CONFLICT, "模板编码已存在");
        }
        // 回读最新数据
        return requireById(id);
    }

    /**
     * 软删除单条模板。
     *
     * @param id 模板主键
     */
    public void softDelete(String id) {
        // 确认存在后再软删
        requireById(id);
        // 委托仓储软删除
        repository.softDelete(id);
    }

    /**
     * 批量软删除模板。
     *
     * @param ids 主键列表
     * @return 实际删除条数；空入参返回 0
     */
    public int softDeleteBatch(List<String> ids) {
        // 空列表直接返回，避免无意义写库
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        // 委托仓储批量软删
        return repository.softDeleteBatch(ids);
    }

    /**
     * 从模板正文中按出现顺序去重抽取变量名。
     *
     * @param content 模板正文，可为 null/空白
     * @return 变量名列表；无内容时为空列表
     */
    public static List<String> extractVariables(String content) {
        LinkedHashSet<String> vars = new LinkedHashSet<>();
        // 空正文无法抽取
        if (content == null || content.isBlank()) {
            return List.of();
        }
        Matcher m = VAR_PATTERN.matcher(content);
        // 逐个匹配 {{var}} 或 {var}
        while (m.find()) {
            String a = m.group(1);
            String b = m.group(2);
            // 优先取双花括号捕获组，否则取单花括号
            vars.add(a != null ? a : b);
        }
        return new ArrayList<>(vars);
    }

    /**
     * 校验创建/更新入参：编码、名称、正文必填；场景须在白名单；enabled 仅 0/1。
     *
     * @param code     编码
     * @param name     名称
     * @param content  正文
     * @param scenario 场景
     * @param enabled  启用标记
     */
    private void validate(String code, String name, String content, String scenario, Integer enabled) {
        // 编码必填
        if (code == null || code.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "code required");
        }
        // 名称必填
        if (name == null || name.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name required");
        }
        // 正文必填
        if (content == null || content.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "content required");
        }
        String sc = scenario == null ? "" : scenario.trim();
        // 读取系统允许的 Prompt 场景列表
        List<String> allowed = systemConfig.promptScenarios();
        // 非空场景且配置了白名单时，必须命中白名单
        if (!sc.isEmpty() && allowed != null && !allowed.isEmpty() && !new HashSet<>(allowed).contains(sc)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "scenario invalid");
        }
        // enabled 只能是 0 或 1（null 表示默认启用，此处不拦）
        if (enabled != null && enabled != 0 && enabled != 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "enabled must be 0 or 1");
        }
    }

    /**
     * 将变量列表序列化为 JSON 数组字符串；失败时回退为 {@code []}。
     *
     * @param vars 变量名列表
     * @return JSON 数组文本
     */
    private String toJsonArray(List<String> vars) {
        try {
            // 使用注入的 ObjectMapper 序列化
            return objectMapper.writeValueAsString(vars);
        } catch (Exception e) {
            // 序列化异常时返回空数组，避免阻断主流程
            return "[]";
        }
    }

    /**
     * 规范化模板编码：去空白、转大写、空格替换为下划线。
     *
     * @param code 原始编码
     * @return 规范化后的编码
     */
    private static String normalizeCode(String code) {
        return code.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
    }

    /**
     * 将空白字符串转为 null，非空白则 trim。
     *
     * @param s 原始字符串
     * @return null 或 trim 后的值
     */
    private static String blankToNull(String s) {
        // 空或纯空白视为未填写
        if (s == null || s.isBlank()) return null;
        return s.trim();
    }
}
