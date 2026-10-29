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
import com.base.admin.util.TrainQaAnswerSanitizer;
import com.base.admin.util.TrainQaIntentMatcher;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
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
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

@Slf4j
@Service
public class TrainDocService {

    public static final String CATEGORY_CASHIER = "cashier";

    private final JdbcTemplate jdbc;
    private final FileStorageService fileStorage;
    private final AiChatService aiChatService;
    private final ObjectMapper objectMapper;
    private final DingTalkAppService dingTalkAppService;
    private final DingTalkProperties dingTalkProperties;
    private final TrainQaCalibrateService trainQaCalibrateService;
    private final TrainQaMissService trainQaMissService;
    private final TrainQaItemService trainQaItemService;
    private final TrainQaSearchService trainQaSearchService;

    @Value("${xby.upload-public-base:}")
    private String uploadPublicBase;

    public TrainDocService(JdbcTemplate jdbc,
                           FileStorageService fileStorage,
                           AiChatService aiChatService,
                           ObjectMapper objectMapper,
                           DingTalkAppService dingTalkAppService,
                           DingTalkProperties dingTalkProperties,
                           @Lazy TrainQaCalibrateService trainQaCalibrateService,
                           TrainQaMissService trainQaMissService,
                           @Lazy TrainQaItemService trainQaItemService,
                           @Lazy TrainQaSearchService trainQaSearchService) {
        this.jdbc = jdbc;
        this.fileStorage = fileStorage;
        this.aiChatService = aiChatService;
        this.objectMapper = objectMapper;
        this.dingTalkAppService = dingTalkAppService;
        this.dingTalkProperties = dingTalkProperties;
        this.trainQaCalibrateService = trainQaCalibrateService;
        this.trainQaMissService = trainQaMissService;
        this.trainQaItemService = trainQaItemService;
        this.trainQaSearchService = trainQaSearchService;
    }

