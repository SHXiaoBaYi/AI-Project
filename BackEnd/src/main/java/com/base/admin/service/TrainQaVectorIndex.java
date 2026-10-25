package com.base.admin.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 已发布题目的内存快照。一万条题干向量精确检索，换快照不阻塞正在进行的提问。
 */
@Slf4j
@Component
public class TrainQaVectorIndex {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private volatile List<Entry> snapshot = List.of();

    public TrainQaVectorIndex(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public void reload() {
        try {
            List<Entry> loaded = jdbc.query("""
                    SELECT i.id, i.doc_id, i.version_id, i.category, i.question, i.aliases_json,
                           i.answer_text, i.answer_html, i.images_json, i.embedding,
                           v.version_label,
                           CASE WHEN i.version_id = d.latest_version_id THEN 1 ELSE 0 END latest
                    FROM train_qa_item i
                    JOIN train_doc d ON d.id = i.doc_id AND d.is_active = 1 AND d.status = 0
                    JOIN train_doc_version v ON v.id = i.version_id AND v.is_active = 1
                    WHERE i.is_active = 1 AND i.embedding IS NOT NULL
                    """, (rs, i) -> {
                float[] vec = TrainQaVectors.fromBytes(rs.getBytes("embedding"));
                if (vec == null) {
                    return null;
                }
                Entry e = new Entry();
                e.id = rs.getLong("id");
                e.docId = rs.getLong("doc_id");
                e.versionId = rs.getLong("version_id");
                e.category = rs.getString("category") == null ? "cashier" : rs.getString("category");
                e.question = rs.getString("question") == null ? "" : rs.getString("question");
                e.aliases = parseAliases(rs.getString("aliases_json"));
                e.answer = rs.getString("answer_text") == null ? "" : rs.getString("answer_text");
                e.answerHtml = rs.getString("answer_html") == null ? "" : rs.getString("answer_html");
                e.images = parseAliases(rs.getString("images_json"));
                e.versionLabel = rs.getString("version_label") == null ? "" : rs.getString("version_label");
                e.latest = rs.getInt("latest") == 1;
                e.vector = vec;
                return e;
            });
            List<Entry> clean = new ArrayList<>();
            for (Entry e : loaded) {
                if (e != null && StringUtils.hasText(e.question)) {
                    clean.add(e);
                }
            }
            snapshot = List.copyOf(clean);
            log.info("收银答疑题目向量已装载 count={}", clean.size());
        } catch (Exception e) {
            log.warn("装载题目向量失败，提问将回退原检索: {}", e.getMessage());
        }
    }

    public List<Entry> snapshot(String category, boolean oldVersions) {
        String cat = normalize(category);
        List<Entry> all = snapshot;
        List<Entry> out = new ArrayList<>();
        for (Entry e : all) {
            if (!cat.equals(normalize(e.category))) {
                continue;
            }
            if (oldVersions) {
                if (!e.latest) {
                    out.add(e);
                }
            } else if (e.latest) {
                out.add(e);
            }
        }
        return out;
    }

    private List<String> parseAliases(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            List<String> list = objectMapper.readValue(json, new TypeReference<List<String>>() {});
            if (list == null || list.isEmpty()) {
                return List.of();
            }
            List<String> out = new ArrayList<>();
            for (String s : list) {
                if (StringUtils.hasText(s)) {
                    out.add(s.trim());
                }
            }
            return List.copyOf(out);
        } catch (Exception e) {
            return List.of();
        }
    }

    private static String normalize(String category) {
        if (!StringUtils.hasText(category)) {
            return "cashier";
        }
        return category.trim().toLowerCase(Locale.ROOT);
    }

    static final class Entry {
        long id;
        long docId;
        long versionId;
        String category;
        String question;
        List<String> aliases = List.of();
        String answer;
        String answerHtml;
        List<String> images = List.of();
        String versionLabel;
        boolean latest;
        float[] vector;
    }
}
