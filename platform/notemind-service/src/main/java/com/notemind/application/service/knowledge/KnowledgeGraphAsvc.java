package com.notemind.application.service.knowledge;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 本地知识图谱（Phase C 最小可测）：JSON 文件存储，不依赖 Neo4j。
 */
@Service
public class KnowledgeGraphAsvc {

    private static final int MAX_PATHS = 50;

    private final ObjectMapper objectMapper;
    private final Path storePath;
    private final CopyOnWriteArrayList<Map<String, Object>> entities = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<Map<String, Object>> relations = new CopyOnWriteArrayList<>();

    public KnowledgeGraphAsvc(
            ObjectMapper objectMapper,
            @Value("${notemind.storage.upload-dir:./data/uploads}") String uploadDir) {
        this.objectMapper = objectMapper;
        this.storePath = Path.of(uploadDir).toAbsolutePath().normalize().getParent().resolve("kg").resolve("default.json");
    }

    @PostConstruct
    public void init() {
        try {
            if (Files.exists(storePath)) {
                Map<String, Object> root = objectMapper.readValue(Files.readString(storePath), new TypeReference<>() {});
                Object es = root.get("entities");
                Object rs = root.get("relations");
                if (es instanceof List<?> list) {
                    for (Object o : list) {
                        if (o instanceof Map<?, ?> m) {
                            entities.add(cast(m));
                        }
                    }
                }
                if (rs instanceof List<?> list) {
                    for (Object o : list) {
                        if (o instanceof Map<?, ?> m) {
                            relations.add(cast(m));
                        }
                    }
                }
            }
            if (entities.isEmpty()) {
                seedDemo();
                persist();
            }
        } catch (Exception e) {
            seedDemo();
        }
    }

    public Map<String, Object> overview() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("entityCount", entities.size());
        m.put("relationCount", relations.size());
        m.put("engine", "local-json");
        // 不返回本机绝对路径，避免信息泄露
        return m;
    }

    public List<Map<String, Object>> search(String query, int limit) {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        int lim = Math.max(1, Math.min(limit, 50));
        List<Map<String, Object>> hits = new ArrayList<>();
        for (Map<String, Object> e : entities) {
            String name = String.valueOf(e.getOrDefault("name", "")).toLowerCase(Locale.ROOT);
            String type = String.valueOf(e.getOrDefault("type", "")).toLowerCase(Locale.ROOT);
            String desc = String.valueOf(e.getOrDefault("description", "")).toLowerCase(Locale.ROOT);
            if (q.isEmpty() || name.contains(q) || type.contains(q) || desc.contains(q)) {
                hits.add(e);
            }
            if (hits.size() >= lim) {
                break;
            }
        }
        return hits;
    }

    public List<Map<String, Object>> multiHop(String entityId, int depth) {
        if (entityId == null || entityId.isBlank()) {
            return List.of();
        }
        int d = Math.max(1, Math.min(depth, 3));
        List<Map<String, Object>> paths = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        visited.add(entityId);
        walk(entityId, d, new ArrayList<>(), visited, paths);
        return paths;
    }

    private void walk(
            String entityId,
            int remain,
            List<Map<String, Object>> path,
            Set<String> visited,
            List<Map<String, Object>> out) {
        if (remain <= 0 || out.size() >= MAX_PATHS) {
            return;
        }
        for (Map<String, Object> rel : relations) {
            if (out.size() >= MAX_PATHS) {
                return;
            }
            String from = String.valueOf(rel.get("from"));
            String to = String.valueOf(rel.get("to"));
            if (!entityId.equals(from) && !entityId.equals(to)) {
                continue;
            }
            String next = entityId.equals(from) ? to : from;
            if (visited.contains(next)) {
                continue;
            }
            Map<String, Object> step = new LinkedHashMap<>();
            step.put("from", from);
            step.put("to", to);
            step.put("type", rel.get("type"));
            step.put("fromName", nameOf(from));
            step.put("toName", nameOf(to));
            List<Map<String, Object>> nextPath = new ArrayList<>(path);
            nextPath.add(step);
            if (remain == 1) {
                out.add(Map.of("hops", nextPath));
            } else {
                Set<String> nextVisited = new HashSet<>(visited);
                nextVisited.add(next);
                walk(next, remain - 1, nextPath, nextVisited, out);
            }
        }
    }

    private String nameOf(String id) {
        for (Map<String, Object> e : entities) {
            if (id.equals(String.valueOf(e.get("id")))) {
                return String.valueOf(e.get("name"));
            }
        }
        return id;
    }

    private void seedDemo() {
        entities.clear();
        relations.clear();
        String x1 = addEntity("PRODUCT", "X1", "去年发布产品，续航约 12 小时，石墨散热");
        String x2 = addEntity("PRODUCT", "X2", "今年发布产品，续航约 18 小时，双风扇主动散热");
        String dept = addEntity("DEPT", "硬件评测中心", "负责接收测试机申请工单");
        String ops = addEntity("DEPT", "产品运营部", "测试机申请需抄送");
        String flow = addEntity("PROCESS", "测试机申请", "向硬件评测中心提交工单，抄送产品运营部");
        addRelation(x1, flow, "RELATED_PROCESS");
        addRelation(x2, flow, "RELATED_PROCESS");
        addRelation(flow, dept, "SUBMIT_TO");
        addRelation(flow, ops, "CC_TO");
        addRelation(x1, x2, "COMPARE_WITH");
    }

    private String addEntity(String type, String name, String description) {
        String id = "e_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("id", id);
        e.put("type", type);
        e.put("name", name);
        e.put("description", description);
        entities.add(e);
        return id;
    }

    private void addRelation(String from, String to, String type) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", "r_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10));
        r.put("from", from);
        r.put("to", to);
        r.put("type", type);
        relations.add(r);
    }

    private void persist() {
        try {
            Files.createDirectories(storePath.getParent());
            Map<String, Object> root = new LinkedHashMap<>();
            root.put("entities", entities);
            root.put("relations", relations);
            Files.writeString(storePath, objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root));
        } catch (Exception ignored) {
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> cast(Map<?, ?> m) {
        return (Map<String, Object>) m;
    }
}