    public List<TrainDocVO> list(String category) {
        String cat = normalizeCategory(category);
        return jdbc.query("""
                SELECT d.id, d.title, d.category, d.description, d.latest_version_id, d.status, d.update_time,
                       v.version_no, v.version_label, v.file_name, v.file_path,
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
            String filePath = rs.getString("file_path");
            vo.setLatestFilePath(filePath);
            vo.setLatestFileUrl(StringUtils.hasText(filePath) ? fileStorage.toPublicUrl(filePath) : null);
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
        trainQaItemService.deactivateDoc(id);
    }

    public Long latestVersionId(Long docId) {
        if (docId == null || !docExists(docId)) {
            throw new BusinessException("文档不存在");
        }
        return jdbc.query("""
                SELECT latest_version_id FROM train_doc WHERE id = ? AND is_active = 1
                """, rs -> rs.next() && rs.getObject(1) != null ? rs.getLong(1) : null, docId);
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
                SELECT id, doc_id, version_no, version_label, file_name, file_path, file_size, remark, create_by, create_time
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
            String filePath = rs.getString("file_path");
            vo.setFilePath(filePath);
            vo.setFileUrl(StringUtils.hasText(filePath) ? fileStorage.toPublicUrl(filePath) : null);
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
        // 预览走原文件；库内只存轻量文本 HTML，避免几十张图 base64 撑爆上传/预览
        String html = toPreviewHtml(text);
        jdbc.update("""
                INSERT INTO train_doc_version (doc_id, version_no, version_label, file_name, file_path,
                                              content_text, content_html, file_size, remark, create_by, is_active)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1)
                """, docId, nextNo, label, original, relative, text, html, (long) bytes.length,
                blankToNull(remark), SecurityUtils.getCurrentUsername());
        Long versionId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        jdbc.update("UPDATE train_doc SET latest_version_id = ? WHERE id = ?", versionId, docId);
        if (versionId != null) {
            trainQaCalibrateService.markStaleForDocExceptVersion(docId, versionId);
            // 上传当下就在本机抽配图，避免首问才抽、或本机抽完公网仍 404
            try {
                DocBundle freshly = loadDocVersion(docId, versionId);
                if (freshly != null) {
                    prepareIndexedDoc(freshly);
                }
            } catch (Exception e) {
                log.warn("上传后预抽答疑配图失败 versionId={}: {}", versionId, e.getMessage());
            }
            Long fileId = insertDocFile(docId, versionId, original, relative, bytes.length, original);
            try {
                if (fileId != null) {
                    trainQaItemService.rebuildFile(docId, versionId, fileId);
                }
            } catch (Exception e) {
                log.warn("上传后拆题入库失败，原文件已保存 versionId={}: {}", versionId, e.getMessage());
            }
        }
        return versionId;
    }

    /** 往已有版本再挂一份收银文档，不升版本号。单份仍不超过上传大小限制。 */
    @Transactional
    public Long addFileToVersion(Long docId, Long versionId, MultipartFile file, String title) {
        if (!versionBelongsToDoc(docId, versionId)) {
            throw new BusinessException("版本不存在");
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
            throw new BusinessException("无法解析文档内容，请确认是可用的 doc/docx/pdf/txt");
        }
        if (!StringUtils.hasText(text)) {
            throw new BusinessException("文档内容为空，无法拆题");
        }
        Long fileId = insertDocFile(docId, versionId, original, relative, bytes.length,
                StringUtils.hasText(title) ? title.trim() : original);
        if (fileId != null) {
            trainQaItemService.rebuildFile(docId, versionId, fileId);
        }
        return fileId;
    }

    public List<com.base.admin.domain.vo.TrainDocFileVO> listFiles(Long versionId) {
        return jdbc.query("""
                SELECT f.id, f.doc_id, f.version_id, v.version_label, f.title, f.file_name, f.file_size,
                       (SELECT COUNT(1) FROM train_qa_item i
                         WHERE i.is_active = 1 AND (
                           i.file_id = f.id
                           OR (f.sort_no = 1 AND i.file_id IS NULL AND i.version_id = f.version_id)
                         )) question_count
                FROM train_doc_file f
                LEFT JOIN train_doc_version v ON v.id = f.version_id
                WHERE f.version_id = ? AND f.is_active = 1
                ORDER BY f.sort_no ASC, f.id ASC
                """, (rs, n) -> {
            com.base.admin.domain.vo.TrainDocFileVO vo = new com.base.admin.domain.vo.TrainDocFileVO();
            vo.setId(rs.getLong("id"));
            vo.setDocId(rs.getLong("doc_id"));
            vo.setVersionId(rs.getLong("version_id"));
            vo.setVersionLabel(rs.getString("version_label"));
            vo.setTitle(rs.getString("title"));
            vo.setFileName(rs.getString("file_name"));
            vo.setFileSize(rs.getObject("file_size") == null ? null : rs.getLong("file_size"));
            vo.setQuestionCount(rs.getInt("question_count"));
            return vo;
        }, versionId);
    }

    /**
     * 删除版本下某一份收银文档：软删文档行，并级联软删其题目与对应答疑校准。
     */
    @Transactional
    public void deleteFileFromVersion(Long docId, Long versionId, Long fileId) {
        if (docId == null || versionId == null || fileId == null) {
            throw new BusinessException("参数不完整");
        }
        if (!versionBelongsToDoc(docId, versionId)) {
            throw new BusinessException("版本不存在");
        }
        List<Map<String, Object>> files = jdbc.queryForList("""
                SELECT id, file_path, sort_no FROM train_doc_file
                WHERE id = ? AND doc_id = ? AND version_id = ? AND is_active = 1
                """, fileId, docId, versionId);
        if (files.isEmpty()) {
            throw new BusinessException("收银文档不存在或已删除");
        }
        Map<String, Object> file = files.get(0);
        String filePath = file.get("file_path") == null ? "" : String.valueOf(file.get("file_path"));
        int sortNo = file.get("sort_no") instanceof Number n ? n.intValue() : 0;
        String versionPath = jdbc.query("""
                SELECT file_path FROM train_doc_version WHERE id = ? AND is_active = 1
                """, rs -> rs.next() ? rs.getString(1) : "", versionId);
        boolean primary = sortNo == 1
                || (StringUtils.hasText(versionPath) && versionPath.equals(filePath));

        List<String> questions = trainQaItemService.deactivateFile(docId, versionId, fileId, primary);
        trainQaCalibrateService.deactivateMatchingQuestions(docId, versionId, questions);

        int n = jdbc.update("""
                UPDATE train_doc_file SET is_active = 0
                WHERE id = ? AND doc_id = ? AND version_id = ? AND is_active = 1
                """, fileId, docId, versionId);
        if (n <= 0) {
            throw new BusinessException("收银文档不存在或已删除");
        }
        log.info("已删除收银文档 fileId={} docId={} versionId={} primary={} questions={}",
                fileId, docId, versionId, primary, questions.size());
    }

    private boolean versionBelongsToDoc(Long docId, Long versionId) {
        if (docId == null || versionId == null) {
            return false;
        }
        Integer n = jdbc.queryForObject("""
                SELECT COUNT(1) FROM train_doc_version
                WHERE id = ? AND doc_id = ? AND is_active = 1
                """, Integer.class, versionId, docId);
        return n != null && n > 0;
    }

    private Long insertDocFile(long docId, long versionId, String fileName, String relative, long size, String title) {
        Integer max = jdbc.queryForObject("""
                SELECT COALESCE(MAX(sort_no), 0) FROM train_doc_file WHERE version_id = ? AND is_active = 1
                """, Integer.class, versionId);
        int sort = (max == null ? 0 : max) + 1;
        String name = StringUtils.hasText(title) ? title.trim() : fileName;
        if (name.length() > 128) {
            name = name.substring(0, 128);
        }
        jdbc.update("""
                INSERT INTO train_doc_file (doc_id, version_id, title, file_name, file_path, file_size, sort_no, create_by, is_active)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 1)
                """, docId, versionId, name, fileName, relative, size, sort, SecurityUtils.getCurrentUsername());
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    /** 按现有一问一答切块导出题目。不删除原文件。拆不出题目时返回空列表。 */
    public List<TrainQaDraft> exportStructuredQa(long docId, long versionId) {
        DocBundle doc = loadDocVersion(docId, versionId);
        if (doc == null) {
            return List.of();
        }
        doc.forceReindex = true;
        return draftsFrom(doc);
    }

    /**
     * 拆指定收银文档。和版本主文件不是同一份时，配图单独落目录，避免盖掉主文件索引。
     */
    public List<TrainQaDraft> exportStructuredQaFile(long docId, long versionId, long fileId,
                                                     String filePath, String fileName) {
        DocBundle doc = loadDocVersion(docId, versionId);
        if (doc == null) {
            return List.of();
        }
        boolean primary = StringUtils.hasText(doc.filePath) && doc.filePath.equals(filePath);
        doc.forceReindex = true;
        if (!primary) {
            doc.filePath = filePath;
            doc.fileName = fileName;
            doc.text = "";
            doc.html = "";
            doc.indexSlot = versionId * 1_000_000L + fileId;
            byte[] bytes = readDocBytes(doc);
            if (bytes != null && bytes.length > 0) {
                String lower = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
                String ext = lower.contains(".") ? lower.substring(lower.lastIndexOf('.')) : "";
                try {
                    doc.text = extractText(bytes, ext);
                } catch (Exception e) {
                    log.warn("收银文档抽文本失败 fileId={}: {}", fileId, e.getMessage());
                }
            }
        }
        List<TrainQaDraft> drafts = draftsFrom(doc);
        for (TrainQaDraft draft : drafts) {
            draft.setFileId(fileId);
        }
        return drafts;
    }

    private List<TrainQaDraft> draftsFrom(DocBundle doc) {
        IndexedDoc indexed = prepareIndexedDoc(doc);
        long slot = doc.indexSlot > 0 ? doc.indexSlot : doc.versionId;
        ensureServedImages(indexed, slot);
        List<QaBlock> blocks = buildQaBlocks(indexed, doc.text);
        List<TrainQaDraft> out = new ArrayList<>();
        int sort = 1;
        for (QaBlock block : blocks) {
            TrainAssistantAskVO.QaItem item = materializeFromBlock(block, indexed);
            if (item == null || !StringUtils.hasText(item.getQuestion())) {
                continue;
            }
            TrainQaDraft draft = new TrainQaDraft();
            draft.setSortNo(sort++);
            draft.setDocId(doc.docId);
            draft.setVersionId(doc.versionId);
            draft.setVersionLabel(doc.versionLabel);
            String question = item.getQuestion().trim();
            draft.setQuestion(question.length() <= 500 ? question : question.substring(0, 500));
            draft.setAnswer(item.getAnswer());
            draft.setAnswerHtml(item.getAnswerHtml());
            draft.setImages(item.getImages() == null ? List.of() : item.getImages());
            out.add(draft);
        }
        return out;
    }

    public void applyPublicImages(TrainAssistantAskVO vo) {
        if (vo != null) {
            vo.setItems(withPublicImages(vo.getItems()));
        }
    }

    private static boolean confidentAsk(TrainAssistantAskVO vo) {
        if (vo == null || vo.getStatus() == null) {
            return false;
        }
        // HIT 直出；SUGGEST 给点选原题（均已过检索闸门）。NEED_OLD/NOT_FOUND 不算自信命中。
        return "HIT".equals(vo.getStatus()) || "SUGGEST".equals(vo.getStatus());
    }

    private TrainAssistantAskVO recordStructuredMiss(TrainAssistantAskVO vo, String question, String cat) {
        if (vo != null && ("NOT_FOUND".equals(vo.getStatus()) || "NEED_OLD".equals(vo.getStatus()))) {
            trainQaMissService.record(question, cat, vo.getStatus(), vo.getDocId(), vo.getVersionId());
        }
        return vo;
    }

    /** 后台「重新识别」：按指定文档版本跑答疑抽段（不走校准库）。 */
    public List<TrainAssistantAskVO.QaItem> extractQaForVersion(String question, long docId, long versionId) {
        if (!aiChatService.isEnabled()) {
            throw new BusinessException("AI 未配置，请到「系统管理 → AI模型配置」填写可用模型");
        }
        DocBundle doc = loadDocVersion(docId, versionId);
        if (doc == null) {
            throw new BusinessException("文档版本不存在");
        }
        return withPublicImages(aiExtractQa(question, doc));
    }

    private DocBundle loadDocVersion(long docId, long versionId) {
        return jdbc.query("""
                SELECT d.id doc_id, v.id version_id, v.version_label, v.content_text, v.content_html,
                       v.file_path, v.file_name
                FROM train_doc_version v
                JOIN train_doc d ON d.id = v.doc_id AND d.is_active = 1
                WHERE d.id = ? AND v.id = ? AND v.is_active = 1
                """, rs -> {
            if (!rs.next()) {
                return null;
            }
            DocBundle b = new DocBundle();
            b.docId = rs.getLong("doc_id");
            b.versionId = rs.getLong("version_id");
            b.versionLabel = rs.getString("version_label") == null ? "v" : rs.getString("version_label");
            b.text = rs.getString("content_text");
            b.html = rs.getString("content_html");
            b.filePath = rs.getString("file_path");
            b.fileName = rs.getString("file_name");
            b.hasOlder = false;
            return b;
        }, docId, versionId);
    }

    /**
     * 预览元信息：Word 走 Office Online（公网 fileUrl）；PDF 直链；文本回退 HTML。
     * 不再在预览时解压/转码大 docx（答疑抽图仍走 prepareIndexedDoc）。
     */
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
        String filePath = nullToEmpty(String.valueOf(meta.get("filePath")));
        String lower = fileName.toLowerCase(Locale.ROOT);
        Path path = fileStorage.resolveUploadPath(filePath);
        boolean fileReady = path != null && Files.isRegularFile(path);
        meta.put("fileReady", fileReady);
        if (!fileReady) {
            meta.put("fileMissing", true);
            log.warn("培训文档预览原文件缺失 versionId={} path={}", versionId, filePath);
        }

        String publicUrl = fileReady ? fileStorage.toPublicUrl(filePath) : "";
        meta.put("fileUrl", nullToEmpty(publicUrl));
        // 预览不需要把长文本带回前端
        meta.put("text", "");

        if (lower.endsWith(".pdf")) {
            meta.put("renderMode", "pdf");
            meta.put("html", "");
            return meta;
        }

        boolean officeDoc = lower.endsWith(".doc") || lower.endsWith(".docx")
                || lower.endsWith(".xls") || lower.endsWith(".xlsx")
                || lower.endsWith(".ppt") || lower.endsWith(".pptx")
                || looksLikeDocxZip(filePath);
        if (officeDoc && fileReady && StringUtils.hasText(publicUrl)) {
            meta.put("renderMode", "office");
            meta.put("html", "");
            return meta;
        }

        meta.put("renderMode", "html");
        String html = String.valueOf(meta.get("html"));
        if ((!StringUtils.hasText(html) || !html.contains("<img")) && fileReady
                && (lower.endsWith(".txt") || lower.endsWith(".md"))) {
            try {
                String text = extractText(Files.readAllBytes(path), lower.endsWith(".md") ? ".md" : ".txt");
                meta.put("html", toPreviewHtml(text));
            } catch (Exception e) {
                log.warn("文本预览失败 versionId={}: {}", versionId, e.getMessage());
            }
        }
        return meta;
    }

    /** 答疑用：把带 [图N] 的正文转成 HTML（图片 /uploads URL）。 */
    private static String buildUrlPreviewHtml(IndexedDoc indexed) {
        if (indexed == null) {
            return "";
        }
        String plain = nullToEmpty(indexed.textForAi);
        if (!StringUtils.hasText(plain) && indexed.images.isEmpty()) {
            return "";
        }
        StringBuilder body = new StringBuilder();
        String[] lines = plain.split("\\R", -1);
        Pattern marker = Pattern.compile("\\[图(\\d+)\\]");
        for (String line : lines) {
            if (!StringUtils.hasText(line)) {
                body.append("<p>&nbsp;</p>");
                continue;
            }
            body.append("<p>");
            Matcher m = marker.matcher(line);
            int last = 0;
            while (m.find()) {
                body.append(escapeHtml(line.substring(last, m.start())));
                int n = Integer.parseInt(m.group(1));
                if (n >= 1 && n <= indexed.images.size()) {
                    body.append("<img src=\"")
                            .append(indexed.images.get(n - 1))
                            .append("\" alt=\"培训配图")
                            .append(n)
                            .append("\" loading=\"lazy\" style=\"max-width:100%;height:auto;display:block;margin:10px 0;border-radius:6px;\" />");
                }
                last = m.end();
            }
            body.append(escapeHtml(line.substring(last)));
            body.append("</p>");
        }
        return wrapPreviewHtml(body.toString());
    }

    private boolean looksLikeDocxZip(String filePath) {
        try {
            Path path = fileStorage.resolveUploadPath(filePath);
            if (path == null || !Files.isRegularFile(path)) {
                return false;
            }
            try (var in = Files.newInputStream(path)) {
                byte[] head = in.readNBytes(4);
                return head.length >= 2 && head[0] == 'P' && head[1] == 'K';
            }
        } catch (Exception e) {
            return false;
        }
    }

    public Path resolveVersionFile(Long versionId) {
        return findVersionFile(versionId)
                .orElseThrow(() -> new BusinessException("文件不存在或已丢失"));
    }

    /** 本机磁盘上的文件；没有则 empty（可能附件只在线上）。 */
    public java.util.Optional<Path> findVersionFile(Long versionId) {
        String path = versionFilePath(versionId);
        Path file = fileStorage.resolveUploadPath(path);
        return java.util.Optional.ofNullable(file);
    }

    /** 相对路径 /uploads/...；版本不存在则抛错。 */
    public String versionFilePath(Long versionId) {
        String path = jdbc.query("""
                SELECT file_path FROM train_doc_version WHERE id = ? AND is_active = 1
                """, rs -> rs.next() ? rs.getString(1) : null, versionId);
        if (path == null) {
            throw new BusinessException("版本不存在");
        }
        return path;
    }

    /** 公网标准下载地址（本机/线上统一）。 */
    public String versionPublicUrl(Long versionId) {
        return fileStorage.toPublicUrl(versionFilePath(versionId));
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
        String cat = normalizeCategory(dto.getCategory());
        DocBundle latest = loadLatestDoc(cat);
        if (latest == null) {
            TrainAssistantAskVO empty = new TrainAssistantAskVO();
            empty.setStatus("NOT_FOUND");
            empty.setMessage("暂无培训文档，请先在后台上传收银操作培训资料");
            empty.setHasOlderVersions(false);
            trainQaMissService.record(question, cat, "NOT_FOUND", null, null);
            return empty;
        }
        boolean searchOld = Boolean.TRUE.equals(dto.getSearchOld());
        if (!searchOld) {
            /*
             * 答疑优先级：
             * 1) 校准有准确答案且绑定最新文档版 → 直接返回校准答案
             * 2) 校准有准确答案但绑定旧版 → 先搜最新文档同意图；有则返回文档答案；
             *    无则返回校准答案并备注「参考 XXX 版本」
             * 3) 校准无准确答案 → 搜最新文档；有则返回；无则找不到答案
             */
            TrainQaCalibrateService.AccurateHit calibrated =
                    trainQaCalibrateService.findAccurateHit(question, latest.docId, latest.versionId);

            // 一：最新版校准直接返回（不必再搜文档）
            if (calibrated != null && calibrated.onLatest()) {
                TrainAssistantAskVO vo = new TrainAssistantAskVO();
                vo.setStatus("HIT");
                vo.setMessage("已命中人工校准答案");
                vo.setItems(withPublicImages(List.of(calibrated.item())));
                vo.setVersionLabel(latest.versionLabel);
                vo.setHasOlderVersions(latest.hasOlder);
                vo.setDocId(latest.docId);
                vo.setVersionId(latest.versionId);
                return vo;
            }

            List<TrainAssistantAskVO.QaItem> latestHits;
            Optional<TrainAssistantAskVO> vectorHit = trainQaSearchService.trySearch(question, cat, false);
            if (vectorHit.isPresent() && (confidentAsk(vectorHit.get()) || !trainQaSearchService.hasUnindexedLatest(cat))) {
                TrainAssistantAskVO vectorVo = vectorHit.get();
                applyPublicImages(vectorVo);
                if (confidentAsk(vectorVo)) {
                    return vectorVo;
                }
                if (calibrated == null) {
                    return recordStructuredMiss(vectorVo, question, cat);
                }
                latestHits = List.of();
            } else {
                latestHits = aiExtractQa(question, latest);
            }

            // 二：旧版校准存在
            if (calibrated != null) {
                if (!latestHits.isEmpty()) {
                    TrainAssistantAskVO vo = new TrainAssistantAskVO();
                    vo.setStatus("HIT");
                    vo.setMessage("已根据最新版培训文档找到相关问答");
                    vo.setItems(withPublicImages(latestHits));
                    vo.setVersionLabel(latest.versionLabel);
                    vo.setHasOlderVersions(latest.hasOlder);
                    vo.setDocId(latest.docId);
                    vo.setVersionId(latest.versionId);
                    return vo;
                }
                TrainAssistantAskVO.QaItem item = calibrated.item();
                String refLabel = StringUtils.hasText(calibrated.versionLabel())
                        ? calibrated.versionLabel()
                        : ("版本#" + calibrated.versionId());
                item.setReferenceNote("该答案是参考「" + refLabel + "」版本的培训文档");
                TrainAssistantAskVO vo = new TrainAssistantAskVO();
                vo.setStatus("HIT");
                vo.setMessage("已命中校准答案（参考历史版本）");
                vo.setItems(withPublicImages(List.of(item)));
                vo.setVersionLabel(latest.versionLabel);
                vo.setHasOlderVersions(latest.hasOlder);
                vo.setDocId(latest.docId);
                vo.setVersionId(latest.versionId);
                return vo;
            }

            // 三：无校准准确答案 → 仅依赖最新文档
            if (!latestHits.isEmpty()) {
                TrainAssistantAskVO vo = new TrainAssistantAskVO();
                vo.setStatus("HIT");
                vo.setMessage("已根据最新版培训文档找到相关问答");
                vo.setItems(withPublicImages(latestHits));
                vo.setVersionLabel(latest.versionLabel);
                vo.setHasOlderVersions(latest.hasOlder);
                vo.setDocId(latest.docId);
                vo.setVersionId(latest.versionId);
                return vo;
            }

            TrainAssistantAskVO vo = new TrainAssistantAskVO();
            vo.setHasOlderVersions(latest.hasOlder);
            vo.setVersionLabel(latest.versionLabel);
            vo.setDocId(latest.docId);
            vo.setVersionId(latest.versionId);
            if (latest.hasOlder) {
                vo.setStatus("NEED_OLD");
                vo.setMessage("暂时没有找到答案，是否检索旧版？");
            } else {
                vo.setStatus("NOT_FOUND");
                vo.setMessage("没有找到与该问题匹配的培训说明，请换个问法后再试。");
            }
            trainQaMissService.record(question, cat, vo.getStatus(), latest.docId, latest.versionId);
            return vo;
        }

        List<DocBundle> older = loadOlderDocs(latest.docId, latest.versionId);
        if (older.isEmpty()) {
            TrainAssistantAskVO vo = new TrainAssistantAskVO();
            vo.setStatus("NOT_FOUND");
            vo.setMessage("没有找到与该问题匹配的培训说明，请换个问法后再试。");
            vo.setHasOlderVersions(false);
            vo.setVersionLabel(latest.versionLabel);
            trainQaMissService.record(question, cat, "NOT_FOUND", latest.docId, latest.versionId);
            return vo;
        }
        Optional<TrainAssistantAskVO> oldVector = trainQaSearchService.trySearch(question, cat, true);
        if (oldVector.isPresent() && confidentAsk(oldVector.get())) {
            TrainAssistantAskVO vectorVo = oldVector.get();
            applyPublicImages(vectorVo);
            return vectorVo;
        }
        List<TrainAssistantAskVO.QaItem> all = new ArrayList<>();
        for (DocBundle old : older) {
            all.addAll(aiExtractQa(question, old));
        }
        TrainAssistantAskVO vo = new TrainAssistantAskVO();
        vo.setHasOlderVersions(false);
        if (all.isEmpty()) {
            vo.setStatus("NOT_FOUND");
            vo.setMessage("没有找到与该问题匹配的培训说明，请换个问法后再试。");
            trainQaMissService.record(question, cat, "NOT_FOUND", latest.docId, latest.versionId);
        } else {
            vo.setStatus("HIT");
            vo.setMessage("已在旧版培训文档中找到相关问答");
            vo.setItems(withPublicImages(all));
            vo.setVersionLabel(all.get(0).getVersionLabel());
        }
        return vo;
    }

    /**
     * 答疑三步（保持简单）：
     * 1) 文档已是一问一答 → 按「问 → 下一问」切块（块内图文一体）
     * 2) AI 把用户意图匹配到真实题干序号
     * 3) 命中后原样返回该答的全部内容（清洗后的纯文 + 配图，不下发 Word/HTML 源码）
     */
    private List<TrainAssistantAskVO.QaItem> aiExtractQa(String question, DocBundle doc) {
        IndexedDoc indexed = prepareIndexedDoc(doc);
        List<QaBlock> blocks = buildQaBlocks(indexed, doc == null ? null : doc.text);
        log.info("答疑一问一答切块 versionId={} blocks={} aiEnabled={}",
                doc.versionId, blocks.size(), aiChatService.isEnabled());
        if (blocks.isEmpty()) {
            log.warn("答疑切块为空 versionId={}", doc.versionId);
            return List.of();
        }

        int pick = -1;
        if (aiChatService.isEnabled()) {
            pick = pickBlockIndexWithAi(question, blocks);
            log.info("答疑AI选题 question={} index={} title={}",
                    question, pick,
                    pick > 0 && pick <= blocks.size() ? blocks.get(pick - 1).question : "-");
        }
        if (pick <= 0) {
            pick = pickBlockIndexByRules(question, blocks);
            log.info("答疑规则选题 question={} index={}", question, pick);
        }
        if (pick <= 0 || pick > blocks.size()) {
            return List.of();
        }

        TrainAssistantAskVO.QaItem item = materializeFromBlock(blocks.get(pick - 1), indexed);
        if (item == null) {
            return List.of();
        }
        item.setVersionLabel(doc.versionLabel);
        return List.of(item);
    }

    /**
     * 一问一答切块：只认一条「结构正文 + [图N]」源，按问→下一问切开。
     * 禁止 HTML 切块与 content_text 按序号硬对齐（题干对了、图却在另一块）。
     */
    private List<QaBlock> buildQaBlocks(IndexedDoc indexed, String contentText) {
        String plain = qaCutPlain(indexed, contentText);
        List<QaBlock> blocks = cutQaBlocks(plain);
        if (!blocks.isEmpty()) {
            return blocks;
        }
        // 极端兜底：无图时仍按 content_text 切题干
        if (StringUtils.hasText(contentText) && !contentText.equals(plain)) {
            return cutQaBlocks(contentText);
        }
        return blocks;
    }

    /**
     * 切块正文优先级：
     * 1) textForAi（preferStructured 已把 [图N] 合进 content_text）
     * 2) markedText（纯 HTML 抽字带图）
     * 3) content_text
     */
    private static String qaCutPlain(IndexedDoc indexed, String contentText) {
        if (indexed != null) {
            String text = TrainQaAnswerSanitizer.stripOfficeMarkup(nullToEmpty(indexed.textForAi));
            if (StringUtils.hasText(text) && text.contains("[图")) {
                return text;
            }
            String marked = TrainQaAnswerSanitizer.stripOfficeMarkup(nullToEmpty(indexed.markedText));
            if (StringUtils.hasText(marked) && marked.contains("[图")) {
                return marked;
            }
            if (StringUtils.hasText(text)) {
                return text;
            }
            if (StringUtils.hasText(marked)) {
                return marked;
            }
        }
        return nullToEmpty(contentText);
    }

    private List<QaBlock> cutQaBlocks(String plain) {
        List<QaBlock> list = new ArrayList<>();
        if (!StringUtils.hasText(plain)) {
            return list;
        }
        List<Integer> starts = listQaStarts(plain);
        if (starts.isEmpty()) {
            starts = listSectionStarts(plain);
        }
        for (int i = 0; i < starts.size(); i++) {
            int from = starts.get(i);
            int to = i + 1 < starts.size() ? starts.get(i + 1) : plain.length();
            String sec = plain.substring(from, to);
            String bodyProbe = stripImageMarkers(trimSectionForDisplay(sec)).trim();
            // 仅当本问已有答文时，才把「下一问题干→答：前」的图收回；
            // 否则空答文 + 偷图 = 题干对了却只返回下一问配图（退单 case）
            if (to < plain.length() && StringUtils.hasText(bodyProbe)) {
                sec = sec + stealHeadingImages(plain.substring(to));
            }
            sec = sec.trim();
            String title = cleanTopicTitle(sectionTitle(sec));
            if (compact(title).length() < 2) {
                continue;
            }
            String mixed = trimSectionForDisplay(sec);
            if (!StringUtils.hasText(stripImageMarkers(mixed)) && countMarkers(mixed) == 0) {
                continue;
            }
            // 仍无答文却只有图：丢掉偷来的图，避免错图充数
            if (!StringUtils.hasText(stripImageMarkers(mixed)) && countMarkers(mixed) > 0) {
                log.warn("答疑切块仅有配图无答文，丢弃配图以免串到下一问 title={}", title);
                mixed = "";
                continue;
            }
            QaBlock b = new QaBlock();
            b.question = title;
            b.mixed = mixed;
            b.fullSection = sec;
            b.from = from;
            b.to = to;
            list.add(b);
        }
        return list;
    }

    /** 文档 AI 选题：全量规则预筛 → Top 小池给模型 → 硬闸门校验。 */
    private static final int AI_BLOCK_RERANK_POOL = 30;

    private int pickBlockIndexWithAi(String question, List<QaBlock> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            return -1;
        }
        List<int[]> scored = scoreAllBlocks(question, blocks);
        if (scored.isEmpty()) {
            return -1;
        }
        int poolSize = Math.min(AI_BLOCK_RERANK_POOL, scored.size());
        List<int[]> poolMeta = scored.subList(0, poolSize);
        StringBuilder catalog = new StringBuilder();
        for (int i = 0; i < poolMeta.size(); i++) {
            int blockIdx = poolMeta.get(i)[0];
            catalog.append(i + 1).append(". ").append(blocks.get(blockIdx).question).append('\n');
        }
        String system = """
                你是收银操作培训手册的答疑选段助手。
                任务：根据用户问题，从「一问一答」题干列表中选出最贴近的一条。
                要求：
                1. 按语义理解，不要只做字面相等。近义词、同一操作的不同说法都算匹配
                   （例如：订货≈要货、厂子≈厂家、怎么找≈如何找、要货单操作≈找厂子要货）。
                2. 业务域不同不要选（寄存/暂存 ≠ 销售流水；订货 ≠ 退货）。
                3. 只能从给定候选中选，禁止编造标题。
                4. 只输出 JSON，不要 markdown、不要解释。格式：{"index":1}
                   index 从 1 开始；若明显都不相关则 {"index":0}。
                """;
        String user = "用户问题：\n" + question + "\n\n候选问法：\n" + catalog;
        try {
            String raw = aiChatService.chat(system, user, null, 0.1);
            Integer idx = parseCandidateIndex(raw, poolMeta.size());
            log.info("答疑AI选题原始返回 poolIndex={} raw={}", idx, raw == null ? "" : raw.replace('\n', ' '));
            if (idx != null && idx > 0 && idx <= poolMeta.size()) {
                int blockIdx = poolMeta.get(idx - 1)[0];
                String title = blocks.get(blockIdx).question;
                if (TrainQaIntentMatcher.passesHardGate(question, title)) {
                    return blockIdx + 1;
                }
                log.info("答疑AI选题被硬闸门拦截 title={}", title);
            }
            // AI 未选或闸门失败：在预筛池里找第一条过闸门且规则分够的
            for (int[] row : poolMeta) {
                String title = blocks.get(row[0]).question;
                if (row[1] >= MIN_SECTION_SCORE && TrainQaIntentMatcher.passesHardGate(question, title)) {
                    return row[0] + 1;
                }
            }
            return -1;
        } catch (Exception e) {
            log.warn("答疑AI选题失败，将回退规则: {}", e.getMessage());
            return -1;
        }
    }

    /** 全量块打分，按分降序；元素为 [blockIndex, score]。 */
    private List<int[]> scoreAllBlocks(String question, List<QaBlock> blocks) {
        List<int[]> scored = new ArrayList<>();
        for (int i = 0; i < blocks.size(); i++) {
            String title = blocks.get(i).question;
            int score = scoreUserAgainstTitle(question, title);
            if (titleMatchesTopic(question, title)) {
                score += 200;
            }
            String left = TrainQaIntentMatcher.leftover(question);
            if (left.length() >= 4 && compact(title).contains(left)) {
                score += 500;
            }
            float kw = TrainQaIntentMatcher.keywordOverlap(question, title);
            score += Math.round(kw * 100);
            if (score > 0 || titleMatchesTopic(question, title)) {
                scored.add(new int[]{i, score});
            }
        }
        // 无任何词面命中时仍保留规则分最高的一批，避免空池
        if (scored.isEmpty()) {
            for (int i = 0; i < blocks.size(); i++) {
                scored.add(new int[]{i, scoreUserAgainstTitle(question, blocks.get(i).question)});
            }
        }
        scored.sort((a, b) -> Integer.compare(b[1], a[1]));
        return scored;
    }

    private int pickBlockIndexByRules(String question, List<QaBlock> blocks) {
        int best = -1;
        int bestScore = -1;
        for (int i = 0; i < blocks.size(); i++) {
            String title = blocks.get(i).question;
            if (!TrainQaIntentMatcher.passesHardGate(question, title)) {
                continue;
            }
            if (!titleMatchesTopic(question, title)) {
                continue;
            }
            int score = scoreUserAgainstTitle(question, title);
            String left = TrainQaIntentMatcher.leftover(question);
            if (left.length() >= 4 && compact(title).contains(left)) {
                score += 500;
            }
            if (score > bestScore) {
                bestScore = score;
                best = i + 1;
            }
        }
        return bestScore >= MIN_SECTION_SCORE ? best : -1;
    }

    private TrainAssistantAskVO.QaItem materializeFromBlock(QaBlock block, IndexedDoc indexed) {
        TrainAssistantAskVO.QaItem item = new TrainAssistantAskVO.QaItem();
        item.setQuestion(cleanTopicTitle(TrainQaAnswerSanitizer.stripOfficeMarkup(block.question)));
        // 本问答块全文（答文 + 块内 [图N]），只清洗 Word 泄漏，不跨问补图
        String mixed = TrainQaAnswerSanitizer.stripOfficeMarkup(nullToEmpty(block.mixed));
        if (countMarkers(mixed) == 0 && StringUtils.hasText(block.fullSection)) {
            String again = TrainQaAnswerSanitizer.stripOfficeMarkup(trimSectionForDisplay(block.fullSection));
            if (countMarkers(again) > 0) {
                mixed = again;
            }
        }
        // 答文为空时：禁止只用「下一问题干前的图」充数；优先按题干重切带图正文
        if (!StringUtils.hasText(stripImageMarkers(mixed).trim())) {
            String fromMarked = reloadSectionMixedFromMarked(block.question, indexed);
            String markedBody = stripImageMarkers(nullToEmpty(fromMarked)).trim();
            if (StringUtils.hasText(markedBody)) {
                mixed = fromMarked;
            } else if (countMarkers(mixed) > 0) {
                log.warn("答疑命中题干但答文为空，丢弃可能串题的配图 title={}", item.getQuestion());
                mixed = "";
            }
        } else if (countMarkers(mixed) == 0) {
            String fromMarked = reloadSectionMixedFromMarked(block.question, indexed);
            if (StringUtils.hasText(fromMarked) && countMarkers(fromMarked) > 0) {
                String body = stripImageMarkers(mixed).trim();
                // 重切结果若几乎无答文，只补图、不覆盖已有答文
                String reloadBody = stripImageMarkers(fromMarked).trim();
                if (StringUtils.hasText(reloadBody) && reloadBody.length() + 8 >= body.length()) {
                    mixed = fromMarked;
                } else {
                    mixed = (body + "\n" + keepImageMarkersOnly(fromMarked)).trim();
                }
            }
        }
        List<Integer> refs = extractMarkers(mixed, indexed);
        String answer = stripImageMarkers(mixed).trim();
        item.setAnswer(answer);
        item.setImages(toImageUrls(refs, indexed));
        // answerHtml 仅由纯文 + 受控 <img> 拼出，禁止把文档 HTML/OOXML 原样下发
        item.setAnswerHtml(buildMixedAnswerHtml(mixed, indexed));
        if (!StringUtils.hasText(answer) && (item.getImages() == null || item.getImages().isEmpty())) {
            log.warn("答疑切块无答文无配图，丢弃 title={}", item.getQuestion());
            return null;
        }
        if (!StringUtils.hasText(answer) && item.getImages() != null && !item.getImages().isEmpty()) {
            log.warn("答疑切块仍无答文仅有图，丢弃以免返回下一问配图 title={}", item.getQuestion());
            return null;
        }
        if (TrainQaAnswerSanitizer.looksLikeSourceCode(item.getAnswer(), item.getAnswerHtml())) {
            log.warn("答疑切块疑似源码，丢弃 title={}", item.getQuestion());
            return null;
        }
        log.info("答疑返回问答块 title={} imgs={} bodyLen={}",
                item.getQuestion(), refs.size(), answer.length());
        return item;
    }

    private static final class QaBlock {
        String question;
        /** 答案正文 + 本问 [图N] */
        String mixed;
        /** 问→下一问 原始整段（含题干），仅用于本问收图 */
        String fullSection;
        int from;
        int to;
    }

    private TitleCandidate resolveTitleCandidate(String question, List<TitleCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        List<TitleCandidate> titleAligned = new ArrayList<>();
        for (TitleCandidate c : candidates) {
            if (titleMatchesTopic(question, c.title)) {
                titleAligned.add(c);
            }
        }
        List<TitleCandidate> pool = !titleAligned.isEmpty() ? titleAligned : candidates;
        pool.sort(Comparator.comparingInt((TitleCandidate c) -> c.score).reversed());
        return pool.get(0);
    }

    private TitleCandidate pickTitleWithAi(String question, List<TitleCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        // 全量按规则分排序，只把 Top 小池交给模型（上千题也不撑爆上下文）
        List<TitleCandidate> ranked = new ArrayList<>(candidates);
        ranked.sort(Comparator.comparingInt((TitleCandidate c) -> {
            int s = scoreUserAgainstTitle(question, c.title) + c.score;
            if (titleMatchesTopic(question, c.title)) {
                s += 200;
            }
            s += Math.round(TrainQaIntentMatcher.keywordOverlap(question, c.title) * 100);
            return s;
        }).reversed());
        List<TitleCandidate> pool = ranked.size() > AI_BLOCK_RERANK_POOL
                ? new ArrayList<>(ranked.subList(0, AI_BLOCK_RERANK_POOL))
                : ranked;
        StringBuilder catalog = new StringBuilder();
        for (int i = 0; i < pool.size(); i++) {
            catalog.append(i + 1).append(". ").append(pool.get(i).title).append('\n');
        }
        String system = """
                你是收银操作培训手册的答疑选段助手。
                任务：根据用户问题，从「候选问法」列表中选出最贴近的一条。
                要求：
                1. 按语义理解，不要只做字面相等。近义词、同一操作的不同说法都算匹配
                   （例如：订货≈要货、厂子≈厂家、怎么找≈如何找、要货单操作≈找厂子要货）。
                2. 业务域不同不要选（寄存/暂存 ≠ 销售流水；订货 ≠ 退货）。
                3. 只能从给定候选中选，禁止编造标题。
                4. 只输出 JSON，不要 markdown、不要解释。格式：{"index":1}
                   index 从 1 开始；若明显都不相关则 {"index":0}。
                """;
        String user = "用户问题：\n" + question + "\n\n候选问法：\n" + catalog;
        try {
            String raw = aiChatService.chat(system, user, null, 0.1);
            Integer idx = parseCandidateIndex(raw, pool.size());
            log.info("答疑AI选段原始返回 index={} raw={}", idx, raw == null ? "" : raw.replace('\n', ' '));
            if (idx != null && idx > 0 && idx <= pool.size()) {
                TitleCandidate picked = pool.get(idx - 1);
                if (TrainQaIntentMatcher.passesHardGate(question, picked.title)) {
                    return picked;
                }
                log.info("答疑AI选段被硬闸门拦截 title={}", picked.title);
            }
            for (TitleCandidate c : pool) {
                if (TrainQaIntentMatcher.passesHardGate(question, c.title)
                        && scoreUserAgainstTitle(question, c.title) >= MIN_SECTION_SCORE) {
                    return c;
                }
            }
            return null;
        } catch (Exception e) {
            log.warn("答疑AI选段失败，将回退规则: {}", e.getMessage());
            return null;
        }
    }

    private Integer parseCandidateIndex(String raw, int size) {
        if (!StringUtils.hasText(raw) || size <= 0) {
            return null;
        }
        String text = raw.trim()
                .replaceFirst("^```json", "")
                .replaceFirst("^```", "");
        if (text.endsWith("```")) {
            text = text.substring(0, text.length() - 3);
        }
        text = text.trim();
        try {
            int start = text.indexOf('{');
            int end = text.lastIndexOf('}');
            if (start >= 0 && end > start) {
                JsonNode node = objectMapper.readTree(text.substring(start, end + 1));
                int index = node.path("index").asInt(-1);
                if (index == 0) {
                    return 0;
                }
                if (index >= 1 && index <= size) {
                    return index;
                }
            }
        } catch (Exception ignored) {
            // fallthrough
        }
        Matcher m = Pattern.compile("\"index\"\\s*:\\s*(\\d+)").matcher(text);
        if (m.find()) {
            int index = Integer.parseInt(m.group(1));
            if (index == 0) {
                return 0;
            }
            if (index >= 1 && index <= size) {
                return index;
            }
        }
        return null;
    }

    private List<TitleCandidate> recallTitleCandidates(String userQuestion, IndexedDoc indexed) {
        String plain = qaSource(indexed);
        List<TitleCandidate> list = new ArrayList<>();
        if (!StringUtils.hasText(plain) || !StringUtils.hasText(userQuestion)) {
            return list;
        }
        List<Integer> starts = listQaStarts(plain);
        if (starts.isEmpty()) {
            starts = listSectionStarts(plain);
        }
        String userNorm = compact(userQuestion);
        if (userNorm.length() < 2) {
            return list;
        }
        for (int i = 0; i < starts.size(); i++) {
            int from = starts.get(i);
            int to = i + 1 < starts.size() ? starts.get(i + 1) : plain.length();
            String sec = plain.substring(from, to);
            String title = sectionTitle(sec);
            String titleNorm = compact(title);
            if (titleNorm.length() < 2) {
                continue;
            }
            String bodyProbe = stripImageMarkers(trimSectionForDisplay(sec));
            if (bodyProbe.length() < 4 && countMarkers(sec) == 0) {
                continue;
            }
            boolean titleHit = titleMatchesTopic(userQuestion, title);
            boolean bodyHit = TrainQaIntentMatcher.bodyMatches(userQuestion, bodyProbe);
            if (!titleHit && !bodyHit) {
                continue;
            }
            if (titleHit) {
                int score = scoreUserAgainstTitle(userQuestion, title) + 400;
                // 题干几乎整句命中（如「怎么找厂子要货，要货单操作」）再加权
                String left = TrainQaIntentMatcher.leftover(userQuestion);
                String titleCompact = TrainQaIntentMatcher.compact(title);
                if (left.length() >= 4 && titleCompact.contains(left)) {
                    score += 600;
                }
                if (score >= MIN_SECTION_SCORE || titleHit) {
                    list.add(new TitleCandidate(from, to, title, score));
                }
                continue;
            }
            // 正文命中但标题不对：在段内回找真正的「问：…」题干，禁止用父标题（如导购）冒充
            List<TitleCandidate> nested = recallNestedQaInSection(userQuestion, plain, from, to, sec);
            if (!nested.isEmpty()) {
                list.addAll(nested);
            }
        }
        // HTML 兜底常把「一、系统概述与登录」拆成无标记短行，SECTION_START 认不出 → 行级兜底
        if (list.isEmpty()) {
            list.addAll(recallLineTitleCandidates(userQuestion, plain, starts));
        }
        if (list.isEmpty()) {
            list.addAll(recallByAskPrefixScan(userQuestion, plain));
        }
        list.sort(Comparator.comparingInt((TitleCandidate c) -> c.score).reversed());
        if (list.size() > 12) {
            return new ArrayList<>(list.subList(0, 12));
        }
        return list;
    }

    /**
     * 父段标题未对齐、但正文里埋了真正「问：」题干时（HTML 切段失败常见），
     * 在段内再扫一层问答起点。
     */
    private List<TitleCandidate> recallNestedQaInSection(String userQuestion, String plain,
                                                         int sectionFrom, int sectionTo, String sec) {
        List<TitleCandidate> list = new ArrayList<>();
        List<Integer> nested = listQaStarts(sec);
        if (nested.isEmpty()) {
            return list;
        }
        for (int i = 0; i < nested.size(); i++) {
            int relFrom = nested.get(i);
            int relTo = i + 1 < nested.size() ? nested.get(i + 1) : sec.length();
            if (relFrom <= 2) {
                continue; // 仍是父段自己的题干
            }
            String nestSec = sec.substring(relFrom, relTo);
            String nestTitle = sectionTitle(nestSec);
            if (!titleMatchesTopic(userQuestion, nestTitle)) {
                continue;
            }
            int absFrom = sectionFrom + relFrom;
            int absTo = sectionFrom + relTo;
            int score = scoreUserAgainstTitle(userQuestion, nestTitle) + 800;
            list.add(new TitleCandidate(absFrom, Math.min(absTo, plain.length()), nestTitle, score));
        }
        return list;
    }

    /**
     * 全篇扫描「问：…」题干：即使用户问法与题干高度重合（怎么找厂子要货），也要能直接命中。
     */
    private List<TitleCandidate> recallByAskPrefixScan(String userQuestion, String plain) {
        List<TitleCandidate> list = new ArrayList<>();
        if (!StringUtils.hasText(plain)) {
            return list;
        }
        List<Integer> starts = listQaStarts(plain);
        for (int i = 0; i < starts.size(); i++) {
            int from = starts.get(i);
            int to = i + 1 < starts.size() ? starts.get(i + 1) : plain.length();
            String sec = plain.substring(from, to);
            String title = sectionTitle(sec);
            if (!titleMatchesTopic(userQuestion, title)) {
                continue;
            }
            int score = scoreUserAgainstTitle(userQuestion, title) + 500;
            String left = TrainQaIntentMatcher.leftover(userQuestion);
            if (left.length() >= 4 && TrainQaIntentMatcher.compact(title).contains(left)) {
                score += 800;
            }
            list.add(new TitleCandidate(from, to, title, score));
        }
        return list;
    }

    /**
     * 短问行级召回：标题行本身含用户词（如单独一行「系统概述与登录」），
     * 段落范围取到下一硬切点或一定长度内。
     */
    private List<TitleCandidate> recallLineTitleCandidates(String userQuestion, String plain, List<Integer> hardStarts) {
        String userNorm = compact(userQuestion);
        List<TitleCandidate> list = new ArrayList<>();
        Matcher line = Pattern.compile("(?m)^[\\t　 ]*([^\\n\\r]{2,40})$").matcher(plain);
        while (line.find()) {
            String title = line.group(1).trim();
            if (!StringUtils.hasText(title) || title.length() > 40) {
                continue;
            }
            String titleNorm = compact(title);
            if (titleNorm.length() < 2) {
                continue;
            }
            // 跳过答句/过长说明
            if (title.contains("答：") || title.contains("答:") || titleNorm.length() > 36) {
                continue;
            }
            if (!TrainQaIntentMatcher.titleMatches(userQuestion, title)
                    && !TrainQaIntentMatcher.titleMatches(userNorm, title)) {
                continue;
            }
            int score = scoreUserAgainstTitle(userQuestion, title);
            if (score < MIN_SECTION_SCORE && !TrainQaIntentMatcher.titleMatches(userQuestion, title)) {
                continue;
            }
            int from = line.start(1);
            int to = plain.length();
            for (int s : hardStarts) {
                if (s > from + Math.min(8, title.length())) {
                    to = s;
                    break;
                }
            }
            // 再看后续短行标题，避免吞掉下一章
            Matcher next = Pattern.compile("(?m)^[\\t　 ]*([^\\n\\r]{2,40})$").matcher(plain);
            next.region(from + title.length(), plain.length());
            while (next.find()) {
                String nxt = compact(next.group(1));
                if (nxt.length() >= 2 && nxt.length() <= 24
                        && (nxt.startsWith("二") || nxt.startsWith("三") || nxt.startsWith("四")
                        || nxt.matches("^[一二三四五六七八九十\\d]+、.*")
                        || nxt.matches("^\\d+[\\.、．].*")
                        || nxt.startsWith("问题") || nxt.startsWith("问：") || nxt.startsWith("问:"))) {
                    to = Math.min(to, next.start(1));
                    break;
                }
                if (next.start(1) > from + 1500) {
                    break;
                }
            }
            to = Math.min(to, Math.min(plain.length(), from + 1500));
            if (to - from < 8) {
                continue;
            }
            list.add(new TitleCandidate(from, to, title, score));
        }
        return list;
    }

    /** 只用用户原话给标题打分：命中次数多的问答题干优先，罗列词条靠后。 */
    private static int scoreUserAgainstTitle(String userQuestion, String title) {
        return TrainQaIntentMatcher.score(userQuestion, title);
    }

    private static boolean passesUserAlignment(String userQuestion, String title) {
        return titleMatchesTopic(userQuestion, title);
    }

    /** 分词意图：核心词/同义族出现在标题即可，不要求「如何折扣」整句连续贴在标题上。 */
    private static boolean titleMatchesTopic(String userQuestion, String title) {
        return TrainQaIntentMatcher.titleMatches(userQuestion, cleanTopicTitle(title));
    }

    private static String topicKey(String q) {
        return TrainQaIntentMatcher.leftover(q);
    }

    private static String titleCoreOf(String titleNorm) {
        return titleNorm.replaceFirst("^(?:问答\\d+|问题\\d+|问)[：:：]?", "")
                .replaceFirst("^[一二三四五六七八九十\\d]+[、．.]", "");
    }

    private SectionHit sectionHitFromCandidate(TitleCandidate c, IndexedDoc indexed) {
        // AI/目录可能在 content_text 上选段；配图在 HTML 抽的 markedText（含 [图N]）。
        // 一律按题干标题回定位到带图正文，避免用错坐标系导致 imgs=0。
        String marked = TrainQaAnswerSanitizer.stripOfficeMarkup(
                indexed == null ? "" : nullToEmpty(indexed.markedText));
        if (StringUtils.hasText(marked) && marked.contains("[图")) {
            SectionHit withImg = hitByTitleInSource(c, marked);
            // 只有真正带 [图N] 才优先用 marked；无图的短段不要挡住后续补图逻辑
            if (withImg != null && countMarkers(withImg.bodyWithMarkers) > 0) {
                return withImg;
            }
        }
        String source = qaSource(indexed);
        if (!StringUtils.hasText(source)) {
            return null;
        }
        SectionHit hit = hitByTitleInSource(c, source);
        if (hit != null) {
            return hit;
        }
        // 最后才用候选偏移（仅当 source 与建目录时同一份文本）
        if (c.from < 0 || c.from >= source.length()) {
            return null;
        }
        int from = c.from;
        int to = nextQaAfter(source, from);
        String sec = appendStolenHeadingImagesIfHasBody(source, from, to);
        sec = sec.trim();
        if (!StringUtils.hasText(sec)) {
            return null;
        }
        SectionHit fallback = new SectionHit();
        fallback.from = from;
        fallback.to = to;
        fallback.score = c.score;
        fallback.title = StringUtils.hasText(c.title) ? c.title : sectionTitle(sec);
        fallback.bodyWithMarkers = trimSectionForDisplay(sec);
        return fallback;
    }

    private SectionHit hitByTitleInSource(TitleCandidate c, String source) {
        if (c == null || !StringUtils.hasText(source)) {
            return null;
        }
        int from = indexOfQaTitle(source, c.title);
        if (from < 0) {
            return null;
        }
        int to = nextQaAfter(source, from);
        String sec = appendStolenHeadingImagesIfHasBody(source, from, to);
        sec = sec.trim();
        if (!StringUtils.hasText(sec)) {
            return null;
        }
        SectionHit hit = new SectionHit();
        hit.from = from;
        hit.to = to;
        hit.score = c.score;
        hit.title = StringUtils.hasText(c.title) ? c.title : sectionTitle(sec);
        hit.bodyWithMarkers = trimSectionForDisplay(sec);
        return hit;
    }

    /** 有本问答文才回收下一问题干前的配图，避免空答文串下一问图 */
    private static String appendStolenHeadingImagesIfHasBody(String source, int from, int to) {
        String sec = source.substring(from, Math.min(to, source.length()));
        String bodyProbe = stripImageMarkers(trimSectionForDisplay(sec)).trim();
        if (to < source.length() && StringUtils.hasText(bodyProbe)) {
            sec = sec + stealHeadingImages(source.substring(to));
        }
        return sec;
    }

    /** 带 [图N] 且能认出「问：」的正文优先，保证切段和配图同一份。 */
    private static String qaSource(IndexedDoc indexed) {
        String marked = TrainQaAnswerSanitizer.stripOfficeMarkup(
                indexed == null ? "" : nullToEmpty(indexed.markedText));
        String text = TrainQaAnswerSanitizer.stripOfficeMarkup(
                indexed == null ? "" : nullToEmpty(indexed.textForAi));
        int qaMarked = listQaStarts(marked).size();
        int qaText = listQaStarts(text).size();
        if (qaMarked >= 2 && (marked.contains("[图") || qaMarked >= qaText)) {
            return marked;
        }
        if (qaText >= 2 && text.contains("[图")) {
            return text;
        }
        return StringUtils.hasText(marked) ? marked : text;
    }

    private static int indexOfQaTitle(String source, String title) {
        String want = compact(cleanTopicTitle(title));
        if (want.length() < 2 || !StringUtils.hasText(source)) {
            return -1;
        }
        List<Integer> starts = listQaStarts(source);
        if (starts.isEmpty()) {
            starts = listSectionStarts(source);
        }
        if (starts.isEmpty()) {
            starts = new ArrayList<>();
            starts.add(0);
        }
        int best = -1;
        int bestScore = -1;
        for (int i = 0; i < starts.size(); i++) {
            int from = starts.get(i);
            int to = i + 1 < starts.size() ? starts.get(i + 1) : source.length();
            String got = compact(cleanTopicTitle(sectionTitle(source.substring(from, to))));
            if (got.length() < 2) {
                continue;
            }
            int score = 0;
            if (got.equals(want)) {
                score = 100;
            } else if (got.contains(want) || want.contains(got)) {
                score = 70;
            } else {
                // 语义近义：content_text 题干 vs HTML 题干用词不一致时也能回定位
                String wantCore = TrainQaIntentMatcher.canonicalCore(want);
                String gotCore = TrainQaIntentMatcher.canonicalCore(got);
                if (wantCore.length() >= 4 && gotCore.contains(wantCore)) {
                    score = 85;
                } else if (gotCore.length() >= 4 && wantCore.contains(gotCore)) {
                    score = 80;
                } else if (wantCore.length() >= 4 && gotCore.length() >= 4
                        && TrainQaIntentMatcher.titleMatches(want, got)) {
                    score = 75;
                }
            }
            if (score > bestScore) {
                bestScore = score;
                best = from;
            }
        }
        if (bestScore >= 70) {
            return best;
        }
        String raw = cleanTopicTitle(title);
        int at = source.indexOf(raw);
        if (at < 0 && raw.length() > 4) {
            at = source.indexOf(raw.substring(0, Math.min(raw.length(), 8)));
        }
        if (at < 0) {
            return -1;
        }
        int line = source.lastIndexOf('\n', at);
        return line < 0 ? at : line + 1;
    }

    /**
     * 下一问题干上常锚着上一问最后一张图。把「下一问题干行 → 答：之前」的 [图N] 收回本问。
     */
    private static String stealHeadingImages(String nextSection) {
        if (!StringUtils.hasText(nextSection)) {
            return "";
        }
        String s = nextSection.stripLeading();
        Matcher qa = QA_LINE.matcher(s);
        if (!qa.lookingAt()) {
            return "";
        }
        int nl = indexOfNewline(s);
        if (nl < 0 || nl + 1 >= s.length()) {
            return "";
        }
        s = s.substring(nl + 1);
        int ans = indexOfAnswerMark(s);
        String head = ans >= 0 ? s.substring(0, ans) : s;
        Matcher m = Pattern.compile("\\A(?:\\s*\\[图\\d+]\\s*)+").matcher(head);
        if (!m.find()) {
            return "";
        }
        return m.group();
    }

    private static int nextQaAfter(String source, int from) {
        for (int s : listQaStarts(source)) {
            if (s > from) {
                return s;
            }
        }
        return source.length();
    }

    /**
     * 题干起点。注意：
     * 1) 「请问：」里的「问：」不能当新题（否则答文被提前切开，bodyLen=0 再偷下一问的图）；
     * 2) 无序序号「1.问：」「（3）问：」也要认；
     * 3) 文档已有「问：」题干时，不再把答文里的「如何/怎么」短行当新题。
     */
    private static List<Integer> listQaStarts(String plain) {
        List<Integer> starts = new ArrayList<>();
        if (!StringUtils.hasText(plain)) {
            return starts;
        }
        // 行首（可带无序/有序序号）+ 「问：/问答N：/问题N：」
        Matcher lineAsk = Pattern.compile(
                "(?m)^[\\t　 ]*(?:[\\d一二三四五六七八九十百]+[\\.、．\\)）]\\s*|[(（][\\d一二三四五六七八九十]+[)）]\\s*)?"
                        + "(?:问答\\s*\\d+|问题\\s*\\d+|问)\\s*[：:：]"
        ).matcher(plain);
        while (lineAsk.find()) {
            addQaStart(starts, plain, lineAsk.start());
        }
        // HTML 粘连兜底：非行首的「问：」，但不得落在「请/反/追/疑」等字后面（避免「请问：」假切）
        Matcher glued = Pattern.compile(
                "(?<![\\u4e00-\\u9fffA-Za-z0-9])(?:问答\\s*\\d+|问题\\s*\\d+|问)\\s*[：:：]"
        ).matcher(plain);
        while (glued.find()) {
            addQaStart(starts, plain, glued.start());
        }
        // 仅当全文几乎没有「问：」题干时，才用「如何/怎么」短行作题干（纯「如何折扣」类手册）
        if (starts.size() < 2) {
            Matcher lineHow = Pattern.compile("(?m)^[\\t　 ]*(?:如何|怎么)[^\\n\\r]{1,50}$").matcher(plain);
            while (lineHow.find()) {
                addQaStart(starts, plain, lineHow.start());
            }
        }
        starts.sort(Integer::compareTo);
        return starts;
    }

    private static void addQaStart(List<Integer> starts, String plain, int at) {
        if (at < 0) {
            return;
        }
        // 「1.问：」「（3）问：」行首与「问」字位置不同，按题干关键字对齐去重
        int norm = normalizeAskAnchor(plain, at);
        for (int i = 0; i < starts.size(); i++) {
            int s = starts.get(i);
            int sNorm = normalizeAskAnchor(plain, s);
            if (Math.abs(sNorm - norm) <= 2 || Math.abs(s - at) <= 12) {
                // 保留更靠前的行首偏移，便于截取完整题干行
                if (at < s) {
                    starts.set(i, at);
                }
                return;
            }
        }
        starts.add(at);
    }

    private static int normalizeAskAnchor(String plain, int at) {
        if (!StringUtils.hasText(plain) || at < 0 || at >= plain.length()) {
            return at;
        }
        int end = Math.min(plain.length(), at + 24);
        Matcher m = Pattern.compile("(?:问答\\s*\\d+|问题\\s*\\d+|问)\\s*[：:：]").matcher(plain);
        m.region(at, end);
        if (m.find()) {
            return m.start();
        }
        return at;
    }

    /** 诊断用：抽若干段标题，便于排查召回为空。 */
    private static String sampleSectionTitles(String plain, int limit) {
        if (!StringUtils.hasText(plain) || limit <= 0) {
            return "-";
        }
        List<Integer> starts = listQaStarts(plain);
        if (starts.isEmpty()) {
            starts = listSectionStarts(plain);
        }
        List<String> titles = new ArrayList<>();
        for (int i = 0; i < starts.size() && titles.size() < limit; i++) {
            int from = starts.get(i);
            int to = i + 1 < starts.size() ? starts.get(i + 1) : Math.min(plain.length(), from + 80);
            String title = cleanTopicTitle(sectionTitle(plain.substring(from, to)));
            if (StringUtils.hasText(title)) {
                titles.add(title);
            }
        }
        return titles.isEmpty() ? "-" : String.join(" | ", titles);
    }

    private static boolean htmlHasImg(String html) {
        return StringUtils.hasText(html) && html.toLowerCase(Locale.ROOT).contains("<img");
    }

    private static void appendImagesToAnswerHtml(TrainAssistantAskVO.QaItem item) {
        if (item == null || item.getImages() == null || item.getImages().isEmpty() || htmlHasImg(item.getAnswerHtml())) {
            return;
        }
        StringBuilder imgs = new StringBuilder();
        for (String url : item.getImages()) {
            if (!StringUtils.hasText(url)) {
                continue;
            }
            imgs.append("<img src=\"")
                    .append(escapeHtml(url))
                    .append("\" alt=\"培训配图\" style=\"max-width:100%;height:auto;display:block;margin:10px 0;border-radius:6px;\" />");
        }
        if (imgs.isEmpty()) {
            return;
        }
        String html = item.getAnswerHtml();
        if (StringUtils.hasText(html)) {
            int close = html.lastIndexOf("</div>");
            item.setAnswerHtml(close >= 0 ? html.substring(0, close) + imgs + html.substring(close) : html + imgs);
            return;
        }
        if (!StringUtils.hasText(item.getAnswer())) {
            item.setAnswerHtml("<div class=\"train-qa-answer\" style=\"line-height:1.7;\">" + imgs + "</div>");
            return;
        }
        item.setAnswerHtml("<div class=\"train-qa-answer\" style=\"line-height:1.7;\">"
                + escapeHtml(item.getAnswer()).replace("\n", "<br/>") + imgs + "</div>");
    }

    private static final class TitleCandidate {
        final int from;
        final int to;
        final String title;
        final int score;

        TitleCandidate(int from, int to, String title, int score) {
            this.from = from;
            this.to = to;
            this.title = title;
            this.score = score;
        }
    }

    private static final String INDEX_TEXT_FILE = "_index.txt";

    /**
     * 答疑索引：磁盘缓存 → 本地原文件 → 公网拉原文件按段落重抽 → 最后才 HTML。
     * 必须按 Word 段落顺序落 [图N]，才能保证「一问一答」块内图文不错位。
     */
    private IndexedDoc prepareIndexedDoc(DocBundle doc) {
        IndexedDoc empty = new IndexedDoc();
        empty.textForAi = nullToEmpty(doc == null ? null : doc.text);
        if (doc == null || !StringUtils.hasText(doc.filePath)) {
            return empty;
        }
        try {
            long slot = doc.indexSlot > 0 ? doc.indexSlot : doc.versionId;
            if (!doc.forceReindex) {
                IndexedDoc cached = loadCachedIndexedFromDisk(slot);
                if (cached != null && !cached.images.isEmpty()) {
                    preferStructuredPlainText(cached, doc.text);
                    return cached;
                }
            }
            byte[] bytes = readDocBytes(doc);
            if (bytes != null && bytes.length > 0) {
                IndexedDoc indexed = indexOriginalBytes(bytes, doc);
                if (indexed != null && !indexed.images.isEmpty()) {
                    preferStructuredPlainText(indexed, doc.text);
                    saveIndexedText(slot, indexed.textForAi);
                    if (doc.indexSlot <= 0) {
                        String rich = buildUrlPreviewHtml(indexed);
                        if (StringUtils.hasText(rich)) {
                            doc.html = rich;
                            jdbc.update("UPDATE train_doc_version SET content_html = ? WHERE id = ?", rich, doc.versionId);
                        }
                    }
                    log.info("答疑原文件索引完成 versionId={} slot={} images={} textLen={} hasMarkers={}",
                            doc.versionId, slot, indexed.images.size(),
                            indexed.textForAi == null ? 0 : indexed.textForAi.length(),
                            indexed.textForAi != null && indexed.textForAi.contains("[图"));
                    return indexed;
                }
            }
            // 仅无原文件时退回 HTML（图位可能错，仅兜底）
            if (StringUtils.hasText(doc.html) && doc.html.contains("<img")) {
                IndexedDoc fromHtml = indexHtmlImages(doc.html, doc.text);
                if (!fromHtml.images.isEmpty()) {
                    preferStructuredPlainText(fromHtml, doc.text);
                    log.warn("答疑使用 HTML 兜底索引 versionId={} images={} textLen={}（原文件不可用，图位可能不准）",
                            doc.versionId, fromHtml.images.size(),
                            fromHtml.textForAi == null ? 0 : fromHtml.textForAi.length());
                    return fromHtml;
                }
            }
            return empty;
        } catch (Exception e) {
            log.warn("答疑配图索引失败 versionId={}: {}", doc.versionId, e.getMessage());
            IndexedDoc fallback = indexHtmlImages(doc.html, doc.text);
            preferStructuredPlainText(fallback, doc == null ? null : doc.text);
            return fallback;
        }
    }

    /** 本地有文件读本地；没有则按公网 URL 拉取并落盘，供下次直读。 */
    private byte[] readDocBytes(DocBundle doc) {
        if (doc == null || !StringUtils.hasText(doc.filePath)) {
            return null;
        }
        Path path = fileStorage.resolveUploadPath(doc.filePath);
        try {
            if (path != null && Files.isRegularFile(path)) {
                return Files.readAllBytes(path);
            }
        } catch (Exception e) {
            log.warn("读取本地培训原文件失败 path={}: {}", path, e.getMessage());
        }
        String publicUrl = fileStorage.toPublicUrl(doc.filePath);
        byte[] remote = readPublicDocBytes(publicUrl);
        if (remote == null || remote.length == 0) {
            log.warn("培训原文件本地与公网均不可用 versionId={} path={} url={}",
                    doc.versionId, doc.filePath, publicUrl);
            return null;
        }
        Path saveTo = resolveTrainDocSavePath(doc.filePath);
        if (saveTo != null) {
            try {
                Files.createDirectories(saveTo.getParent());
                Files.write(saveTo, remote);
                log.info("已缓存公网培训原文件 versionId={} path={} bytes={}",
                        doc.versionId, saveTo, remote.length);
            } catch (Exception e) {
                log.warn("缓存公网培训原文件失败 versionId={}: {}", doc.versionId, e.getMessage());
            }
        }
        return remote;
    }

    private Path resolveTrainDocSavePath(String filePath) {
        String stored = fileStorage.normalizeStoragePath(filePath);
        if (!StringUtils.hasText(stored) || !stored.startsWith("/uploads/")) {
            return null;
        }
        Path existing = fileStorage.resolveUploadPath(stored);
        if (existing != null) {
            return existing;
        }
        // resolveUploadPath 在文件不存在时返回 null：uploads/train/img/{id} → 上溯两级到 uploads/
        // trainVersionImageDir = {uploadDir}/train/img/{id} → parent×3 = uploadDir
        String relative = stored.substring("/uploads/".length());
        Path uploadRoot = fileStorage.trainVersionImageDir(0).getParent().getParent().getParent();
        return uploadRoot.resolve(relative).toAbsolutePath().normalize();
    }

    private IndexedDoc indexOriginalBytes(byte[] bytes, DocBundle doc) throws Exception {
        String lower = doc.fileName == null ? "" : doc.fileName.toLowerCase(Locale.ROOT);
        String pathLower = doc.filePath == null ? "" : doc.filePath.toLowerCase(Locale.ROOT);
        long slot = doc.indexSlot > 0 ? doc.indexSlot : doc.versionId;
        boolean zip = bytes.length >= 2 && bytes[0] == 'P' && bytes[1] == 'K';
        if (lower.endsWith(".docx") || pathLower.endsWith(".docx") || zip) {
            return indexDocxPackage(bytes, slot, doc.text);
        }
        if (lower.endsWith(".doc") || pathLower.endsWith(".doc")) {
            return indexDocHwpf(bytes, slot, doc.text);
        }
        return null;
    }

    /**
     * HTML 抽正文常打乱「一、标题」换行，导致登录等章节进不了 SECTION_START。
     * 切段优先改用结构更好的 content_text；配图列表与上下文仍保留自 HTML。
     */
    private void preferStructuredPlainText(IndexedDoc indexed, String contentText) {
        if (indexed == null || !StringUtils.hasText(contentText)) {
            return;
        }
        String withMarkers = StringUtils.hasText(indexed.markedText) && indexed.markedText.contains("[图")
                ? indexed.markedText
                : nullToEmpty(indexed.textForAi);
        int markerQa = listQaStarts(withMarkers).size();
        int textQa = listQaStarts(contentText).size();
        // 原文件段落序已带 [图N] 且一问一答够用：直接用，禁止再 merge 进 content_text（会冲掉末段图）
        if (StringUtils.hasText(withMarkers) && withMarkers.contains("[图")
                && markerQa >= Math.max(textQa, 1)) {
            indexed.markedText = withMarkers;
            indexed.textForAi = withMarkers;
            fillImageContexts(indexed);
            log.info("答疑沿用原文件带图正文：markerQa={} textQa={} textLen={}",
                    markerQa, textQa, withMarkers.length());
            return;
        }
        int htmlStarts = listSectionStarts(withMarkers).size();
        int textStarts = listSectionStarts(contentText).size();
        boolean textRicherBiz = contentTextRicherInBizTerms(withMarkers, contentText);
        // 仅当 content_text 题干明显更全、而带图正文问法不够时，才把图合进 content_text
        boolean preferText = textQa > markerQa
                || (textQa > 0 && markerQa == 0)
                || (textQa > 0 && textRicherBiz && textQa >= markerQa)
                || (textQa > 0 && textStarts > htmlStarts + 2);
        if (!preferText) {
            return;
        }
        log.info("答疑切段改用 content_text：markerQa={} textQa={} htmlStarts={} textStarts={} richerBiz={}",
                markerQa, textQa, htmlStarts, textStarts, textRicherBiz);
        if (withMarkers.contains("[图")) {
            indexed.markedText = withMarkers;
        }
        indexed.textForAi = mergeMarkersIntoStructured(contentText, withMarkers);
        fillImageContexts(indexed);
    }

    /** content_text 比 HTML 抽字多出关键业务词（厂家+订货等）时，切段应改用 content_text。 */
    private static boolean contentTextRicherInBizTerms(String htmlPlain, String contentText) {
        String hc = TrainQaIntentMatcher.canonicalize(TrainQaIntentMatcher.compact(htmlPlain));
        String tc = TrainQaIntentMatcher.canonicalize(TrainQaIntentMatcher.compact(contentText));
        if (tc.contains("厂家") && tc.contains("订货") && !(hc.contains("厂家") && hc.contains("订货"))) {
            return true;
        }
        String[] keys = {"厂家", "订货", "寄存", "挂单", "折扣", "退货", "登录", "导购"};
        int htmlHit = 0;
        int textHit = 0;
        for (String k : keys) {
            if (hc.contains(k)) {
                htmlHit++;
            }
            if (tc.contains(k)) {
                textHit++;
            }
        }
        return textHit >= htmlHit + 2;
    }

    /** 把带位置的 [图N] 插回结构更好的纯文本，避免切段把段末配图丢掉。 */
    private static String mergeMarkersIntoStructured(String structured, String marked) {
        if (!StringUtils.hasText(structured)) {
            return marked;
        }
        if (!StringUtils.hasText(marked) || !marked.contains("[图") || structured.contains("[图")) {
            return structured;
        }
        StringBuilder out = new StringBuilder(structured);
        int searchFrom = 0;
        Matcher m = Pattern.compile("\\[图(\\d+)\\]").matcher(marked);
        int last = 0;
        while (m.find()) {
            String prefix = stripImageMarkers(marked.substring(last, m.start()));
            last = m.end();
            String needle = compact(prefix);
            if (needle.length() > 20) {
                needle = needle.substring(needle.length() - 20);
            }
            String marker = " [图" + m.group(1) + "] ";
            int insertAt = needle.length() >= 4 ? indexAfterCompact(out, needle, searchFrom) : -1;
            if (insertAt < 0) {
                continue;
            }
            out.insert(insertAt, marker);
            searchFrom = insertAt + marker.length();
        }
        return out.toString();
    }

    private static int indexAfterCompact(StringBuilder haystack, String compactNeedle, int from) {
        if (haystack == null || from >= haystack.length() || !StringUtils.hasText(compactNeedle)) {
            return -1;
        }
        String h = haystack.substring(from);
        String hc = compact(h);
        int at = hc.indexOf(compactNeedle);
        if (at < 0) {
            return -1;
        }
        int endCompact = at + compactNeedle.length();
        int orig = 0;
        int cc = 0;
        while (orig < h.length() && cc < endCompact) {
            if (!Character.isWhitespace(h.charAt(orig))) {
                cc++;
            }
            orig++;
        }
        return from + orig;
    }

    private static boolean sectionTitlesContain(String plain, String needle) {
        if (!StringUtils.hasText(plain) || !StringUtils.hasText(needle)) {
            return false;
        }
        String n = compact(needle);
        List<Integer> starts = listSectionStarts(plain);
        for (int i = 0; i < starts.size(); i++) {
            int from = starts.get(i);
            int to = i + 1 < starts.size() ? starts.get(i + 1) : plain.length();
            String titleNorm = compact(sectionTitle(plain.substring(from, to)));
            if (titleNorm.contains(n)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 拆题配图必须落在当前这台机器的上传目录。
     * 测试环境拆题，图就在测试机；生产环境拆题，图就在生产机。
     * 文档链接上的原文件会先拉到本机，再把每张图写成 /uploads/train/img/...。
     */
    private void ensureServedImages(IndexedDoc indexed, long slot) {
        if (indexed == null || indexed.images == null || indexed.images.isEmpty()) {
            return;
        }
        int served = 0;
        for (int i = 0; i < indexed.images.size(); i++) {
            String src = indexed.images.get(i);
            if (servedFileExists(src)) {
                String rel = uploadsRelative(src);
                if (StringUtils.hasText(rel)) {
                    indexed.images.set(i, rel);
                }
                served++;
                continue;
            }
            byte[] bytes = imageBytes(src);
            if (bytes == null || bytes.length == 0) {
                log.warn("拆题配图没有落到本环境 slot={} no={} src={}", slot, i + 1, brief(src));
                continue;
            }
            String saved = fileStorage.saveTrainVersionImage(slot, i + 1, bytes, "fig" + sniffImageExt(bytes, src));
            if (StringUtils.hasText(saved)) {
                indexed.images.set(i, saved);
                served++;
            }
        }
        log.info("拆题配图已写入本环境上传目录 slot={} served={}/{}", slot, served, indexed.images.size());
    }

    private boolean servedFileExists(String src) {
        String rel = uploadsRelative(src);
        if (!StringUtils.hasText(rel)) {
            return false;
        }
        Path path = fileStorage.resolveUploadPath(rel);
        return path != null && Files.isRegularFile(path);
    }

    private static String uploadsRelative(String src) {
        if (!StringUtils.hasText(src) || src.startsWith("data:")) {
            return null;
        }
        int idx = src.indexOf("/uploads/");
        if (idx < 0) {
            return null;
        }
        String rel = src.substring(idx);
        int q = rel.indexOf('?');
        if (q >= 0) {
            rel = rel.substring(0, q);
        }
        return rel;
    }

    private byte[] imageBytes(String src) {
        if (!StringUtils.hasText(src)) {
            return null;
        }
        if (src.startsWith("data:")) {
            int comma = src.indexOf(',');
            if (comma > 0 && src.substring(0, comma).toLowerCase(Locale.ROOT).contains(";base64")) {
                try {
                    return Base64.getDecoder().decode(src.substring(comma + 1).replaceAll("\\s", ""));
                } catch (Exception e) {
                    log.warn("拆题 data 配图解码失败: {}", e.getMessage());
                    return null;
                }
            }
            return null;
        }
        String rel = uploadsRelative(src);
        if (StringUtils.hasText(rel)) {
            String pub = fileStorage.toPublicUrl(rel);
            return readPublicDocBytes(pub);
        }
        if (src.startsWith("http://") || src.startsWith("https://")) {
            return readPublicDocBytes(src);
        }
        return null;
    }

    private static String sniffImageExt(byte[] bytes, String src) {
        if (bytes != null && bytes.length >= 8
                && bytes[0] == (byte) 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G') {
            return ".png";
        }
        if (bytes != null && bytes.length >= 3 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8) {
            return ".jpg";
        }
        if (bytes != null && bytes.length >= 6 && bytes[0] == 'G' && bytes[1] == 'I' && bytes[2] == 'F') {
            return ".gif";
        }
        String lower = src == null ? "" : src.toLowerCase(Locale.ROOT);
        if (lower.contains(".jpg") || lower.contains(".jpeg")) {
            return ".jpg";
        }
        if (lower.contains(".gif")) {
            return ".gif";
        }
        if (lower.contains(".webp")) {
            return ".webp";
        }
        return ".png";
    }

    private static String brief(String src) {
        if (!StringUtils.hasText(src)) {
            return "";
        }
        return src.length() <= 80 ? src : src.substring(0, 80);
    }

    /** 读取带正确 [图N] 位置的磁盘缓存（_index.txt + 配图文件）。 */
    private IndexedDoc loadCachedIndexedFromDisk(long versionId) {
        Path dir = fileStorage.trainVersionImageDir(versionId);
        Path indexFile = dir.resolve(INDEX_TEXT_FILE);
        if (!Files.isRegularFile(indexFile)) {
            return null;
        }
        try {
            String text = TrainQaAnswerSanitizer.stripOfficeMarkup(
                    Files.readString(indexFile, StandardCharsets.UTF_8));
            if (!StringUtils.hasText(text) || !text.contains("[图")) {
                return null;
            }
            if (headingOwnsNextImage(text)) {
                return null;
            }
            try (var stream = Files.list(dir)) {
                List<Path> files = stream
                        .filter(Files::isRegularFile)
                        .filter(p -> {
                            String name = p.getFileName().toString();
                            return !INDEX_TEXT_FILE.equals(name) && parseLeadingInt(name) < Integer.MAX_VALUE;
                        })
                        .sorted((a, b) -> Integer.compare(
                                parseLeadingInt(a.getFileName().toString()),
                                parseLeadingInt(b.getFileName().toString())))
                        .toList();
                if (files.isEmpty()) {
                    return null;
                }
                IndexedDoc indexed = new IndexedDoc();
                for (Path f : files) {
                    String rel = fileStorage.toRelativeUploadPath(f);
                    if (StringUtils.hasText(rel)) {
                        indexed.images.add(rel);
                    }
                }
                if (indexed.images.isEmpty()) {
                    return null;
                }
                indexed.textForAi = text;
                indexed.markedText = text;
                fillImageContexts(indexed);
                return indexed;
            }
        } catch (Exception e) {
            return null;
        }
    }

    private void saveIndexedText(long versionId, String textForAi) {
        if (!StringUtils.hasText(textForAi)) {
            return;
        }
        try {
            Path dir = fileStorage.trainVersionImageDir(versionId);
            Files.createDirectories(dir);
            Files.writeString(dir.resolve(INDEX_TEXT_FILE), textForAi, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("保存答疑索引正文失败 versionId={}: {}", versionId, e.getMessage());
        }
    }

    private static int parseLeadingInt(String name) {
        Matcher m = Pattern.compile("^(\\d+)").matcher(name == null ? "" : name);
        if (m.find()) {
            return Integer.parseInt(m.group(1));
        }
        return Integer.MAX_VALUE;
    }

    /** 题干行后面紧跟 [图N]、答之前：旧索引把上一问截图锚在了本题标题上，需要重抽。 */
    /** 「见下图/进入下图」之后到下一问之前没有 [图N]，说明段末截图没抽出来。 */
    private static boolean indexMissingFigureAfterXiatu(String text) {
        if (!StringUtils.hasText(text)) {
            return false;
        }
        Matcher m = Pattern.compile("下图").matcher(text);
        while (m.find()) {
            int from = m.end();
            int to = text.length();
            for (int s : listQaStarts(text)) {
                if (s > from) {
                    to = s;
                    break;
                }
            }
            if (to - from > 1200) {
                continue;
            }
            String between = text.substring(from, to);
            if (!between.contains("[图")) {
                return true;
            }
        }
        return false;
    }

    private static boolean headingOwnsNextImage(String text) {
        if (!StringUtils.hasText(text)) {
            return false;
        }
        return Pattern.compile("(?m)^[\\t　 ]*(?:问答\\s*\\d+|问题\\s*\\d+|问)\\s*[：:：][^\\n]*\\R[\\t　 ]*\\[图\\d+]")
                .matcher(text).find()
                || Pattern.compile("(?m)^[\\t　 ]*(?:如何|怎么)[^\\n]{1,40}\\R[\\t　 ]*\\[图\\d+]")
                .matcher(text).find();
    }

    private static boolean isQaTitleLine(String line) {
        if (!StringUtils.hasText(line)) {
            return false;
        }
        return QA_LINE.matcher(line.strip()).lookingAt();
    }

    /** 按配对的 &lt;w:p&gt; 切段，避免嵌套段落把后面的 drawing 截丢。 */
    private static List<String> splitDocxParagraphs(String xml) {
        List<String> list = new ArrayList<>();
        if (!StringUtils.hasText(xml)) {
            return list;
        }
        int i = 0;
        while (i < xml.length()) {
            int start = indexOfWpOpen(xml, i);
            if (start < 0) {
                break;
            }
            int end = matchingWpClose(xml, start);
            if (end < 0 || end <= start) {
                break;
            }
            list.add(xml.substring(start, end));
            i = end;
        }
        return list;
    }

    private static int indexOfWpOpen(String xml, int from) {
        int i = from;
        while (i < xml.length()) {
            int at = xml.indexOf("<w:p", i);
            if (at < 0) {
                return -1;
            }
            int after = at + 4;
            if (after >= xml.length()) {
                return -1;
            }
            char c = xml.charAt(after);
            if (c == '>' || c == '/' || Character.isWhitespace(c)) {
                return at;
            }
            i = after;
        }
        return -1;
    }

    private static int matchingWpClose(String xml, int start) {
        int gt = xml.indexOf('>', start);
        if (gt < 0) {
            return -1;
        }
        if (gt > start && xml.charAt(gt - 1) == '/') {
            return gt + 1;
        }
        int i = gt + 1;
        int depth = 1;
        while (i < xml.length() && depth > 0) {
            int open = indexOfWpOpen(xml, i);
            int close = xml.indexOf("</w:p>", i);
            if (close < 0) {
                return -1;
            }
            if (open >= 0 && open < close) {
                int ogt = xml.indexOf('>', open);
                if (ogt < 0) {
                    return -1;
                }
                if (xml.charAt(ogt - 1) == '/') {
                    i = ogt + 1;
                    continue;
                }
                depth++;
                i = ogt + 1;
            } else {
                depth--;
                i = close + 6;
            }
        }
        return depth == 0 ? i : -1;
    }

    private byte[] readPublicDocBytes(String url) {
        if (!StringUtils.hasText(url) || !url.startsWith("http")) {
            return null;
        }
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
            conn.setInstanceFollowRedirects(true);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(120000);
            conn.setRequestMethod("GET");
            int code = conn.getResponseCode();
            if (code >= 400) {
                log.warn("公网培训原文件下载失败 code={} url={}", code, url);
                return null;
            }
            try (InputStream in = conn.getInputStream()) {
                return in.readAllBytes();
            }
        } catch (Exception e) {
            log.warn("公网培训原文件下载失败 url={}: {}", url, e.getMessage());
            return null;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /**
     * 段落内按 document.xml 出现顺序输出 w:t 与图片。
     * Word 常把多组「问/答」挤在同一 &lt;w:p&gt;，若先抽全文再挂图，图会整坨落到上一段或段末。
     */
    private void appendDocxParagraphInOrder(String paraXml, ZipFile zip, Map<String, String> relTarget,
                                            Map<String, Integer> targetToFig, IndexedDoc indexed,
                                            long versionId, StringBuilder ai) {
        if (!StringUtils.hasText(paraXml)) {
            return;
        }
        Pattern textPat = Pattern.compile("(?is)<w:t[^>]*>(.*?)</w:t>");
        Pattern blipPat = Pattern.compile(
                "(?is)<(?:a:blip|asvg:svgBlip|v:imagedata)\\b[^>]*?(?:r:embed|r:link|r:id)\\s*=\\s*\"(rId[^\"]+)\"");
        List<int[]> events = new ArrayList<>();
        Matcher tm = textPat.matcher(paraXml);
        while (tm.find()) {
            events.add(new int[]{tm.start(), 0, tm.start(), tm.end()});
        }
        Matcher bm = blipPat.matcher(paraXml);
        while (bm.find()) {
            events.add(new int[]{bm.start(), 1, bm.start(1), bm.end(1)});
        }
        if (events.isEmpty()) {
            return;
        }
        events.sort(Comparator.comparingInt(a -> a[0]));
        StringBuilder out = new StringBuilder();
        for (int[] ev : events) {
            if (ev[1] == 0) {
                out.append(unescapeXml(paraXml.substring(ev[2], ev[3])));
            } else {
                String rId = paraXml.substring(ev[2], ev[3]);
                StringBuilder marks = new StringBuilder();
                appendDocxImage(zip, relTarget, targetToFig, indexed, versionId, rId, marks);
                if (!marks.isEmpty()) {
                    out.append(marks);
                }
            }
        }
        String s = out.toString().replaceAll("[ \\t\\x0B\\f\\r]+", " ").trim();
        if (!StringUtils.hasText(s)) {
            return;
        }
        // 同一段粘了多问时，在后续「问：」前断开，保证一问一答切块能切开
        s = s.replaceAll("(?<=\\S)(?=(?:问答\\s*\\d+|问题\\s*\\d+|问)\\s*[：:：])", "\n");
        ai.append(s).append('\n');
    }

    private void appendDocxImage(ZipFile zip, Map<String, String> relTarget, Map<String, Integer> targetToFig,
                                 IndexedDoc indexed, long versionId, String rId, StringBuilder marks) {
        String target = relTarget.get(rId);
        if (!StringUtils.hasText(target)) {
            return;
        }
        Integer reused = targetToFig.get(target);
        if (reused != null) {
            marks.append(" [图").append(reused).append("] ");
            return;
        }
        try {
            String entryName = target.startsWith("/") ? target.substring(1) : "word/" + target.replaceFirst("^\\.\\./", "");
            if (!entryName.startsWith("word/")) {
                entryName = "word/" + entryName;
            }
            entryName = entryName.replace("\\", "/");
            byte[] imgBytes = readZipEntryBytes(zip, entryName);
            if (imgBytes == null || imgBytes.length == 0) {
                String alt = "word/" + target.replaceFirst("^[/\\\\]+", "");
                imgBytes = readZipEntryBytes(zip, alt);
                entryName = alt;
            }
            if (imgBytes == null || imgBytes.length == 0) {
                return;
            }
            String fileHint = entryName.contains("/")
                    ? entryName.substring(entryName.lastIndexOf('/') + 1)
                    : entryName;
            String url = fileStorage.saveTrainVersionImage(
                    versionId, indexed.images.size() + 1, imgBytes, fileHint);
            if (!StringUtils.hasText(url)) {
                return;
            }
            indexed.images.add(url);
            targetToFig.put(target, indexed.images.size());
            marks.append(" [图").append(indexed.images.size()).append("] ");
        } catch (Exception e) {
            log.warn("抽取 Word 配图失败 rId={}: {}", rId, e.getMessage());
        }
    }

    /**
     * 按 document.xml 段落顺序抽取文字与 blip 图片，配图落到 /uploads/train/img/{versionId}/。
     */
    private IndexedDoc indexDocxPackage(byte[] bytes, long versionId, String fallbackText) throws Exception {
        Path tmp = Files.createTempFile("train-docx-", ".docx");
        try {
            Files.write(tmp, bytes);
            // 仅首次抽取时清空；已有缓存时不应走到这里
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
                StringBuilder ai = new StringBuilder();
                Map<String, Integer> targetToFig = new HashMap<>();
                for (String paraXml : splitDocxParagraphs(documentXml)) {
                    // 按 XML 出现顺序交错输出文字与配图；禁止「整段图挂到上一段」
                    appendDocxParagraphInOrder(paraXml, zip, relTarget, targetToFig, indexed, versionId, ai);
                }
                // 未挂到段落的媒体不再追加到文末，避免「问→下一问」区间被无关图污染
                indexed.textForAi = TrainQaAnswerSanitizer.stripOfficeMarkup(
                        !ai.isEmpty() ? ai.toString().trim() : nullToEmpty(fallbackText));
                indexed.markedText = indexed.textForAi;
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
        IndexedDoc diskCached = loadCachedIndexedFromDisk(versionId);
        if (diskCached != null && !diskCached.images.isEmpty()) {
            return diskCached;
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
            indexed.markedText = indexed.textForAi;
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
        indexed.markedText = indexed.textForAi;
        fillImageContexts(indexed);
        return indexed;
    }

    /** 话题标题命中最低分；宁可不答，也不拼无关段。 */
    private static final int MIN_SECTION_SCORE = 18;

    /**
     * 话题起点（下一段即结束）：
     * 问答形「问/问题N/问答N」；主题形「一、二、三、」「1.标题」「现金/组合支付」等短标题行。
     */
    private static final Pattern SECTION_START = Pattern.compile(
            "问答\\s*\\d+\\s*[：:：]"
                    + "|问题\\s*\\d+\\s*[：:：]"
                    + "|问\\s*[：:：]"
                    + "|问\\s+(?=\\S)"
                    + "|(?:(?<=\\n)|^)\\s*[一二三四五六七八九十]+、[^\\n\\r]{1,40}"
                    + "|(?:(?<=\\n)|^)\\s*\\d+[\\.、．][^\\n\\r]{1,40}"
                    + "|(?:(?<=\\n)|^)\\s*(?:组合支付|现金支付|扫码支付|银行卡支付|微信[^\\n]{0,12}|支付宝[^\\n]{0,12})\\s*(?=\\n|$)");

    /** 问答模式题干行：问： / 1.问： / 问题1： / 如何进行折扣/改价 */
    private static final Pattern QA_LINE = Pattern.compile(
            "(?m)^[\\t　 ]*(?:[\\d一二三四五六七八九十百]+[\\.、．\\)）]\\s*|[(（][\\d一二三四五六七八九十]+[)）]\\s*)?"
                    + "(?:问答\\s*\\d+|问题\\s*\\d+|问)\\s*[：:：]"
                    + "|(?m)^[\\t　 ]*(?:如何|怎么)[^\\n\\r]{1,40}$");

    private static final class SectionHit {
        int from;
        int to;
        int score;
        String title;
        String bodyWithMarkers;
    }

    /**
     * 严格切段：只认「话题标题」对齐意图，正文 = 该标题之后直到下一话题之前。
     */
    private SectionHit locateStrictSection(String topic, String answerHint, String userQuestion, IndexedDoc indexed) {
        String plain = indexed.textForAi == null ? "" : indexed.textForAi;
        if (!StringUtils.hasText(plain)) {
            return null;
        }
        List<Integer> starts = listSectionStarts(plain);
        if (starts.isEmpty()) {
            return null;
        }
        String intent = compact(firstNonBlank(topic, userQuestion));
        String userNorm = compact(userQuestion);
        String hintNorm = compact(answerHint);
        int bestIdx = -1;
        int bestScore = 0;
        for (int i = 0; i < starts.size(); i++) {
            int from = starts.get(i);
            int to = i + 1 < starts.size() ? starts.get(i + 1) : plain.length();
            // 过长段多半是切分失败，降低可信度
            if (to - from > 6000) {
                continue;
            }
            String sec = plain.substring(from, to);
            String title = sectionTitle(sec);
            String titleNorm = compact(title);
            if (titleNorm.length() < 2) {
                continue;
            }
            // 课程表里的纯表头（标题后几乎无说明）跳过
            String bodyProbe = stripImageMarkers(trimSectionForDisplay(sec));
            if (bodyProbe.length() < 4 && countMarkers(sec) == 0) {
                continue;
            }
            int score = scoreTitleIntent(intent, userNorm, hintNorm, titleNorm);
            if (score > bestScore) {
                bestScore = score;
                bestIdx = i;
            }
        }
        if (bestIdx < 0) {
            return null;
        }
        // 标题分不够时，再用「用户/提示原文」落点落入哪一段（仍不跨段）
        if (bestScore < MIN_SECTION_SCORE) {
            int[] needleHit = bestMatchSpan(plain, firstNonBlank(topic, userQuestion));
            if (needleHit == null) {
                needleHit = bestMatchSpan(plain, answerHint);
            }
            if (needleHit != null) {
                int at = needleHit[0];
                for (int i = 0; i < starts.size(); i++) {
                    int from = starts.get(i);
                    int to = i + 1 < starts.size() ? starts.get(i + 1) : plain.length();
                    if (at >= from && at < to) {
                        String title = sectionTitle(plain.substring(from, to));
                        int fallScore = Math.max(bestScore, scoreTitleIntent(intent, userNorm, hintNorm, compact(title)));
                        // 落点命中标题关键词才接受
                        if (fallScore >= MIN_SECTION_SCORE || keywordHitScore(intent, compact(title)) >= 2) {
                            bestIdx = i;
                            bestScore = Math.max(fallScore, MIN_SECTION_SCORE);
                        }
                        break;
                    }
                }
            }
        }
        if (bestIdx < 0 || bestScore < MIN_SECTION_SCORE) {
            return null;
        }
        int from = starts.get(bestIdx);
        int to = bestIdx + 1 < starts.size() ? starts.get(bestIdx + 1) : plain.length();
        String sec = plain.substring(from, to).trim();
        SectionHit hit = new SectionHit();
        hit.from = from;
        hit.to = to;
        hit.score = bestScore;
        hit.title = sectionTitle(sec);
        hit.bodyWithMarkers = trimSectionForDisplay(sec);
        return hit;
    }

    private TrainAssistantAskVO.QaItem materializeFromSection(SectionHit hit, IndexedDoc indexed) {
        TrainAssistantAskVO.QaItem item = new TrainAssistantAskVO.QaItem();
        String title = StringUtils.hasText(hit.title) ? hit.title : "相关说明";
        item.setQuestion(cleanTopicTitle(TrainQaAnswerSanitizer.stripOfficeMarkup(title)));
        String mixed = StringUtils.hasText(hit.bodyWithMarkers) ? hit.bodyWithMarkers : "";
        mixed = TrainQaAnswerSanitizer.stripOfficeMarkup(mixed);
        mixed = cutAtNextTopic(mixed);
        // 目录在 content_text、配图在 HTML marked：无图时按题干回带图正文再抽一次
        if (countMarkers(mixed) == 0) {
            String fromMarked = reloadSectionMixedFromMarked(title, indexed);
            if (StringUtils.hasText(fromMarked) && countMarkers(fromMarked) > 0) {
                String markedAnswer = stripImageMarkers(fromMarked).trim();
                if (!StringUtils.hasText(stripImageMarkers(mixed)) && StringUtils.hasText(markedAnswer)) {
                    mixed = fromMarked;
                } else {
                    mixed = (stripImageMarkers(mixed).trim() + "\n" + keepImageMarkersOnly(fromMarked)).trim();
                }
            }
        }
        List<Integer> refs = extractMarkers(mixed, indexed);
        // 禁止按上下文猜图：配图只认本问答块内的 [图N]
        item.setAnswer(stripImageMarkers(mixed).trim());
        item.setImages(toImageUrls(refs, indexed));
        item.setAnswerHtml(buildMixedAnswerHtml(mixed, indexed));
        if (TrainQaAnswerSanitizer.looksLikeSourceCode(item.getAnswer(), item.getAnswerHtml())) {
            log.warn("答疑抽段疑似源码，丢弃 title={} bodyLen={}",
                    item.getQuestion(), item.getAnswer() == null ? 0 : item.getAnswer().length());
            return null;
        }
        log.info("答疑严格切段 score={} span=[{},{}) title={} imgs={} bodyLen={}",
                hit.score, hit.from, hit.to, item.getQuestion(), refs.size(),
                item.getAnswer() == null ? 0 : item.getAnswer().length());
        return item;
    }

    /** 按题干在带 [图N] 的 markedText 中重切本问，收回答前/答后配图。 */
    private String reloadSectionMixedFromMarked(String title, IndexedDoc indexed) {
        String marked = TrainQaAnswerSanitizer.stripOfficeMarkup(
                indexed == null ? "" : nullToEmpty(indexed.markedText));
        if (!StringUtils.hasText(marked) || !marked.contains("[图")) {
            // textForAi 本身也可能带图（未分 marked 时）
            marked = TrainQaAnswerSanitizer.stripOfficeMarkup(
                    indexed == null ? "" : nullToEmpty(indexed.textForAi));
        }
        if (!StringUtils.hasText(marked) || !marked.contains("[图")) {
            return "";
        }
        int from = indexOfQaTitle(marked, title);
        if (from < 0) {
            return "";
        }
        int to = nextQaAfter(marked, from);
        String sec = appendStolenHeadingImagesIfHasBody(marked, from, to);
        return trimSectionForDisplay(sec.trim());
    }

    /** 标题对齐意图分：只看标题，不看正文（避免串段）。 */
    private static int scoreTitleIntent(String intent, String userNorm, String hintNorm, String titleNorm) {
        if (!StringUtils.hasText(titleNorm)) {
            return 0;
        }
        int score = 0;
        String titleCore = titleNorm.replaceFirst("^(?:问答\\d+|问题\\d+|问)[：:：]?", "")
                .replaceFirst("^[一二三四五六七八九十\\d]+[、．.]", "");
        for (String needle : List.of(intent, userNorm, hintNorm)) {
            if (!StringUtils.hasText(needle) || needle.length() < 2) {
                continue;
            }
            if (titleNorm.contains(needle) || needle.contains(titleNorm) || titleCore.contains(needle) || needle.contains(titleCore)) {
                score += 100;
            }
            score += keywordHitScore(needle, titleNorm) * 15;
            score += overlapScore(needle, titleNorm);
            score += keywordHitScore(needle, titleCore) * 10;
        }
        return score;
    }

    private static String sectionTitle(String sec) {
        if (!StringUtils.hasText(sec)) {
            return "";
        }
        int nl = indexOfNewline(sec);
        String first = (nl < 0 ? sec : sec.substring(0, nl)).trim();
        // 「问：xxx答：yyy」同一行时，标题只取到答之前
        int ans = indexOfAnswerMark(first);
        if (ans > 0) {
            first = first.substring(0, ans).trim();
        }
        if (first.length() > 80) {
            first = first.substring(0, 80);
        }
        return first;
    }

    private static String cleanTopicTitle(String title) {
        if (!StringUtils.hasText(title)) {
            return "相关说明";
        }
        return title.replaceAll("\\s*\\[图\\d+]\\s*", " ").trim();
    }

    private static String cutAtNextTopic(String body) {
        if (!StringUtils.hasText(body)) {
            return "";
        }
        Matcher m = QA_LINE.matcher(body);
        if (m.find() && m.start() > 8) {
            return body.substring(0, m.start()).trim();
        }
        return body;
    }

    private static String firstNonBlank(String a, String b) {
        if (StringUtils.hasText(a)) {
            return a;
        }
        return b == null ? "" : b;
    }

    private static int indexOfNewline(String s) {
        int n1 = s.indexOf('\n');
        int n2 = s.indexOf('\r');
        if (n1 < 0) {
            return n2;
        }
        if (n2 < 0) {
            return n1;
        }
        return Math.min(n1, n2);
    }

    private static int countMarkers(String text) {
        if (!StringUtils.hasText(text)) {
            return 0;
        }
        Matcher m = Pattern.compile("\\[图\\d+]").matcher(text);
        int n = 0;
        while (m.find()) {
            n++;
        }
        return n;
    }

    private static List<Integer> extractMarkers(String text, IndexedDoc indexed) {
        List<Integer> refs = new ArrayList<>();
        if (!StringUtils.hasText(text) || indexed.images.isEmpty()) {
            return refs;
        }
        Matcher m = Pattern.compile("\\[图(\\d+)\\]").matcher(text);
        while (m.find()) {
            addRef(refs, Integer.parseInt(m.group(1)), indexed);
        }
        return refs;
    }

    private static void addRef(List<Integer> refs, int n, IndexedDoc indexed) {
        if (n > 0 && n <= indexed.images.size() && !refs.contains(n)) {
            refs.add(n);
        }
    }

    /**
     * 去掉题干行，保留答案与配图。
     * 「问」与「答」之间的 [图N]、以及答后到下一问的 [图N] 都保留（培训截图常在答前/答后）。
     */
    private static String trimSectionForDisplay(String sec) {
        if (!StringUtils.hasText(sec)) {
            return "";
        }
        String s = sec.stripLeading();
        Matcher qa = QA_LINE.matcher(s);
        if (qa.lookingAt()) {
            int nl = indexOfNewline(s);
            if (nl >= 0 && nl + 1 < s.length()) {
                s = s.substring(nl + 1);
            } else {
                int ans = indexOfAnswerMark(s);
                if (ans < 0) {
                    return keepImageMarkersOnly(s);
                }
                s = s.substring(ans);
            }
        }
        int ans = indexOfAnswerMark(s);
        if (ans >= 0) {
            String preImgs = keepImageMarkersOnly(s.substring(0, ans));
            String after = s.substring(ans).replaceFirst("^答\\s*[：:：]\\s*", "").trim();
            if (StringUtils.hasText(preImgs)) {
                return (preImgs + "\n" + after).trim();
            }
            return after;
        }
        return s.trim();
    }

    private static String keepImageMarkersOnly(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        Matcher m = Pattern.compile("\\[图\\d+]").matcher(text);
        while (m.find()) {
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            sb.append(m.group());
        }
        return sb.toString();
    }

    private static String stripImageMarkers(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        return text.replaceAll("\\s*\\[图\\d+]\\s*", " ").replaceAll("[ \\t\\x0B\\f\\r]+", " ")
                .replaceAll("\\n{3,}", "\n\n").trim();
    }

    private static int indexOfAnswerMark(String sec) {
        Matcher m = Pattern.compile("答\\s*[：:：]").matcher(sec);
        return m.find() ? m.start() : -1;
    }

    private static int keywordHitScore(String qNorm, String head) {
        if (qNorm.length() < 2 || head.isEmpty()) {
            return 0;
        }
        int hits = 0;
        String core = qNorm.replaceAll("[如何怎么怎样什么吗呢？?的了吗啊哦呀呗]|问[：:：]?", "");
        if (core.length() < 2) {
            core = qNorm;
        }
        for (int len = Math.min(4, core.length()); len >= 2; len--) {
            for (int i = 0; i + len <= core.length(); i++) {
                if (head.contains(core.substring(i, i + len))) {
                    hits++;
                }
            }
            if (hits > 0) {
                break;
            }
        }
        return Math.min(hits, 5);
    }

    private static List<Integer> listSectionStarts(String plain) {
        List<Integer> starts = new ArrayList<>();
        if (!StringUtils.hasText(plain)) {
            return starts;
        }
        Matcher m = SECTION_START.matcher(plain);
        while (m.find()) {
            int at = m.start();
            if (starts.isEmpty() || at > starts.get(starts.size() - 1)) {
                starts.add(at);
            }
        }
        return starts;
    }

    private static String compact(String s) {
        return s == null ? "" : s.replaceAll("\\s+", "");
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
        // 把无空白下标映射回原文下标
        int origAt = 0;
        int compactAt = 0;
        while (origAt < haystack.length() && compactAt < bestAt) {
            if (!Character.isWhitespace(haystack.charAt(origAt))) {
                compactAt++;
            }
            origAt++;
        }
        int start = Math.min(haystack.length() - 1, origAt);
        double ratio = haystack.length() / (double) Math.max(1, h.length());
        int end = Math.min(haystack.length(), start + (int) Math.ceil(bestLen * ratio) + 8);
        return new int[]{start, Math.max(start + 1, end)};
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

    /**
     * 答疑下发前把 /uploads/... 配图改成当前环境的公网绝对地址，
     * 避免前端写死错主机时配图 404（内容对、图数对但打不开）。
     */
    private List<TrainAssistantAskVO.QaItem> withPublicImages(List<TrainAssistantAskVO.QaItem> items) {
        if (items == null || items.isEmpty()) {
            return items == null ? List.of() : items;
        }
        for (TrainAssistantAskVO.QaItem item : items) {
            applyPublicImageUrls(item);
        }
        return items;
    }

    private void applyPublicImageUrls(TrainAssistantAskVO.QaItem item) {
        if (item == null) {
            return;
        }
        if (item.getImages() != null && !item.getImages().isEmpty()) {
            List<String> pubs = new ArrayList<>(item.getImages().size());
            for (String u : item.getImages()) {
                String pub = fileStorage.toPublicUrl(u);
                if (StringUtils.hasText(pub)) {
                    pubs.add(pub);
                }
            }
            item.setImages(pubs);
        }
        if (StringUtils.hasText(item.getAnswerHtml())) {
            item.setAnswerHtml(rewriteUploadSrcToPublic(item.getAnswerHtml()));
        }
    }

    private String rewriteUploadSrcToPublic(String html) {
        if (!StringUtils.hasText(html) || !html.contains("/uploads/")) {
            return html;
        }
        Matcher m = Pattern.compile("(?i)(\\bsrc=[\"'])([^\"']+)([\"'])").matcher(html);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String src = m.group(2);
            String pub = src;
            if (src.contains("/uploads/")) {
                String converted = fileStorage.toPublicUrl(src);
                if (StringUtils.hasText(converted)) {
                    pub = converted;
                }
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(m.group(1) + pub + m.group(3)));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /** 把带 [图N] 的原文转成图文混排 HTML（图片插在原文位置）。 */
    private static String buildMixedAnswerHtml(String textWithMarkers, IndexedDoc indexed) {
        StringBuilder html = new StringBuilder();
        html.append("<div class=\"train-qa-answer\" style=\"line-height:1.7;\">");
        if (!StringUtils.hasText(textWithMarkers)) {
            html.append("</div>");
            return html.toString();
        }
        Matcher m = Pattern.compile("\\[图(\\d+)\\]").matcher(textWithMarkers);
        int last = 0;
        while (m.find()) {
            String before = textWithMarkers.substring(last, m.start());
            if (StringUtils.hasText(before)) {
                html.append(escapeHtml(before).replace("\n", "<br/>"));
            }
            int n = Integer.parseInt(m.group(1));
            if (n >= 1 && n <= indexed.images.size()) {
                String url = indexed.images.get(n - 1);
                html.append("<img src=\"")
                        .append(url)
                        .append("\" alt=\"培训配图")
                        .append(n)
                        .append("\" style=\"max-width:100%;height:auto;display:block;margin:10px 0;border-radius:6px;\" />");
            }
            last = m.end();
        }
        String tail = textWithMarkers.substring(last);
        if (StringUtils.hasText(tail)) {
            html.append(escapeHtml(tail).replace("\n", "<br/>"));
        }
        html.append("</div>");
        return html.toString();
    }

    private DocBundle loadLatestDoc(String category) {
        return jdbc.query("""
                SELECT d.id doc_id, d.latest_version_id, v.version_label, v.content_text, v.content_html,
                       v.file_path, v.file_name,
                       (SELECT COUNT(1) FROM train_doc_version x
                        WHERE x.doc_id = d.id AND x.is_active = 1 AND x.id <> IFNULL(d.latest_version_id, 0)
                          AND x.content_text IS NOT NULL AND TRIM(x.content_text) <> '') old_cnt
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
        /** 大于 0 时配图目录用这个槽位，避免同一版本里多份收银文档共用一份索引。 */
        long indexSlot;
        /** 拆题时不走旧缓存，按文档链接在本机上传目录重新抽出配图。 */
        boolean forceReindex;
    }

    private static class IndexedDoc {
        String textForAi = "";
        /** 带 [图N] 的原文，切段配图用；textForAi 可能被换成结构更好的纯文本。 */
        String markedText = "";
        List<String> images = new ArrayList<>();
        List<String> contexts = new ArrayList<>();
    }
}
