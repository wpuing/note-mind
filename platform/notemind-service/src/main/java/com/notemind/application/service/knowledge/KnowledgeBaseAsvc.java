package com.notemind.application.service.knowledge;

import com.notemind.application.assembler.knowledge.KnowledgeBaseAssembler;
import com.notemind.client.ai.AiEngineClient;
import com.notemind.client.ai.KnowledgeVectorStatusResult;
import com.notemind.common.result.PageResult;
import com.notemind.domain.knowledge.entity.KnowledgeBase;
import com.notemind.domain.knowledge.repositories.KnowledgeBaseRepository;
import com.notemind.domain.knowledge.service.KnowledgeBaseDsvc;
import com.notemind.domain.shared.PageData;
import com.notemind.interfaces.knowledge.vo.KnowledgeBaseSaveRequest;
import com.notemind.interfaces.knowledge.vo.KnowledgeBaseVo;
import com.notemind.interfaces.knowledge.vo.KnowledgeDocumentVectorVo;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 知识库应用服务：编排 Dsvc + AI 向量状态 enrich。
 * 持久化与领域规则在 {@link KnowledgeBaseDsvc} / Repository。
 */

@Service
public class KnowledgeBaseAsvc {

    private final KnowledgeBaseDsvc knowledgeBaseDsvc;
    private final AiEngineClient aiEngineClient;

    /**
     * 注入知识库领域服务与 AI 引擎客户端。
     *
     * @param knowledgeBaseDsvc 知识库领域服务
     * @param aiEngineClient    AI 引擎 HTTP 客户端
     */
    public KnowledgeBaseAsvc(KnowledgeBaseDsvc knowledgeBaseDsvc, AiEngineClient aiEngineClient) {
        this.knowledgeBaseDsvc = knowledgeBaseDsvc;
        this.aiEngineClient = aiEngineClient;
    }

    /**
     * 查询启用中的知识库列表。
     *
     * @return 启用知识库 VO 列表
     */
    public List<KnowledgeBaseVo> listEnabled() {
        // 领域查询启用库并装配 VO
        return knowledgeBaseDsvc.listEnabled().stream().map(KnowledgeBaseAssembler::toVo).toList();
    }

    /**
     * 分页查询知识库。
     *
     * @param name     名称模糊，可空
     * @param status   状态，可空
     * @param category 分类码，可空
     * @param page     页码
     * @param pageSize 每页条数
     * @return 分页结果
     */
    public PageResult<KnowledgeBaseVo> page(String name, Integer status, String category, int page, int pageSize) {
        // 调用领域服务分页
        PageData<KnowledgeBase> data = knowledgeBaseDsvc.page(name, status, category, page, pageSize);
        List<KnowledgeBaseVo> records = data.getRecords().stream().map(KnowledgeBaseAssembler::toVo).toList();
        return PageResult.of(data.getTotal(), data.getPage(), data.getPageSize(), records);
    }

    /**
     * 按 ID 查询知识库详情（含 AI 向量状态 enrich）。
     *
     * @param id 知识库 ID
     * @return 知识库 VO
     */
    public KnowledgeBaseVo getById(String id) {
        // 领域必存在后装配，再拉 AI 向量统计
        KnowledgeBaseVo vo = KnowledgeBaseAssembler.toVo(knowledgeBaseDsvc.requireById(id));
        enrichVectorFromAi(vo);
        return vo;
    }

