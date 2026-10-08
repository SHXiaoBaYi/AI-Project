package com.base.admin.service;

import com.base.admin.config.DingTalkProperties;
import com.base.admin.domain.dto.TrainAssistantAskDTO;
import com.base.admin.domain.dto.TrainDocSaveDTO;
import com.base.admin.domain.vo.TrainAssistantAskVO;
import com.base.admin.domain.vo.TrainAssistantEntryVO;
import com.base.admin.domain.vo.TrainAssistantMetaVO;
import com.base.admin.domain.vo.TrainDocVO;
import com.base.admin.domain.vo.TrainDocVersionVO;
import com.base.admin.exception.BusinessException;
import com.base.admin.util.SecurityUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.converter.PicturesManager;
import org.apache.poi.hwpf.converter.WordToHtmlConverter;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.hwpf.usermodel.PictureType;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFPicture;
import org.apache.poi.xwpf.usermodel.XWPFPictureData;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.w3c.dom.Document;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class TrainDocService {

    public static final String CATEGORY_CASHIER = "cashier";

    private final JdbcTemplate jdbc;
    private final FileStorageService fileStorage;
    private final AiChatService aiChatService;
    private final ObjectMapper objectMapper;
    private final DingTalkAppService dingTalkAppService;
    private final DingTalkProperties dingTalkProperties;

    @Value("${xby.upload-public-base:}")
    private String uploadPublicBase;

    public List<TrainDocVO> list(String category) {
        String cat = normalizeCategory(category);
        return jdbc.query("""
                SELECT d.id, d.title, d.category, d.description, d.latest_version_id, d.status, d.update_time,
                       v.version_no, v.version_label, v.file_name,
                       (SELECT COUNT(1) FROM train_doc_version x WHERE x.doc_id = d.id AND x.is_active = 1) version_count
                FROM train_doc d
                LEFT JOIN train_doc_version v ON v.id = d.latest_version_id AND v.is_active = 1
                WHERE d.is_active = 1 AND d.category = ?
                ORDER BY d.update_time DESC, d.id DESC
                """, (rs, i) -> {
            TrainDocVO vo = new TrainDocVO();
            vo.setId(rs.getLong("id"));
            vo.setTitle(rs.getString("title"));
            vo.setCategory(rs.getString("category"));
            vo.setDescription(rs.getString("description"));
            long latestId = rs.getLong("latest_version_id");
            vo.setLatestVersionId(rs.wasNull() ? null : latestId);
            vo.setStatus(rs.getInt("status"));
            if (rs.getTimestamp("update_time") != null) {
                vo.setUpdateTime(rs.getTimestamp("update_time").toLocalDateTime());
            }
            int vn = rs.getInt("version_no");
            vo.setLatestVersionNo(rs.wasNull() ? null : vn);
            vo.setLatestVersionLabel(rs.getString("version_label"));
            vo.setLatestFileName(rs.getString("file_name"));
            vo.setVersionCount(rs.getInt("version_count"));
            return vo;
        }, cat);
    }

    @Transactional
    public Long saveDoc(TrainDocSaveDTO dto) {
        String title = dto.getTitle() == null ? "" : dto.getTitle().trim();
        if (title.isEmpty()) {
            throw new BusinessException("请填写文档标题");
        }
        String cat = normalizeCategory(dto.getCategory());
        String desc = blankToNull(dto.getDescription());
        String user = SecurityUtils.getCurrentUsername();
        if (dto.getId() == null) {
            jdbc.update("""
                    INSERT INTO train_doc (title, category, description, status, create_by, is_active)
                    VALUES (?, ?, ?, 0, ?, 1)
                    """, title, cat, desc, user);
            return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        }
        int n = jdbc.update("""
                UPDATE train_doc SET title = ?, description = ? WHERE id = ? AND is_active = 1
                """, title, desc, dto.getId());
        if (n == 0) {
            throw new BusinessException("文档不存在");
        }
        return dto.getId();
    }

    @Transactional
    public void deleteDoc(Long id) {
        int n = jdbc.update("UPDATE train_doc SET is_active = 0 WHERE id = ? AND is_active = 1", id);
        if (n == 0) {
            throw new BusinessException("文档不存在");
        }
        jdbc.update("UPDATE train_doc_version SET is_active = 0 WHERE doc_id = ?", id);
    }

    public List<TrainDocVersionVO> versions(Long docId) {
        Long latestId = jdbc.query("""
                SELECT latest_version_id FROM train_doc WHERE id = ? AND is_active = 1
                """, rs -> rs.next() ? (rs.getObject(1) == null ? null : rs.getLong(1)) : null, docId);
        if (latestId == null && !docExists(docId)) {
            throw new BusinessException("文档不存在");
        }
        Long finalLatest = latestId;
        return jdbc.query("""
                SELECT id, doc_id, version_no, version_label, file_name, file_size, remark, create_by, create_time
                FROM train_doc_version
                WHERE doc_id = ? AND is_active = 1
                ORDER BY version_no DESC
                """, (rs, i) -> {
            TrainDocVersionVO vo = new TrainDocVersionVO();
            vo.setId(rs.getLong("id"));
            vo.setDocId(rs.getLong("doc_id"));
            vo.setVersionNo(rs.getInt("version_no"));
            vo.setVersionLabel(rs.getString("version_label"));
            vo.setFileName(rs.getString("file_name"));
            long size = rs.getLong("file_size");
            vo.setFileSize(rs.wasNull() ? null : size);
            vo.setRemark(rs.getString("remark"));
            vo.setCreateBy(rs.getString("create_by"));
            if (rs.getTimestamp("create_time") != null) {
                vo.setCreateTime(rs.getTimestamp("create_time").toLocalDateTime());
            }
            vo.setLatest(finalLatest != null && finalLatest.equals(vo.getId()));
            return vo;
        }, docId);
    }

    /** 本地上传：新建文档并写入首个版本。 */
    @Transactional
    public Long createWithFile(MultipartFile file, String title, String description,
                               String category, String versionLabel, String remark) {
        TrainDocSaveDTO dto = new TrainDocSaveDTO();
        dto.setTitle(title);
        dto.setDescription(description);
        dto.setCategory(category);
        Long docId = saveDoc(dto);
        uploadVersion(docId, file, versionLabel, remark);
        return docId;
    }

    @Transactional
    public Long uploadVersion(Long docId, MultipartFile file, String versionLabel, String remark) {
        if (!docExists(docId)) {
            throw new BusinessException("文档不存在");
        }
        Path abs = fileStorage.saveTrainDoc(file);
        String relative = fileStorage.toRelativeUploadPath(abs);
        String original = file.getOriginalFilename() == null ? "train.doc" : file.getOriginalFilename();
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(abs);
        } catch (Exception e) {
            throw new BusinessException("读取上传文件失败");
        }
        String ext = original.contains(".") ? original.substring(original.lastIndexOf('.')).toLowerCase(Locale.ROOT) : "";
        String text;
        try {
            text = extractText(bytes, ext);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("培训文档文本抽取失败: {}", e.getMessage());
            throw new BusinessException("无法解析文档内容，请确认是可用的 doc/docx/pdf/txt");
        }
        if (!StringUtils.hasText(text)) {
            throw new BusinessException("文档内容为空，无法用于答疑检索");
        }
        Integer maxNo = jdbc.queryForObject("""
                SELECT COALESCE(MAX(version_no), 0) FROM train_doc_version WHERE doc_id = ? AND is_active = 1
                """, Integer.class, docId);
        int nextNo = (maxNo == null ? 0 : maxNo) + 1;
        String label = StringUtils.hasText(versionLabel) ? versionLabel.trim() : ("v" + nextNo);
        String html = buildRichPreviewHtml(bytes, original);
        jdbc.update("""
                INSERT INTO train_doc_version (doc_id, version_no, version_label, file_name, file_path,
                                              content_text, content_html, file_size, remark, create_by, is_active)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1)
                """, docId, nextNo, label, original, relative, text, html, (long) bytes.length,
                blankToNull(remark), SecurityUtils.getCurrentUsername());
        Long versionId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        jdbc.update("UPDATE train_doc SET latest_version_id = ? WHERE id = ?", versionId, docId);
        return versionId;
    }

    public Map<String, Object> preview(Long versionId) {
        Map<String, Object> meta = jdbc.query("""
                SELECT v.id, v.doc_id, v.version_no, v.version_label, v.file_name, v.file_path,
                       v.content_html, v.content_text, d.title
                FROM train_doc_version v
                JOIN train_doc d ON d.id = v.doc_id AND d.is_active = 1
                WHERE v.id = ? AND v.is_active = 1
                """, rs -> {
            if (!rs.next()) {
                throw new BusinessException("版本不存在");
            }
            java.util.LinkedHashMap<String, Object> map = new java.util.LinkedHashMap<>();
            map.put("id", rs.getLong("id"));
            map.put("docId", rs.getLong("doc_id"));
            map.put("title", nullToEmpty(rs.getString("title")));
            map.put("versionNo", rs.getInt("version_no"));
            map.put("versionLabel", nullToEmpty(rs.getString("version_label")));
            map.put("fileName", nullToEmpty(rs.getString("file_name")));
            map.put("filePath", nullToEmpty(rs.getString("file_path")));
            map.put("html", nullToEmpty(rs.getString("content_html")));
            map.put("text", nullToEmpty(rs.getString("content_text")));
            return map;
        }, versionId);

        String fileName = String.valueOf(meta.get("fileName"));
        String lower = fileName.toLowerCase(Locale.ROOT);
        String renderMode = "html";
        if (lower.endsWith(".pdf")) {
            renderMode = "pdf";
        } else if (lower.endsWith(".docx") || looksLikeDocxZip(nullToEmpty(String.valueOf(meta.get("filePath"))))) {
            renderMode = "docx";
        }
        meta.put("renderMode", renderMode);

        try {
            Path path = fileStorage.resolveUploadPath(String.valueOf(meta.get("filePath")));
            if (path != null && Files.isRegularFile(path)) {
                byte[] bytes = Files.readAllBytes(path);
                boolean word = lower.endsWith(".doc") || lower.endsWith(".docx")
                        || (bytes.length >= 2 && bytes[0] == 'P' && bytes[1] == 'K');
                if (word) {
                    if (bytes.length >= 2 && bytes[0] == 'P' && bytes[1] == 'K') {
                        meta.put("renderMode", "docx");
                    }
                    String rich = buildRichPreviewHtml(bytes, fileName);
                    if (StringUtils.hasText(rich)) {
                        meta.put("html", rich);
                        jdbc.update("UPDATE train_doc_version SET content_html = ? WHERE id = ?", rich, versionId);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("培训文档富预览生成失败 versionId={}: {}", versionId, e.getMessage());
        }
        return meta;
    }

    private boolean looksLikeDocxZip(String filePath) {
        try {
            Path path = fileStorage.resolveUploadPath(filePath);
            if (path == null || !Files.isRegularFile(path)) {
                return false;
            }
            byte[] head = Files.readAllBytes(path);
            return head.length >= 2 && head[0] == 'P' && head[1] == 'K';
        } catch (Exception e) {
            return false;
        }
    }

    public Path resolveVersionFile(Long versionId) {
        String path = jdbc.query("""
                SELECT file_path FROM train_doc_version WHERE id = ? AND is_active = 1
                """, rs -> rs.next() ? rs.getString(1) : null, versionId);
        if (path == null) {
            throw new BusinessException("版本不存在");
        }
        Path file = fileStorage.resolveUploadPath(path);
        if (file == null) {
            throw new BusinessException("文件不存在或已丢失");
        }
        return file;
    }

    public String versionFileName(Long versionId) {
        return jdbc.query("""
                SELECT file_name FROM train_doc_version WHERE id = ? AND is_active = 1
                """, rs -> rs.next() ? rs.getString(1) : "train.doc", versionId);
    }

    /**
     * 长期有效的钉钉扫码入口：HTTPS 页面地址 + dingtalk:// 打开协议。
     * 二维码请扫 pageUrl；须在钉钉内打开并完成企业免登后才能使用。
     */
    public TrainAssistantEntryVO assistantEntry() {
        TrainAssistantEntryVO vo = new TrainAssistantEntryVO();
        String corpId = dingTalkAppService.corpId();
        vo.setCorpId(corpId);
        if (dingTalkAppService.credential() == null) {
            vo.setReady(false);
            vo.setMessage("请先在「系统管理 → 钉钉应用配置」启用企业内部应用");
            return vo;
        }
        if (!StringUtils.hasText(corpId)) {
            vo.setReady(false);
            vo.setMessage("请配置企业 CorpId，否则无法校验是否为本企业员工");
            return vo;
        }
        String base = resolvePublicH5Base();
        if (!StringUtils.hasText(base)) {
            vo.setReady(false);
            vo.setMessage("未配置 H5 根地址（dingtalk.h5-base-url 或 xby.upload-public-base）");
            return vo;
        }
        String pageUrl = base + "/train/cashier-assistant?corpId="
                + java.net.URLEncoder.encode(corpId, java.nio.charset.StandardCharsets.UTF_8);
        String dingTalkUrl = "dingtalk://dingtalkclient/page/link?url="
                + java.net.URLEncoder.encode(pageUrl, java.nio.charset.StandardCharsets.UTF_8)
                + "&pc_slide=true";
        vo.setPageUrl(pageUrl);
        vo.setDingTalkUrl(dingTalkUrl);
        vo.setReady(true);
        vo.setMessage("请用钉钉扫码打开；仅本企业员工免登后可用。链接长期有效，无需反复生成。");
        return vo;
    }

    private String resolvePublicH5Base() {
        if (StringUtils.hasText(dingTalkProperties.getH5BaseUrl())) {
            return dingTalkProperties.getH5BaseUrl().trim().replaceAll("/+$", "");
        }
        if (StringUtils.hasText(uploadPublicBase)) {
            String base = uploadPublicBase.trim().replaceAll("/+$", "");
            if (base.endsWith("/api")) {
                base = base.substring(0, base.length() - 4);
            }
            return base.replaceAll("/+$", "");
        }
        return "";
    }

    public TrainAssistantMetaVO assistantMeta(String category) {
        String cat = normalizeCategory(category);
        TrainAssistantMetaVO vo = new TrainAssistantMetaVO();
        vo.setCategory(cat);
        Map<String, Object> row = jdbc.query("""
                SELECT d.title, d.latest_version_id, v.version_no, v.version_label,
                       (SELECT COUNT(1) FROM train_doc_version x
                        WHERE x.doc_id = d.id AND x.is_active = 1 AND x.id <> IFNULL(d.latest_version_id, 0)) old_cnt
                FROM train_doc d
                LEFT JOIN train_doc_version v ON v.id = d.latest_version_id AND v.is_active = 1
                WHERE d.is_active = 1 AND d.category = ? AND d.status = 0
                ORDER BY d.update_time DESC, d.id DESC
                LIMIT 1
                """, rs -> {
            if (!rs.next()) {
                return null;
            }
            return Map.<String, Object>of(
                    "title", nullToEmpty(rs.getString("title")),
                    "latestVersionId", rs.getObject("latest_version_id") == null ? 0L : rs.getLong("latest_version_id"),
                    "versionNo", rs.getObject("version_no") == null ? 0 : rs.getInt("version_no"),
                    "versionLabel", nullToEmpty(rs.getString("version_label")),
                    "oldCnt", rs.getInt("old_cnt")
            );
        }, cat);
        if (row == null || ((Number) row.get("latestVersionId")).longValue() <= 0) {
            vo.setHasDocument(false);
            vo.setHasOlderVersions(false);
            return vo;
        }
        vo.setHasDocument(true);
        vo.setDocTitle(String.valueOf(row.get("title")));
        vo.setLatestVersionNo(((Number) row.get("versionNo")).intValue());
        vo.setLatestVersionLabel(String.valueOf(row.get("versionLabel")));
        vo.setHasOlderVersions(((Number) row.get("oldCnt")).intValue() > 0);
        return vo;
    }

    public TrainAssistantAskVO ask(TrainAssistantAskDTO dto) {
        String question = dto.getQuestion() == null ? "" : dto.getQuestion().trim();
        if (question.isEmpty()) {
            throw new BusinessException("请输入问题");
        }
        if (!aiChatService.isEnabled()) {
            throw new BusinessException("AI 未配置，请到「系统管理 → AI模型配置」填写可用模型");
        }
        String cat = normalizeCategory(dto.getCategory());
        DocBundle latest = loadLatestDoc(cat);
        if (latest == null) {
            TrainAssistantAskVO empty = new TrainAssistantAskVO();
            empty.setStatus("NOT_FOUND");
            empty.setMessage("暂无培训文档，请先在后台上传收银操作培训资料");
            empty.setHasOlderVersions(false);
            return empty;
        }
        boolean searchOld = Boolean.TRUE.equals(dto.getSearchOld());
        if (!searchOld) {
            List<TrainAssistantAskVO.QaItem> hits = aiExtractQa(question, latest);
            if (!hits.isEmpty()) {
                TrainAssistantAskVO vo = new TrainAssistantAskVO();
                vo.setStatus("HIT");
                vo.setMessage("已根据最新版培训文档找到相关问答");
                vo.setItems(hits);
                vo.setVersionLabel(latest.versionLabel);
                vo.setHasOlderVersions(latest.hasOlder);
                return vo;
            }
            TrainAssistantAskVO vo = new TrainAssistantAskVO();
            vo.setHasOlderVersions(latest.hasOlder);
            vo.setVersionLabel(latest.versionLabel);
            if (latest.hasOlder) {
                vo.setStatus("NEED_OLD");
                vo.setMessage("暂时没有找到答案，是否检索旧版？");
            } else {
                vo.setStatus("NOT_FOUND");
                vo.setMessage("没有找到答案");
            }
            return vo;
        }

        List<DocBundle> older = loadOlderDocs(latest.docId, latest.versionId);
        if (older.isEmpty()) {
            TrainAssistantAskVO vo = new TrainAssistantAskVO();
            vo.setStatus("NOT_FOUND");
            vo.setMessage("没有找到答案");
            vo.setHasOlderVersions(false);
            vo.setVersionLabel(latest.versionLabel);
            return vo;
        }
        List<TrainAssistantAskVO.QaItem> all = new ArrayList<>();
        for (DocBundle old : older) {
            all.addAll(aiExtractQa(question, old));
        }
        TrainAssistantAskVO vo = new TrainAssistantAskVO();
        vo.setHasOlderVersions(false);
        if (all.isEmpty()) {
            vo.setStatus("NOT_FOUND");
            vo.setMessage("没有找到答案");
        } else {
            vo.setStatus("HIT");
            vo.setMessage("已在旧版培训文档中找到相关问答");
            vo.setItems(all);
            vo.setVersionLabel(all.get(0).getVersionLabel());
        }
        return vo;
    }

    private List<TrainAssistantAskVO.QaItem> aiExtractQa(String question, DocBundle doc) {
        IndexedDoc indexed = prepareIndexedDoc(doc);
        log.info("答疑索引 versionId={} images={} textLen={}",
                doc.versionId, indexed.images.size(), indexed.textForAi == null ? 0 : indexed.textForAi.length());
        String clipped = indexed.textForAi.length() > 28000
                ? indexed.textForAi.substring(0, 28000)
                : indexed.textForAi;
        String system = """
                你是收银操作培训答疑助手。根据「培训文档正文」找出与用户问题相关的「简易问答」条目。
                文档中 [图N] 表示第 N 张配图（紧挨相关操作说明）。回答时必须带上相关配图编号。
                只输出 JSON 数组，不要 markdown，不要解释。格式：
                [{"question":"文档中的问法","answer":"文档中的答法","imageRefs":[1,2,3]}]
                规则：
                1. 只摘录文档里已有的问答或可直接对应的说明，禁止编造。
                2. 可返回 1~5 条最相关的；没有相关内容时返回 []。
                3. question/answer 用中文，尽量保留原文要点；answer 里不要写 [图N]。
                4. 同一操作步骤/同一段说明里出现的全部 [图N] 都必须写入 imageRefs（可连续多张，最多 10 个），不要只写一张。
                5. 确实没有配图时 imageRefs 用 []。
                """;
        String user = "用户问题：\n" + question + "\n\n培训文档正文（版本 " + doc.versionLabel + "）：\n" + clipped;
        String raw = aiChatService.chat(system, user);
        return parseQaList(raw, doc.versionLabel, indexed);
    }

    private List<TrainAssistantAskVO.QaItem> parseQaList(String raw, String versionLabel, IndexedDoc indexed) {
        List<TrainAssistantAskVO.QaItem> list = new ArrayList<>();
        if (!StringUtils.hasText(raw)) {
            return list;
        }
        String text = raw.trim();
        text = text.replaceFirst("^```json", "").replaceFirst("^```", "");
        if (text.endsWith("```")) {
            text = text.substring(0, text.length() - 3);
        }
        text = text.trim();
        try {
            int start = text.indexOf('[');
            int end = text.lastIndexOf(']');
            if (start < 0 || end <= start) {
                return list;
            }
            JsonNode arr = objectMapper.readTree(text.substring(start, end + 1));
            if (!arr.isArray()) {
                return list;
            }
            for (JsonNode node : arr) {
                String q = node.path("question").asText("").trim();
                String a = node.path("answer").asText("").trim();
                if (q.isEmpty() && a.isEmpty()) {
                    continue;
                }
                String answer = a.isEmpty() ? "（文档未给出明确答案，请查阅原文）" : a;
                String question = q.isEmpty() ? "相关说明" : q;
                List<Integer> refs = resolveImageRefs(node, question, answer, indexed);
                List<String> imageUrls = toImageUrls(refs, indexed);
                TrainAssistantAskVO.QaItem item = new TrainAssistantAskVO.QaItem();
                item.setQuestion(question);
                item.setAnswer(answer);
                item.setImages(imageUrls);
                item.setAnswerHtml(buildAnswerHtml(answer, imageUrls));
                item.setVersionLabel(versionLabel);
                list.add(item);
            }
        } catch (Exception e) {
            log.warn("解析答疑 JSON 失败: {}", e.getMessage());
        }
        return list;
    }

    /** 从原文件按阅读顺序抽取配图到磁盘，并生成带 [图N] 的检索正文。 */
    private IndexedDoc prepareIndexedDoc(DocBundle doc) {
        IndexedDoc empty = new IndexedDoc();
        empty.textForAi = nullToEmpty(doc == null ? null : doc.text);
        if (doc == null || !StringUtils.hasText(doc.filePath)) {
            return empty;
        }
        try {
            Path path = fileStorage.resolveUploadPath(doc.filePath);
            if (path == null || !Files.isRegularFile(path)) {
                return empty;
            }
            byte[] bytes = Files.readAllBytes(path);
            String lower = doc.fileName == null ? "" : doc.fileName.toLowerCase(Locale.ROOT);
            IndexedDoc indexed;
            if (lower.endsWith(".docx") || (bytes.length >= 2 && bytes[0] == 'P' && bytes[1] == 'K')) {
                indexed = indexDocxPackage(bytes, doc.versionId, doc.text);
            } else if (lower.endsWith(".doc")) {
                indexed = indexDocHwpf(bytes, doc.versionId, doc.text);
            } else {
                indexed = indexHtmlImages(doc.html, doc.text);
            }
            if (indexed.images.isEmpty() && StringUtils.hasText(doc.html)) {
                IndexedDoc fromHtml = indexHtmlImages(doc.html, doc.text);
                if (!fromHtml.images.isEmpty()) {
                    return fromHtml;
                }
            }
            return indexed;
        } catch (Exception e) {
            log.warn("答疑配图索引失败 versionId={}: {}", doc.versionId, e.getMessage());
            return indexHtmlImages(doc.html, doc.text);
        }
    }

    /**
     * 按 document.xml 段落顺序抽取文字与 blip 图片，配图落到 /uploads/train/img/{versionId}/。
     */
    private IndexedDoc indexDocxPackage(byte[] bytes, long versionId, String fallbackText) throws Exception {
        Path tmp = Files.createTempFile("train-docx-", ".docx");
        try {
            Files.write(tmp, bytes);
            fileStorage.clearTrainVersionImages(versionId);
            IndexedDoc indexed = new IndexedDoc();
            try (ZipFile zip = new ZipFile(tmp.toFile())) {
                String documentXml = readZipEntry(zip, "word/document.xml");
                String relsXml = readZipEntry(zip, "word/_rels/document.xml.rels");
                if (!StringUtils.hasText(documentXml)) {
                    indexed.textForAi = nullToEmpty(fallbackText);
                    return indexed;
                }
                Map<String, String> relTarget = parseDocxRels(relsXml);
                Pattern paraPat = Pattern.compile("(?is)<w:p[\\s>].*?</w:p>");
                Pattern textPat = Pattern.compile("(?is)<w:t[^>]*>(.*?)</w:t>");
                Pattern blipPat = Pattern.compile(
                        "(?is)<a:blip[^>]+(?:r:embed|r:link)=\"(rId[^\"]+)\"[^>]*/?>");
                Matcher paraMatcher = paraPat.matcher(documentXml);
                StringBuilder ai = new StringBuilder();
                Set<String> usedTargets = new LinkedHashSet<>();
                while (paraMatcher.find()) {
                    String paraXml = paraMatcher.group();
                    StringBuilder line = new StringBuilder();
                    Matcher tm = textPat.matcher(paraXml);
                    while (tm.find()) {
                        line.append(unescapeXml(tm.group(1)));
                    }
                    if (!line.isEmpty()) {
                        ai.append(line).append('\n');
                    }
                    Matcher bm = blipPat.matcher(paraXml);
                    while (bm.find()) {
                        String rId = bm.group(1);
                        String target = relTarget.get(rId);
                        if (!StringUtils.hasText(target) || usedTargets.contains(target)) {
                            continue;
                        }
                        String entryName = target.startsWith("/") ? target.substring(1) : "word/" + target.replaceFirst("^\\.\\./", "");
                        if (!entryName.startsWith("word/")) {
                            entryName = "word/" + entryName;
                        }
                        entryName = entryName.replace("\\", "/");
                        byte[] imgBytes = readZipEntryBytes(zip, entryName);
                        if (imgBytes == null || imgBytes.length == 0) {
                            // 兼容 Target="media/image1.png"
                            String alt = "word/" + target.replaceFirst("^[/\\\\]+", "");
                            imgBytes = readZipEntryBytes(zip, alt);
                            entryName = alt;
                        }
                        if (imgBytes == null || imgBytes.length == 0) {
                            continue;
                        }
                        String fileHint = entryName.contains("/")
                                ? entryName.substring(entryName.lastIndexOf('/') + 1)
                                : entryName;
                        String url = fileStorage.saveTrainVersionImage(
                                versionId, indexed.images.size() + 1, imgBytes, fileHint);
                        if (!StringUtils.hasText(url)) {
                            continue;
                        }
                        usedTargets.add(target);
                        indexed.images.add(url);
                        ai.append(" [图").append(indexed.images.size()).append("] ");
                    }
                }
                // 补漏：关系里仍有未挂到段落的媒体
                for (Map.Entry<String, String> e : relTarget.entrySet()) {
                    String target = e.getValue();
                    if (!StringUtils.hasText(target) || usedTargets.contains(target)) {
                        continue;
                    }
                    if (!target.toLowerCase(Locale.ROOT).contains("media/")) {
                        continue;
                    }
                    String entryName = target.startsWith("/") ? target.substring(1) : "word/" + target.replaceFirst("^\\.\\./", "");
                    if (!entryName.startsWith("word/")) {
                        entryName = "word/" + entryName;
                    }
                    byte[] imgBytes = readZipEntryBytes(zip, entryName);
                    if (imgBytes == null || imgBytes.length == 0) {
                        continue;
                    }
                    String fileHint = entryName.contains("/")
                            ? entryName.substring(entryName.lastIndexOf('/') + 1)
                            : entryName;
                    String url = fileStorage.saveTrainVersionImage(
                            versionId, indexed.images.size() + 1, imgBytes, fileHint);
                    if (!StringUtils.hasText(url)) {
                        continue;
                    }
                    usedTargets.add(target);
                    indexed.images.add(url);
                    ai.append(" [图").append(indexed.images.size()).append("] ");
                }
                indexed.textForAi = !ai.isEmpty() ? ai.toString().trim() : nullToEmpty(fallbackText);
                fillImageContexts(indexed);
                return indexed;
            }
        } finally {
            try {
                Files.deleteIfExists(tmp);
            } catch (Exception ignored) {
                // ignore
            }
        }
    }

    private IndexedDoc indexDocHwpf(byte[] bytes, long versionId, String fallbackText) throws Exception {
        IndexedDoc indexed = new IndexedDoc();
        if (bytes.length >= 2 && bytes[0] == 'P' && bytes[1] == 'K') {
            return indexDocxPackage(bytes, versionId, fallbackText);
        }
        fileStorage.clearTrainVersionImages(versionId);
        try (HWPFDocument wordDocument = new HWPFDocument(new ByteArrayInputStream(bytes))) {
            String text = nullToEmpty(fallbackText);
            if (!StringUtils.hasText(text)) {
                try (org.apache.poi.hwpf.extractor.WordExtractor extractor =
                             new org.apache.poi.hwpf.extractor.WordExtractor(wordDocument)) {
                    text = nullToEmpty(extractor.getText());
                }
            }
            StringBuilder ai = new StringBuilder(text);
            List<org.apache.poi.hwpf.usermodel.Picture> pictures =
                    wordDocument.getPicturesTable().getAllPictures();
            if (pictures != null) {
                for (org.apache.poi.hwpf.usermodel.Picture picture : pictures) {
                    byte[] data = picture.getContent();
                    if (data == null || data.length == 0) {
                        continue;
                    }
                    String hint = picture.suggestFullFileName();
                    String url = fileStorage.saveTrainVersionImage(
                            versionId, indexed.images.size() + 1, data, hint);
                    if (!StringUtils.hasText(url)) {
                        continue;
                    }
                    indexed.images.add(url);
                    ai.append(" [图").append(indexed.images.size()).append("] ");
                }
            }
            indexed.textForAi = ai.toString().trim();
            fillImageContexts(indexed);
            return indexed;
        }
    }

    private static String readZipEntry(ZipFile zip, String name) throws Exception {
        ZipEntry entry = zip.getEntry(name);
        if (entry == null) {
            return "";
        }
        try (InputStream in = zip.getInputStream(entry)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static byte[] readZipEntryBytes(ZipFile zip, String name) throws Exception {
        ZipEntry entry = zip.getEntry(name);
        if (entry == null) {
            return null;
        }
        try (InputStream in = zip.getInputStream(entry)) {
            return in.readAllBytes();
        }
    }

    private static Map<String, String> parseDocxRels(String relsXml) {
        Map<String, String> map = new HashMap<>();
        if (!StringUtils.hasText(relsXml)) {
            return map;
        }
        Matcher m = Pattern.compile(
                "(?is)<Relationship[^>]+Id=\"(rId[^\"]+)\"[^>]+Target=\"([^\"]+)\"[^>]*/?>").matcher(relsXml);
        while (m.find()) {
            map.put(m.group(1), m.group(2).replace('\\', '/'));
        }
        // Target 在前 Id 在后的写法
        Matcher m2 = Pattern.compile(
                "(?is)<Relationship[^>]+Target=\"([^\"]+)\"[^>]+Id=\"(rId[^\"]+)\"[^>]*/?>").matcher(relsXml);
        while (m2.find()) {
            map.putIfAbsent(m2.group(2), m2.group(1).replace('\\', '/'));
        }
        return map;
    }

    private static String unescapeXml(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&apos;", "'");
    }

    private static void fillImageContexts(IndexedDoc indexed) {
        indexed.contexts.clear();
        String plain = indexed.textForAi == null ? "" : indexed.textForAi;
        for (int i = 0; i < indexed.images.size(); i++) {
            String marker = "[图" + (i + 1) + "]";
            int pos = plain.indexOf(marker);
            if (pos < 0) {
                indexed.contexts.add("");
                continue;
            }
            int from = Math.max(0, pos - 160);
            int to = Math.min(plain.length(), pos + marker.length() + 160);
            indexed.contexts.add(plain.substring(from, to));
        }
    }

    /**
     * 把 HTML 中的图片（data: 或 /uploads）替换为 [图N]，供兜底索引。
     */
    private static IndexedDoc indexHtmlImages(String html, String fallbackText) {
        IndexedDoc indexed = new IndexedDoc();
        String source = StringUtils.hasText(html) ? html : "";
        if (!StringUtils.hasText(source)) {
            indexed.textForAi = nullToEmpty(fallbackText);
            return indexed;
        }
        Pattern imgPat = Pattern.compile(
                "<img[^>]+src=[\"'](data:[^\"']+|/?uploads/[^\"']+)[\"'][^>]*>",
                Pattern.CASE_INSENSITIVE);
        Matcher m = imgPat.matcher(source);
        StringBuffer withMarkers = new StringBuffer();
        while (m.find()) {
            indexed.images.add(m.group(1));
            m.appendReplacement(withMarkers, Matcher.quoteReplacement(" [图" + indexed.images.size() + "] "));
        }
        m.appendTail(withMarkers);
        String plain = withMarkers.toString()
                .replaceAll("(?is)<style[^>]*>.*?</style>", " ")
                .replaceAll("(?is)<script[^>]*>.*?</script>", " ")
                .replaceAll("(?is)<br\\s*/?>", "\n")
                .replaceAll("(?is)</p>", "\n")
                .replaceAll("(?is)</tr>", "\n")
                .replaceAll("(?is)<[^>]+>", " ")
                .replace("&nbsp;", " ")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&")
                .replaceAll("[ \\t\\x0B\\f\\r]+", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
        indexed.textForAi = StringUtils.hasText(plain) ? plain : nullToEmpty(fallbackText);
        fillImageContexts(indexed);
        return indexed;
    }

    private static final int MAX_IMAGES_PER_ANSWER = 10;

    private static List<Integer> resolveImageRefs(JsonNode node, String question, String answer, IndexedDoc indexed) {
        List<Integer> refs = new ArrayList<>();
        if (indexed.images.isEmpty()) {
            return refs;
        }
        for (String field : List.of("imageRefs", "images", "image_refs", "pics")) {
            JsonNode refNode = node.path(field);
            if (!refNode.isArray()) {
                continue;
            }
            for (JsonNode r : refNode) {
                int n = 0;
                if (r.isNumber()) {
                    n = r.asInt(0);
                } else {
                    String s = r.asText("").trim();
                    Matcher mm = Pattern.compile("(\\d+)").matcher(s);
                    if (mm.find()) {
                        n = Integer.parseInt(mm.group(1));
                    }
                }
                addRef(refs, n, indexed);
            }
        }
        // answer 里若残留 [图N]
        Matcher inAnswer = Pattern.compile("\\[图(\\d+)\\]").matcher(answer + " " + question);
        while (inAnswer.find()) {
            addRef(refs, Integer.parseInt(inAnswer.group(1)), indexed);
        }
        // 无论 AI 是否已返回，都补齐同一段正文附近的全部配图（多图步骤常见只回 1 张）
        mergeRefs(refs, findSectionImageRefs(question, answer, indexed), indexed);
        if (refs.isEmpty()) {
            mergeRefs(refs, guessImageRefs(question + " " + answer, indexed), indexed);
        }
        // 把连续成簇的配图补全（如已有 5、7 → 补上中间的 6）
        refs = expandImageClusters(refs, indexed);
        refs.sort(Integer::compareTo);
        if (refs.size() > MAX_IMAGES_PER_ANSWER) {
            return new ArrayList<>(refs.subList(0, MAX_IMAGES_PER_ANSWER));
        }
        return refs;
    }

    private static void addRef(List<Integer> refs, int n, IndexedDoc indexed) {
        if (n > 0 && n <= indexed.images.size() && !refs.contains(n)) {
            refs.add(n);
        }
    }

    private static void mergeRefs(List<Integer> target, List<Integer> extra, IndexedDoc indexed) {
        if (extra == null) {
            return;
        }
        for (Integer n : extra) {
            if (n != null) {
                addRef(target, n, indexed);
            }
        }
    }

    /**
     * 在正文中定位问答所属段落窗口，收集窗口内全部 [图N]。
     * 覆盖「一步多图 / 连续截图」场景。
     */
    private static List<Integer> findSectionImageRefs(String question, String answer, IndexedDoc indexed) {
        List<Integer> refs = new ArrayList<>();
        String plain = indexed.textForAi == null ? "" : indexed.textForAi;
        if (plain.isEmpty()) {
            return refs;
        }
        int[] span = bestMatchSpan(plain, answer);
        if (span == null) {
            span = bestMatchSpan(plain, question);
        }
        if (span == null) {
            return refs;
        }
        int matchStart = span[0];
        int matchEnd = span[1];
        // 向前扩到上一段空白，向后按答案长度再扩一截，尽量包住整段操作说明
        int from = matchStart;
        while (from > 0 && matchStart - from < 400) {
            char c = plain.charAt(from - 1);
            if (c == '\n' && from > 1 && plain.charAt(from - 2) == '\n') {
                break;
            }
            from--;
        }
        from = Math.max(0, from - 80);
        int answerPad = Math.min(1200, Math.max(answer == null ? 0 : answer.length() * 2, 360));
        int to = Math.min(plain.length(), matchEnd + answerPad);
        // 若窗口内后续仍是连续 [图N]，继续吃掉后面紧邻的配图
        to = extendWindowThroughImageCluster(plain, to, indexed.images.size());

        String window = plain.substring(from, to);
        Matcher m = Pattern.compile("\\[图(\\d+)\\]").matcher(window);
        while (m.find()) {
            addRef(refs, Integer.parseInt(m.group(1)), indexed);
            if (refs.size() >= MAX_IMAGES_PER_ANSWER) {
                break;
            }
        }
        return refs;
    }

    /** 若 to 之后紧挨着更多 [图N]（中间几乎只有空白），把窗口再往后扩。 */
    private static int extendWindowThroughImageCluster(String plain, int to, int imageCount) {
        int cursor = to;
        int guard = 0;
        while (cursor < plain.length() && guard++ < imageCount + 5) {
            int next = plain.indexOf("[图", cursor);
            if (next < 0 || next - cursor > 120) {
                break;
            }
            String between = plain.substring(cursor, next).replaceAll("\\s+", "");
            if (!between.isEmpty() && between.length() > 12) {
                break;
            }
            Matcher m = Pattern.compile("^\\[图(\\d+)\\]").matcher(plain.substring(next));
            if (!m.find()) {
                break;
            }
            cursor = next + m.group(0).length();
        }
        return Math.min(plain.length(), Math.max(to, cursor));
    }

    /** 已选配图若编号连续成簇，把中间漏掉的编号也补上。 */
    private static List<Integer> expandImageClusters(List<Integer> refs, IndexedDoc indexed) {
        if (refs == null || refs.size() < 2) {
            return refs == null ? new ArrayList<>() : new ArrayList<>(refs);
        }
        List<Integer> sorted = new ArrayList<>(refs);
        sorted.sort(Integer::compareTo);
        List<Integer> out = new ArrayList<>(sorted);
        for (int i = 0; i < sorted.size() - 1; i++) {
            int a = sorted.get(i);
            int b = sorted.get(i + 1);
            if (b - a <= 4) {
                for (int n = a + 1; n < b; n++) {
                    addRef(out, n, indexed);
                }
            }
        }
        // 单侧扩展：对每个已选图，若正文里前后紧邻还有图，一并纳入
        String plain = indexed.textForAi == null ? "" : indexed.textForAi;
        List<Integer> seed = new ArrayList<>(out);
        for (Integer ref : seed) {
            if (ref == null) {
                continue;
            }
            String marker = "[图" + ref + "]";
            int pos = plain.indexOf(marker);
            if (pos < 0) {
                continue;
            }
            // 向后
            int after = pos + marker.length();
            after = extendWindowThroughImageCluster(plain, after, indexed.images.size());
            Matcher mAfter = Pattern.compile("\\[图(\\d+)\\]").matcher(plain.substring(pos, after));
            while (mAfter.find()) {
                addRef(out, Integer.parseInt(mAfter.group(1)), indexed);
            }
            // 向前：看 marker 前 100 字符内是否还有图
            int before = Math.max(0, pos - 100);
            String head = plain.substring(before, pos);
            if (head.replaceAll("\\s+", "").length() <= 12) {
                Matcher mBefore = Pattern.compile("\\[图(\\d+)\\]").matcher(head);
                while (mBefore.find()) {
                    addRef(out, Integer.parseInt(mBefore.group(1)), indexed);
                }
            }
        }
        return out;
    }

    /** @return [start, end) in original haystack, or null */
    private static int[] bestMatchSpan(String haystack, String needle) {
        if (!StringUtils.hasText(haystack) || !StringUtils.hasText(needle)) {
            return null;
        }
        String h = haystack.replaceAll("\\s+", "");
        String n = needle.replaceAll("\\s+", "");
        if (n.length() < 4) {
            return null;
        }
        int bestAt = -1;
        int bestLen = 0;
        int len = Math.min(n.length(), 64);
        while (len >= 8) {
            for (int i = 0; i + len <= n.length(); i += Math.max(1, len / 4)) {
                String sub = n.substring(i, i + len);
                int at = h.indexOf(sub);
                if (at >= 0) {
                    bestAt = at;
                    bestLen = len;
                    // 尽量用更长命中
                    len = 0;
                    break;
                }
            }
            if (bestAt >= 0 && len == 0) {
                break;
            }
            len -= 8;
        }
        if (bestAt < 0) {
            return null;
        }
        int start = Math.min(haystack.length() - 1, bestAt);
        // 粗略映射回原串长度：按无空白比例放大
        double ratio = haystack.length() / (double) Math.max(1, h.length());
        int end = Math.min(haystack.length(), start + (int) Math.ceil(bestLen * ratio) + 8);
        return new int[]{start, Math.max(start + 1, end)};
    }

    private static List<Integer> guessImageRefs(String qaText, IndexedDoc indexed) {
        List<Integer> refs = new ArrayList<>();
        if (!StringUtils.hasText(qaText) || indexed.images.isEmpty()) {
            return refs;
        }
        String needle = qaText.replaceAll("\\s+", "");
        List<int[]> scores = new ArrayList<>();
        for (int i = 0; i < indexed.contexts.size(); i++) {
            String ctx = indexed.contexts.get(i).replaceAll("\\s+", "");
            if (ctx.isEmpty()) {
                continue;
            }
            int score = overlapScore(needle, ctx);
            if (score >= 3) {
                scores.add(new int[]{i + 1, score});
            }
        }
        scores.sort((a, b) -> Integer.compare(b[1], a[1]));
        for (int[] s : scores) {
            if (refs.size() >= 6) {
                break;
            }
            refs.add(s[0]);
        }
        return refs;
    }

    private static int overlapScore(String a, String b) {
        if (a.length() < 2 || b.length() < 2) {
            return 0;
        }
        int hit = 0;
        for (int i = 0; i < a.length() - 1; i++) {
            String gram = a.substring(i, i + 2);
            if (b.contains(gram)) {
                hit++;
            }
            if (hit > 40) {
                break;
            }
        }
        return hit;
    }

    private static List<String> toImageUrls(List<Integer> refs, IndexedDoc indexed) {
        List<String> urls = new ArrayList<>();
        if (refs == null) {
            return urls;
        }
        for (Integer ref : refs) {
            if (ref == null || ref < 1 || ref > indexed.images.size()) {
                continue;
            }
            String url = indexed.images.get(ref - 1);
            if (StringUtils.hasText(url) && !urls.contains(url)) {
                urls.add(url);
            }
        }
        return urls;
    }

    private static String buildAnswerHtml(String answer, List<String> imageUrls) {
        StringBuilder html = new StringBuilder();
        html.append("<div class=\"train-qa-answer\" style=\"line-height:1.7;\">");
        html.append("<div>").append(escapeHtml(answer).replace("\n", "<br/>")).append("</div>");
        if (imageUrls != null) {
            for (String url : imageUrls) {
                html.append("<img src=\"")
                        .append(url)
                        .append("\" alt=\"培训配图\" style=\"max-width:100%;height:auto;display:block;margin:10px 0;border-radius:6px;\" />");
            }
        }
        html.append("</div>");
        return html.toString();
    }

    private DocBundle loadLatestDoc(String category) {
        return jdbc.query("""
                SELECT d.id doc_id, d.latest_version_id, v.version_label, v.content_text, v.content_html,
                       v.file_path, v.file_name,
                       (SELECT COUNT(1) FROM train_doc_version x
                        WHERE x.doc_id = d.id AND x.is_active = 1 AND x.id <> IFNULL(d.latest_version_id, 0)) old_cnt
                FROM train_doc d
                JOIN train_doc_version v ON v.id = d.latest_version_id AND v.is_active = 1
                WHERE d.is_active = 1 AND d.category = ? AND d.status = 0
                  AND v.content_text IS NOT NULL AND TRIM(v.content_text) <> ''
                ORDER BY d.update_time DESC, d.id DESC
                LIMIT 1
                """, rs -> {
            if (!rs.next()) {
                return null;
            }
            DocBundle b = new DocBundle();
            b.docId = rs.getLong("doc_id");
            b.versionId = rs.getLong("latest_version_id");
            b.versionLabel = rs.getString("version_label") == null ? "最新版" : rs.getString("version_label");
            b.text = rs.getString("content_text");
            b.html = rs.getString("content_html");
            b.filePath = rs.getString("file_path");
            b.fileName = rs.getString("file_name");
            b.hasOlder = rs.getInt("old_cnt") > 0;
            return b;
        }, category);
    }

    private List<DocBundle> loadOlderDocs(Long docId, Long latestVersionId) {
        return jdbc.query("""
                SELECT id, version_label, content_text, content_html, file_path, file_name
                FROM train_doc_version
                WHERE doc_id = ? AND is_active = 1 AND id <> ?
                  AND content_text IS NOT NULL AND TRIM(content_text) <> ''
                ORDER BY version_no DESC
                """, (rs, i) -> {
            DocBundle b = new DocBundle();
            b.docId = docId;
            b.versionId = rs.getLong("id");
            b.versionLabel = rs.getString("version_label") == null ? ("旧版#" + b.versionId) : rs.getString("version_label");
            b.text = rs.getString("content_text");
            b.html = rs.getString("content_html");
            b.filePath = rs.getString("file_path");
            b.fileName = rs.getString("file_name");
            b.hasOlder = false;
            return b;
        }, docId, latestVersionId);
    }

    private boolean docExists(Long id) {
        Integer n = jdbc.queryForObject("SELECT COUNT(1) FROM train_doc WHERE id = ? AND is_active = 1", Integer.class, id);
        return n != null && n > 0;
    }

    private static String normalizeCategory(String category) {
        if (!StringUtils.hasText(category)) {
            return CATEGORY_CASHIER;
        }
        return category.trim().toLowerCase(Locale.ROOT);
    }

    private static String blankToNull(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String toPreviewHtml(String text) {
        String escaped = text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
        return "<pre style=\"white-space:pre-wrap;word-break:break-word;font-family:inherit;margin:0;line-height:1.7;\">"
                + escaped + "</pre>";
    }

    /** 生成含图片的预览 HTML（.doc / .docx）；失败时回退纯文本。 */
    private String buildRichPreviewHtml(byte[] bytes, String fileName) {
        String lower = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        try {
            if (lower.endsWith(".docx") || (bytes.length >= 2 && bytes[0] == 'P' && bytes[1] == 'K')) {
                return wrapPreviewHtml(docxToHtmlWithImages(bytes));
            }
            if (lower.endsWith(".doc")) {
                return wrapPreviewHtml(docToHtmlWithImages(bytes));
            }
            String text = extractText(bytes, lower.contains(".") ? lower.substring(lower.lastIndexOf('.')) : ".txt");
            return toPreviewHtml(text);
        } catch (Exception e) {
            log.warn("富文本预览转换失败 {}: {}", fileName, e.getMessage());
            try {
                String text = extractText(bytes, lower.endsWith(".docx") ? ".docx" : lower.endsWith(".doc") ? ".doc" : ".txt");
                return toPreviewHtml(text);
            } catch (Exception ignored) {
                return toPreviewHtml("");
            }
        }
    }

    private static String wrapPreviewHtml(String body) {
        return """
                <div class="train-doc-preview" style="line-height:1.75;color:#262626;font-size:14px;">
                <style>
                .train-doc-preview img{max-width:100%;height:auto;display:block;margin:12px 0;border-radius:4px;}
                .train-doc-preview p{margin:0 0 10px;}
                .train-doc-preview table{border-collapse:collapse;width:100%;margin:12px 0;}
                .train-doc-preview td,.train-doc-preview th{border:1px solid #d9d9d9;padding:6px 8px;vertical-align:top;}
                </style>
                """ + body + "</div>";
    }

    private static String docToHtmlWithImages(byte[] bytes) throws Exception {
        if (bytes.length >= 2 && bytes[0] == 'P' && bytes[1] == 'K') {
            return docxToHtmlWithImages(bytes);
        }
        try (HWPFDocument wordDocument = new HWPFDocument(new ByteArrayInputStream(bytes))) {
            Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
            WordToHtmlConverter converter = new WordToHtmlConverter(doc);
            converter.setPicturesManager(new PicturesManager() {
                @Override
                public String savePicture(byte[] content, PictureType pictureType, String suggestedName,
                                          float widthInches, float heightInches) {
                    String mime = pictureType == null ? "image/png" : pictureType.getMime();
                    if (!StringUtils.hasText(mime)) {
                        mime = "image/png";
                    }
                    return "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(content);
                }
            });
            converter.processDocument(wordDocument);
            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty(OutputKeys.METHOD, "html");
            StringWriter writer = new StringWriter();
            transformer.transform(new DOMSource(converter.getDocument()), new StreamResult(writer));
            String full = writer.toString();
            int bodyStart = full.toLowerCase(Locale.ROOT).indexOf("<body");
            int bodyOpenEnd = full.indexOf('>', bodyStart);
            int bodyEnd = full.toLowerCase(Locale.ROOT).lastIndexOf("</body>");
            if (bodyStart >= 0 && bodyOpenEnd > bodyStart && bodyEnd > bodyOpenEnd) {
                return full.substring(bodyOpenEnd + 1, bodyEnd);
            }
            return full;
        }
    }

    private static String docxToHtmlWithImages(byte[] bytes) throws Exception {
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            StringBuilder html = new StringBuilder();
            for (IBodyElement element : doc.getBodyElements()) {
                if (element instanceof XWPFParagraph paragraph) {
                    html.append("<p>");
                    boolean hasContent = false;
                    for (XWPFRun run : paragraph.getRuns()) {
                        String text = run.text();
                        if (StringUtils.hasText(text)) {
                            html.append(escapeHtml(text).replace("\n", "<br/>"));
                            hasContent = true;
                        }
                        for (XWPFPicture picture : run.getEmbeddedPictures()) {
                            XWPFPictureData data = picture.getPictureData();
                            if (data == null || data.getData() == null) {
                                continue;
                            }
                            String mime = data.getPackagePart() == null
                                    ? "image/png"
                                    : data.getPackagePart().getContentType();
                            html.append("<img src=\"data:").append(mime).append(";base64,")
                                    .append(Base64.getEncoder().encodeToString(data.getData()))
                                    .append("\" alt=\"\" />");
                            hasContent = true;
                        }
                    }
                    if (!hasContent) {
                        html.append("&nbsp;");
                    }
                    html.append("</p>");
                } else if (element instanceof XWPFTable table) {
                    html.append("<table>");
                    for (XWPFTableRow row : table.getRows()) {
                        html.append("<tr>");
                        for (XWPFTableCell cell : row.getTableCells()) {
                            html.append("<td>");
                            for (XWPFParagraph paragraph : cell.getParagraphs()) {
                                html.append(escapeHtml(paragraph.getText()));
                                for (XWPFRun run : paragraph.getRuns()) {
                                    for (XWPFPicture picture : run.getEmbeddedPictures()) {
                                        XWPFPictureData data = picture.getPictureData();
                                        if (data == null || data.getData() == null) {
                                            continue;
                                        }
                                        String mime = data.getPackagePart() == null
                                                ? "image/png"
                                                : data.getPackagePart().getContentType();
                                        html.append("<img src=\"data:").append(mime).append(";base64,")
                                                .append(Base64.getEncoder().encodeToString(data.getData()))
                                                .append("\" alt=\"\" />");
                                    }
                                }
                            }
                            html.append("</td>");
                        }
                        html.append("</tr>");
                    }
                    html.append("</table>");
                }
            }
            // 文档内未挂到段落的图片补在末尾，避免漏图
            List<XWPFPictureData> all = doc.getAllPictures();
            if (all != null && !all.isEmpty() && !html.toString().contains("<img ")) {
                for (XWPFPictureData data : all) {
                    if (data.getData() == null) {
                        continue;
                    }
                    String mime = data.getPackagePart() == null ? "image/png" : data.getPackagePart().getContentType();
                    html.append("<p><img src=\"data:").append(mime).append(";base64,")
                            .append(Base64.getEncoder().encodeToString(data.getData()))
                            .append("\" alt=\"\" /></p>");
                }
            }
            return html.toString();
        }
    }

    private static String escapeHtml(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private String extractText(byte[] bytes, String ext) throws Exception {
        return switch (ext) {
            case ".docx" -> extractDocx(bytes);
            case ".doc" -> extractDocSmart(bytes);
            case ".pdf" -> extractPdf(bytes);
            case ".txt", ".md" -> decodeText(bytes);
            default -> throw new BusinessException("不支持的文件类型");
        };
    }

    private static String extractDocSmart(byte[] bytes) throws Exception {
        if (bytes.length >= 2 && bytes[0] == 'P' && bytes[1] == 'K') {
            return extractDocx(bytes);
        }
        try (HWPFDocument doc = new HWPFDocument(new ByteArrayInputStream(bytes));
             WordExtractor extractor = new WordExtractor(doc)) {
            return nz(extractor.getText());
        }
    }

    private static String extractDocx(byte[] bytes) throws Exception {
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(bytes));
             XWPFWordExtractor extractor = new XWPFWordExtractor(doc)) {
            return nz(extractor.getText());
        }
    }

    private static String extractPdf(byte[] bytes) throws Exception {
        try (PDDocument doc = Loader.loadPDF(bytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            return nz(stripper.getText(doc));
        }
    }

    private static String decodeText(byte[] bytes) {
        String utf8 = new String(bytes, StandardCharsets.UTF_8);
        if (utf8.contains("\uFFFD")) {
            return new String(bytes, Charset.forName("GBK"));
        }
        return utf8;
    }

    private static String nz(String s) {
        return s == null ? "" : s.trim();
    }

    private static class DocBundle {
        long docId;
        long versionId;
        String versionLabel;
        String text;
        String html;
        String filePath;
        String fileName;
        boolean hasOlder;
    }

    private static class IndexedDoc {
        String textForAi = "";
        List<String> images = new ArrayList<>();
        List<String> contexts = new ArrayList<>();
    }
}