    /**
     * 知识库向量状态下钻到文档列表（MySQL 文档元数据 + PG 真实片段/向量计数）。
     *
     * @param knowledgeBaseId 知识库 ID
     * @return 文档向量状态列表
     */
    public List<KnowledgeDocumentVectorVo> listDocumentVectorStatus(String knowledgeBaseId) {
        // 校验知识库存在
        knowledgeBaseDsvc.requireById(knowledgeBaseId);
        // 列出文档元数据行
        List<KnowledgeBaseRepository.DocumentMetaRow> metas =
                // 调用 knowledgeBaseDsvc.listDocumentMetas
                knowledgeBaseDsvc.listDocumentMetas(knowledgeBaseId);
        List<KnowledgeDocumentVectorVo> docs = new ArrayList<>(metas.size());
        // 先用 MySQL 元数据填充基线状态
        for (KnowledgeBaseRepository.DocumentMetaRow row : metas) {
            KnowledgeDocumentVectorVo vo = new KnowledgeDocumentVectorVo();
            vo.setDocumentId(row.documentId());
            vo.setTitle(row.title());
            vo.setFileName(row.fileName());
            vo.setParseStatus(row.parseStatus());
            vo.setErrorMessage(row.errorMessage());
            long metaSegs = readMetaSegmentCount(row.metaJson());
            vo.setSegmentCount(metaSegs);
            vo.setVectorDoneCount(0L);
            vo.setVectorPendingCount(0L);
            vo.setVectorFailedCount(0L);
            // 解析失败直接标 FAILED
            if ("FAILED".equalsIgnoreCase(vo.getParseStatus())) {
                vo.setVectorStatus("FAILED");
            } else {
                // 尚无 PG 数据时按元数据片段数推断状态
                vo.setVectorStatus(KnowledgeBaseDsvc.resolveVectorStatus(metaSegs, 0, 0, 0));
            }
            vo.setCreateTime(row.createTime());
            docs.add(vo);
        }

        // 用 PG 侧真实统计覆盖
        Map<String, KnowledgeVectorStatusResult.DocumentVectorStat> pgMap = loadPgDocStats(knowledgeBaseId);
        // 遍历处理
        for (KnowledgeDocumentVectorVo vo : docs) {
            KnowledgeVectorStatusResult.DocumentVectorStat st = pgMap.get(vo.getDocumentId());
            // 有 PG 统计则覆盖
            if (st != null) {
                applyDocPgStats(vo, st);
            } else if ("FAILED".equalsIgnoreCase(vo.getParseStatus())) {
                // 无 PG 且解析失败保持 FAILED
                vo.setVectorStatus("FAILED");
            }
        }
        return docs;
    }

    /**
     * 从 AI 引擎拉取向量状态并写回 VO；不可达时保留 MySQL 聚合。
     *
     * @param vo 知识库 VO
     */
    private void enrichVectorFromAi(KnowledgeBaseVo vo) {
        // VO 或 ID 为空则跳过
        if (vo == null || vo.getId() == null) return;
        // 尝试执行
        try {
            // 调用 AI 引擎获取知识库向量状态
            KnowledgeVectorStatusResult stats = aiEngineClient.getKnowledgeBaseVectorStatus(vo.getId());
            // 无结果则跳过
            if (stats == null) return;

            // 仅统计仍存活的文档
            Set<String> liveIds = knowledgeBaseDsvc.listLiveDocumentIds(vo.getId());
            long segs = 0L;
            long done = 0L;
            long pending = 0L;
            long vectorFailed = 0L;
            // 累加各文档向量计数
            if (stats.getDocuments() != null) {
                // 遍历处理
                for (KnowledgeVectorStatusResult.DocumentVectorStat d : stats.getDocuments()) {
                    // 跳过已删文档的孤儿向量统计
                    if (d.getDocumentId() == null || !liveIds.contains(d.getDocumentId())) {
                        continue;
                    }
                    segs += nullToZero(d.getSegmentCount());
                    done += nullToZero(d.getVectorDoneCount());
                    pending += nullToZero(d.getVectorPendingCount());
                    vectorFailed += nullToZero(d.getVectorFailedCount());
                }
            }

            // 向量失败 + MySQL 解析失败
            long failed = vectorFailed + knowledgeBaseDsvc.countParseFailedDocuments(vo.getId());

            vo.setSegmentCount(segs);
            vo.setVectorDoneCount(done);
            vo.setVectorPendingCount(pending);
            vo.setVectorFailedCount(failed);
            // 调用 KnowledgeBaseDsvc.resolveVectorStatus
            vo.setVectorStatus(KnowledgeBaseDsvc.resolveVectorStatus(segs, done, pending, vectorFailed));

            // PG 无向量但 MySQL 已有切分元数据：只补展示片段数，不伪装 READY
            if (segs <= 0 && vo.getDocumentCount() != null && vo.getDocumentCount() > 0) {
                long metaSum = knowledgeBaseDsvc.sumMetaSegmentCountReady(vo.getId());
                if (metaSum > 0) {
                    vo.setSegmentCount(metaSum);
                    if (vo.getVectorPendingCount() == null || vo.getVectorPendingCount() <= 0) {
                        vo.setVectorPendingCount(metaSum);
                    }
                    if (vo.getVectorDoneCount() == null || vo.getVectorDoneCount() <= 0) {
                        vo.setVectorDoneCount(0L);
                    }
                    String st = vo.getVectorStatus();
                    if (st == null || "EMPTY".equalsIgnoreCase(st) || "READY".equalsIgnoreCase(st)) {
                        vo.setVectorStatus("PARTIAL");
                    }
                }
            }

            purgeOrphanVectors(stats, liveIds);
        } catch (Exception ignored) {
            // AI 不可达时保留 MySQL 聚合
        }
    }

    /**
     * 清理 PG 中已不在 MySQL 存活文档集合的孤儿向量。
     *
     * @param stats   AI 返回的向量状态
     * @param liveIds 存活文档 ID 集合
     */
    private void purgeOrphanVectors(KnowledgeVectorStatusResult stats, Set<String> liveIds) {
        // 无文档统计则返回
        if (stats.getDocuments() == null || stats.getDocuments().isEmpty()) return;
        // 遍历处理
        for (KnowledgeVectorStatusResult.DocumentVectorStat d : stats.getDocuments()) {
            String docId = d.getDocumentId();
            // 仍存活或 ID 无效则跳过
            if (docId == null || docId.isBlank() || liveIds.contains(docId)) continue;
            // 尝试执行
            try {
                // 调用 AI 删除孤儿文档向量
                aiEngineClient.deleteDocumentVectors(docId);
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * Long 空值转 0。
     *
     * @param v 可空 Long
     * @return 非空值或 0
     */
    private static long nullToZero(Long v) {
        return v == null ? 0L : v;
    }

    /**
     * 从 AI 引擎加载文档级向量统计 Map。
     *
     * @param knowledgeBaseId 知识库 ID
     * @return documentId → 统计
     */
    private Map<String, KnowledgeVectorStatusResult.DocumentVectorStat> loadPgDocStats(String knowledgeBaseId) {
        Map<String, KnowledgeVectorStatusResult.DocumentVectorStat> map = new HashMap<>();
        // 尝试执行
        try {
            // 调用 AI 获取知识库向量状态
            KnowledgeVectorStatusResult stats = aiEngineClient.getKnowledgeBaseVectorStatus(knowledgeBaseId);
            // 无文档列表则返回空 Map
            if (stats == null || stats.getDocuments() == null) return map;
            // 遍历处理
            for (KnowledgeVectorStatusResult.DocumentVectorStat d : stats.getDocuments()) {
                // 有 documentId 才入 Map
                if (d.getDocumentId() != null) {
                    map.put(d.getDocumentId(), d);
                }
            }
        } catch (Exception ignored) {
        }
        return map;
    }

    /**
     * 将 PG 文档统计写到下钻 VO。
     *
     * @param vo 文档向量 VO
     * @param st PG 统计
     */
    private static void applyDocPgStats(
            KnowledgeDocumentVectorVo vo, KnowledgeVectorStatusResult.DocumentVectorStat st) {
        vo.setSegmentCount(st.getSegmentCount());
        vo.setVectorDoneCount(st.getVectorDoneCount());
        vo.setVectorPendingCount(st.getVectorPendingCount());
        vo.setVectorFailedCount(st.getVectorFailedCount());
        vo.setVectorStatus(st.getVectorStatus());
    }

    /**
     * 从 metaJson 粗解析 segmentCount 字段。
     *
     * @param metaJson 文档 meta JSON
     * @return 片段数，失败为 0
     */
    private static long readMetaSegmentCount(String metaJson) {
        // 空 JSON
        if (metaJson == null || metaJson.isBlank()) return 0L;
        // 尝试执行
        try {
            int idx = metaJson.indexOf("\"segmentCount\"");
            // 无该字段
            if (idx < 0) return 0L;
            int colon = metaJson.indexOf(':', idx);
            // 无冒号
            if (colon < 0) return 0L;
            StringBuilder num = new StringBuilder();
            // 遍历处理
            for (int i = colon + 1; i < metaJson.length(); i++) {
                char c = metaJson.charAt(i);
                // 收集数字字符
                if (c == '-' || (c >= '0' && c <= '9')) num.append(c);
                // 否则若
                else if (!Character.isWhitespace(c) && num.length() > 0) break;
            }
            // 未读到数字
            if (num.length() == 0) return 0L;
            return Long.parseLong(num.toString());
        } catch (Exception e) {
            return 0L;
        }
    }

    /**
     * 新建知识库。
     *
     * @param req 保存请求
     * @return 新建后详情（含向量 enrich）
     */

    @Transactional
    public KnowledgeBaseVo create(KnowledgeBaseSaveRequest req) {
        // 请求体必填
        if (req == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name required");
        }
        // 调用领域服务创建
        KnowledgeBase kb = knowledgeBaseDsvc.create(
                req.getName(),
                req.getCategory(),
                req.getDescription(),
                req.getStatus(),
                req.getEmbeddingModelId(),
                req.getChunkStrategyId(),
                req.getRetrievalStrategyId());
        return getById(kb.getId());
    }

    /**
     * 更新知识库。
     *
     * @param id  知识库 ID
     * @param req 保存请求
     * @return 更新后详情
     */

    @Transactional
    public KnowledgeBaseVo update(String id, KnowledgeBaseSaveRequest req) {
        // 请求体必填
        if (req == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name required");
        }
        // 调用领域服务更新
        knowledgeBaseDsvc.update(
                id,
                req.getName(),
                req.getCategory(),
                req.getDescription(),
                req.getStatus(),
                req.getEmbeddingModelId(),
                req.getChunkStrategyId(),
                req.getRetrievalStrategyId());
        return getById(id);
    }

    /**
     * 级联软删除知识库并清理 PG 向量（无事务包裹：向量/文件不可回滚）。
     */
    public void delete(String id) {
        assertNoEmbeddingDocs(id);
        try {
            aiEngineClient.deleteKnowledgeBaseVectors(id);
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY, "清除知识库向量失败，未删除，请检查 AI 引擎后重试");
        }
        if (!knowledgeBaseDsvc.softDeleteCascade(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "knowledge base not found");
        }
    }

    /**
     * 批量级联软删除：逐条提交，部分失败不回滚已成功项。
     */
    public int batchDelete(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ids required");
        }
        int n = 0;
        List<String> vectorFailed = new ArrayList<>();
        List<String> conflictIds = new ArrayList<>();
        for (String id : ids) {
            if (id == null || id.isBlank()) continue;
            String trimmed = id.trim();
            try {
                assertNoEmbeddingDocs(trimmed);
            } catch (ResponseStatusException ex) {
                if (ex.getStatusCode() == HttpStatus.CONFLICT) {
                    conflictIds.add(trimmed);
                    continue;
                }
                throw ex;
            }
            try {
                aiEngineClient.deleteKnowledgeBaseVectors(trimmed);
            } catch (Exception ex) {
                vectorFailed.add(trimmed);
                continue;
            }
            if (!knowledgeBaseDsvc.softDeleteCascade(trimmed)) {
                continue;
            }
            n++;
        }
        if (!vectorFailed.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "部分知识库向量清理失败，未删除: " + String.join(",", vectorFailed)
                            + (n > 0 ? "；已删除 " + n + " 个" : ""));
        }
        if (!conflictIds.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "部分知识库文档正在向量化，未删除: " + String.join(",", conflictIds)
                            + (n > 0 ? "；已删除 " + n + " 个" : ""));
        }
        return n;
    }

    private void assertNoEmbeddingDocs(String knowledgeBaseId) {
        long n = knowledgeBaseDsvc.countEmbeddingDocuments(knowledgeBaseId);
        if (n > 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "知识库内有文档正在向量化，请稍后再删");
        }
    }
}

