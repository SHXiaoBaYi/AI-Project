package com.base.admin.service;

import com.base.admin.domain.vo.HrResumeParsePreviewVO;
import com.base.admin.exception.BusinessException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 从常见简历文件中抽取姓名 / 电话 / 邮箱，供新增候选人时预填核对 */
@Service
public class HrResumeParseService {

    /** 允许邮箱中间被 PDF 抽成空格 */
    private static final Pattern EMAIL = Pattern.compile(
            "[a-zA-Z0-9._%+\\-]+\\s*@\\s*[a-zA-Z0-9.\\-]+\\s*\\.\\s*[a-zA-Z]{2,}",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern EMAIL_LABEL = Pattern.compile(
            "(?:电子邮箱|邮箱|邮件|E-?mail|Email|Mail)[:：\\s|｜]*([a-zA-Z0-9._%+\\-\\s]+@[a-zA-Z0-9.\\-\\s]+\\.[a-zA-Z]{2,})",
            Pattern.CASE_INSENSITIVE);

    /** 11 位手机，允许中间空格/横线/点 */
    private static final Pattern MOBILE_LOOSE = Pattern.compile(
            "(?<![\\d])(?:\\+?86[\\s\\-.]*)?(1[3-9](?:[\\s\\-.]*\\d){9})(?![\\d])");
    private static final Pattern PHONE_LABEL = Pattern.compile(
            "(?:联系电话|手机号码|手机号|电话号码|联系方式|移动电话|手机|电话|Tel(?:ephone)?|Mobile|Phone|Cell)[:：\\s|｜]*([+0-9\\-\\s.()]{7,24})",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern NAME_LABEL = Pattern.compile(
            "(?:姓\\s*名|名\\s*字|候选人|求职者|Name)[:：\\s|｜]*([\\u4e00-\\u9fa5·]{2,8}|[A-Za-z][A-Za-z\\s·\\-.]{1,40})",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern NAME_NEXT_LINE = Pattern.compile(
            "(?:姓\\s*名|名\\s*字|Name)[:：|｜\\t ]*\\R+[ \\t]*([\\u4e00-\\u9fa5·]{2,8}|[A-Za-z][A-Za-z\\s·\\-.]{1,40})",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern CN_NAME_TOKEN = Pattern.compile("[\\u4e00-\\u9fa5·]{2,4}");
    private static final Pattern CN_NAME_LINE = Pattern.compile("^[\\u4e00-\\u9fa5·]{2,4}$");
    private static final Pattern NAME_WITH_TITLE = Pattern.compile(
            "^([\\u4e00-\\u9fa5·]{2,4})\\s*[|/｜·•\\-—]\\s*.{1,20}$");
    private static final Pattern FILE_NAME_CN = Pattern.compile("([\\u4e00-\\u9fa5·]{2,4})");

    /**
     * Boss/猎聘导出常见：
     * 7-杨露-【小红书运营专员_上海 10-15K】杨露 3年
     * 4-王雅琪-王雅琪
     * 16-张三-张三新媒体运营
     * 29-王五-王五-总经理秘书 (1)
     */
    private static final Pattern BOSS_SERIAL_NAME = Pattern.compile(
            "^(\\d{1,4})[-_\\s.]+([\\u4e00-\\u9fa5·]{2,4})(?:女士|先生)?[-_\\s]*(.*)$");
    private static final Pattern BOSS_BRACKET = Pattern.compile("【([^】]+)】");
    private static final Pattern BOSS_SALARY = Pattern.compile(
            "(\\d{1,3}\\s*[-~～—]\\s*\\d{1,3}\\s*[kKwW千]|\\d{1,3}\\s*[kKwW千]|面议)");
    private static final Pattern BOSS_YEARS = Pattern.compile(
            "(\\d{1,2}\\s*年(?:以上)?|应届(?:生)?|在校生)");
    private static final Pattern BOSS_DUP_NAME = Pattern.compile(
            "([\\u4e00-\\u9fa5·]{2,4})(?:女士|先生)?(?:[\\s\\-_】].*)?\\1(?:女士|先生)?");

    private static final Set<String> NAME_STOP = Set.of(
            "男", "女", "未知", "保密", "未婚", "已婚", "党员", "团员", "群众",
            "本科", "硕士", "博士", "大专", "专科", "高中", "初中", "mba", "MBA",
            "上海", "北京", "深圳", "广州", "杭州", "南京", "成都", "武汉", "西安", "苏州", "重庆", "天津",
            "在职", "离职", "应届", "实习", "全职", "兼职", "现居", "户籍", "籍贯",
            "教育", "经历", "工作", "项目", "技能", "自我", "评价", "简介", "基本", "信息",
            "个人", "简历", "求职", "意向", "岗位", "期望", "薪资", "年薪", "月薪",
            // 常见栏目名（易被误判为 2~4 字姓名）
            "核心优势", "个人优势", "专业技能", "职业技能", "工作经历", "工作经验", "项目经验",
            "教育背景", "教育经历", "自我评价", "自我介绍", "个人总结", "获奖情况", "荣誉证书",
            "证书奖项", "培训经历", "校园经历", "社会实践", "兴趣爱好", "其他信息", "附加信息",
            "求职意向", "基本资料", "基本信息", "联系方式", "个人资料", "个人情况", "个人简介",
            "工作描述", "岗位职责", "主要成就", "专业课程", "在校情况", "实习经历", "实习经验",
            "语言能力", "计算机", "相关技能", "职业规划", "到岗时间", "期望薪资", "期望职位",
            "期望行业", "期望城市", "目前状态", "政治面貌", "婚姻状况", "工作年限",
            "链路搭建", "内容策划", "投放策略", "数据分析", "效果追踪", "矩阵搭建");

    private static final Set<String> CITY_WORDS = Set.of(
            "上海", "北京", "深圳", "广州", "杭州", "南京", "成都", "武汉", "西安", "苏州",
            "重庆", "天津", "长沙", "郑州", "青岛", "大连", "厦门", "福州", "合肥", "济南",
            "无锡", "宁波", "东莞", "佛山", "珠海", "昆明", "贵阳", "南宁", "海口", "乌鲁木齐",
            "拉萨", "银川", "西宁", "呼和浩特", "哈尔滨", "长春", "沈阳", "石家庄", "太原",
            "南昌", "异地");

    public HrResumeParsePreviewVO parse(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请选择简历文件");
        }
        String original = file.getOriginalFilename() == null ? "resume" : file.getOriginalFilename();
        original = decodeFileName(original);
        String ext = original.contains(".")
                ? original.substring(original.lastIndexOf('.')).toLowerCase(Locale.ROOT)
                : "";
        String text;
        try {
            byte[] bytes = file.getBytes();
            text = extractText(bytes, ext);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("简历解析失败：" + e.getMessage());
        }
        return buildPreview(original, text);
    }

    private String extractText(byte[] bytes, String ext) throws Exception {
        String byExt = switch (ext) {
            case ".pdf" -> extractPdf(bytes);
            case ".docx" -> extractDocx(bytes);
            case ".doc" -> extractDocSmart(bytes);
            case ".xlsx" -> extractExcel(new ByteArrayInputStream(bytes), true);
            case ".xls" -> extractExcel(new ByteArrayInputStream(bytes), false);
            case ".txt", ".md", ".csv", ".rtf" -> decodeText(bytes);
            default -> null;
        };
        if (byExt != null) {
            return byExt;
        }
        // 扩展名不可靠时按文件头猜测
        if (bytes.length >= 4 && bytes[0] == '%' && bytes[1] == 'P' && bytes[2] == 'D' && bytes[3] == 'F') {
            return extractPdf(bytes);
        }
        if (bytes.length >= 2 && bytes[0] == 'P' && bytes[1] == 'K') {
            try {
                return extractDocx(bytes);
            } catch (Exception ignored) {
                try {
                    return extractExcel(new ByteArrayInputStream(bytes), true);
                } catch (Exception e) {
                    throw new BusinessException("暂不支持解析该格式，请上传 pdf / doc / docx / xls / xlsx / txt");
                }
            }
        }
        throw new BusinessException("暂不支持解析该格式，请上传 pdf / doc / docx / xls / xlsx / txt");
    }

    private static String extractDocSmart(byte[] bytes) throws Exception {
        // 不少「.doc」实际是 docx（PK zip）
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
            stripper.setSortByPosition(true);
            String sorted = nz(stripper.getText(doc));
            stripper.setSortByPosition(false);
            String plain = nz(stripper.getText(doc));
            // 取更能抽出联系方式的一份；长度接近时优先保留排序结果
            int scoreSorted = contactScore(sorted);
            int scorePlain = contactScore(plain);
            if (scorePlain > scoreSorted) {
                return plain;
            }
            if (scoreSorted > scorePlain) {
                return sorted;
            }
            return sorted.length() >= plain.length() ? sorted : plain;
        }
    }

    private static int contactScore(String text) {
        if (!StringUtils.hasText(text)) {
            return 0;
        }
        int score = 0;
        if (EMAIL.matcher(text).find()) {
            score += 3;
        }
        if (MOBILE_LOOSE.matcher(text).find()) {
            score += 3;
        }
        if (NAME_LABEL.matcher(text).find() || NAME_NEXT_LINE.matcher(text).find()) {
            score += 2;
        }
        return score;
    }

    private static String extractExcel(InputStream in, boolean xssf) throws Exception {
        DataFormatter formatter = new DataFormatter();
        StringBuilder sb = new StringBuilder();
        try (Workbook wb = xssf ? new XSSFWorkbook(in) : new HSSFWorkbook(in)) {
            for (int s = 0; s < wb.getNumberOfSheets(); s++) {
                Sheet sheet = wb.getSheetAt(s);
                if (sheet == null) {
                    continue;
                }
                for (Row row : sheet) {
                    if (row == null) {
                        continue;
                    }
                    List<String> cells = new ArrayList<>();
                    for (Cell cell : row) {
                        String v = formatter.formatCellValue(cell).trim();
                        if (!v.isEmpty()) {
                            cells.add(v);
                        }
                    }
                    if (!cells.isEmpty()) {
                        sb.append(String.join(" ", cells)).append('\n');
                    }
                }
            }
        }
        return sb.toString();
    }

    private static String decodeText(byte[] bytes) {
        // 优先 UTF-8，明显乱码时再试 GBK（Windows 记事本常见）
        String utf8 = new String(bytes, StandardCharsets.UTF_8);
        if (!looksGarbled(utf8)) {
            return utf8;
        }
        String gbk = new String(bytes, Charset.forName("GBK"));
        return looksGarbled(gbk) ? utf8 : gbk;
    }

    private static boolean looksGarbled(String s) {
        if (s == null || s.isEmpty()) {
            return true;
        }
        int bad = 0;
        int sample = Math.min(s.length(), 400);
        for (int i = 0; i < sample; i++) {
            char c = s.charAt(i);
            if (c == '\uFFFD') {
                bad++;
            }
        }
        return bad > 3;
    }

    private HrResumeParsePreviewVO buildPreview(String fileName, String text) {
        String normalized = normalizeText(text);
        String email = extractEmail(normalized);
        String phone = extractPhone(normalized);
        FileNameHints fileHints = parseBossLiepinFileName(fileName);

        // Boss/猎聘文件名命中时，姓名以文件名为准（正文常是图片名/栏目名）
        String name = null;
        if (fileHints != null && StringUtils.hasText(fileHints.name)) {
            name = fileHints.name;
        } else {
            name = extractNameFromBody(normalized);
            if (!StringUtils.hasText(name)) {
                name = nameFromFileNameFallback(fileName);
            }
        }

        HrResumeParsePreviewVO vo = new HrResumeParsePreviewVO();
        vo.setFileName(fileName);
        vo.setDisplayName(name);
        vo.setPhone(phone);
        vo.setEmail(email);
        if (fileHints != null) {
            vo.setJobHint(fileHints.job);
            vo.setCityHint(fileHints.city);
            vo.setSalaryHint(fileHints.salary);
            vo.setYearsHint(fileHints.years);
        }
        vo.setNameFound(StringUtils.hasText(name));
        vo.setPhoneFound(StringUtils.hasText(phone));
        vo.setEmailFound(StringUtils.hasText(email));

        List<String> missing = new ArrayList<>();
        if (!Boolean.TRUE.equals(vo.getNameFound())) {
            missing.add("姓名");
        }
        if (!Boolean.TRUE.equals(vo.getPhoneFound())) {
            missing.add("电话");
        }
        if (!Boolean.TRUE.equals(vo.getEmailFound())) {
            missing.add("邮箱");
        }
        StringBuilder tip = new StringBuilder();
        if (missing.isEmpty()) {
            tip.append("已识别姓名、电话、邮箱，请核对后保存");
        } else if (missing.size() == 3) {
            tip.append("未识别到姓名、电话、邮箱，请手动填写");
        } else {
            tip.append("未识别到").append(String.join("、", missing)).append("，请手动填写");
        }
        if (fileHints != null && (StringUtils.hasText(fileHints.job)
                || StringUtils.hasText(fileHints.city)
                || StringUtils.hasText(fileHints.salary)
                || StringUtils.hasText(fileHints.years))) {
            tip.append("；文件名提示");
            if (StringUtils.hasText(fileHints.job)) {
                tip.append(" 岗位「").append(fileHints.job).append("」");
            }
            if (StringUtils.hasText(fileHints.city)) {
                tip.append(" 城市「").append(fileHints.city).append("」");
            }
            if (StringUtils.hasText(fileHints.salary)) {
                tip.append(" 薪资「").append(fileHints.salary).append("」");
            }
            if (StringUtils.hasText(fileHints.years)) {
                tip.append(" 年限「").append(fileHints.years).append("」");
            }
        }
        vo.setTip(tip.toString());
        return vo;
    }

    /** 统一空白、全角数字/符号，并合并 PDF 常见的「逐字空格」 */
    static String normalizeText(String text) {
        if (text == null) {
            return "";
        }
        String s = text
                .replace('\u00A0', ' ')
                .replace('\u3000', ' ')
                .replace('\r', '\n')
                .replace("\u200B", "")
                .replace("\uFEFF", "");
        s = toHalfWidth(s);
        // 合并被拆开的邮箱：a @ b . com / a@ b.com
        s = s.replaceAll("(?i)([a-z0-9._%+\\-])\\s*@\\s*([a-z0-9.\\-])", "$1@$2");
        s = s.replaceAll("(?i)(@[a-z0-9.\\-]+)\\s*\\.\\s*([a-z]{2,})", "$1.$2");
        // 合并被拆开的 11 位手机：1 3 8 0 0 1 3 8 0 0 0
        s = collapseSpacedMobile(s);
        // 合并短中文名逐字空格：张 三 / 诸 葛 亮（仅 2~4 字片段）
        s = collapseSpacedChinese(s);
        return s;
    }

    private static String toHalfWidth(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\u3000') {
                sb.append(' ');
            } else if (c >= '\uFF01' && c <= '\uFF5E') {
                sb.append((char) (c - 0xFEE0));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String collapseSpacedMobile(String s) {
        Matcher m = Pattern.compile("(?<!\\d)(1(?:\\s*[3-9])(?:\\s*\\d){9})(?!\\d)").matcher(s);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            m.appendReplacement(sb, Matcher.quoteReplacement(m.group(1).replaceAll("\\s+", "")));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String collapseSpacedChinese(String s) {
        // 只压同行内空格，避免「明\\n手」被拼成「明手」
        return Pattern.compile("([\\u4e00-\\u9fa5])(?:[ \\t　]+([\\u4e00-\\u9fa5])){1,3}")
                .matcher(s)
                .replaceAll(mr -> mr.group().replaceAll("[ \\t　]+", ""));
    }

    private static String extractEmail(String text) {
        Matcher labeled = EMAIL_LABEL.matcher(text);
        while (labeled.find()) {
            String cleaned = cleanEmail(labeled.group(1));
            if (cleaned != null) {
                return cleaned;
            }
        }
        Matcher m = EMAIL.matcher(text);
        while (m.find()) {
            String cleaned = cleanEmail(m.group());
            if (cleaned != null) {
                return cleaned;
            }
        }
        return null;
    }

    private static String cleanEmail(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String e = raw.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
        if (!e.contains("@") || e.startsWith("@") || e.endsWith("@")) {
            return null;
        }
        // 过滤 PDF 里常见的资源伪邮箱
        if (e.contains("example.com") || e.endsWith(".png") || e.endsWith(".jpg")
                || e.contains("wixpress") || e.contains("sentry")) {
            return null;
        }
        if (!EMAIL.matcher(e).matches()) {
            return null;
        }
        return e;
    }

    private static String extractPhone(String text) {
        Matcher labeled = PHONE_LABEL.matcher(text);
        while (labeled.find()) {
            String digits = normalizePhoneDigits(labeled.group(1));
            if (isMobile(digits)) {
                return digits.length() > 11 ? digits.substring(digits.length() - 11) : digits;
            }
        }
        Matcher m = MOBILE_LOOSE.matcher(text);
        if (m.find()) {
            String digits = normalizePhoneDigits(m.group(1));
            if (isMobile(digits)) {
                return digits.length() > 11 ? digits.substring(digits.length() - 11) : digits;
            }
        }
        return null;
    }

    private static String normalizePhoneDigits(String raw) {
        if (raw == null) {
            return "";
        }
        String digits = raw.replaceAll("\\D", "");
        if (digits.startsWith("86") && digits.length() >= 13) {
            digits = digits.substring(2);
        }
        return digits;
    }

    private static boolean isMobile(String digits) {
        return digits != null && digits.matches("1[3-9]\\d{9}");
    }

    private static String extractNameFromBody(String text) {
        Matcher labeled = NAME_LABEL.matcher(text);
        while (labeled.find()) {
            String v = cleanName(labeled.group(1));
            if (v != null) {
                return v;
            }
        }
        Matcher next = NAME_NEXT_LINE.matcher(text);
        while (next.find()) {
            String v = cleanName(next.group(1));
            if (v != null) {
                return v;
            }
        }

        String[] lines = text.split("\\n");
        int checked = 0;
        for (int i = 0; i < lines.length; i++) {
            String t = lines[i].trim();
            if (t.isEmpty()) {
                continue;
            }
            checked++;
            if (checked > 20) {
                break;
            }
            t = t.replaceAll("^[\\-•*|\\s]+", "").trim();
            if (t.matches("(?i)^(姓\\s*名|名\\s*字|Name)[:：|｜]?$")) {
                for (int j = i + 1; j < lines.length && j <= i + 3; j++) {
                    String nextLine = lines[j].trim();
                    if (nextLine.isEmpty()) {
                        continue;
                    }
                    String v = cleanName(nextLine.replaceAll("[|｜,，].*$", "").trim());
                    if (v != null) {
                        return v;
                    }
                    break;
                }
            }
            Matcher titled = NAME_WITH_TITLE.matcher(t);
            if (titled.find()) {
                String v = cleanName(titled.group(1));
                if (v != null) {
                    return v;
                }
            }
            // 无标签时仅接受 2~3 字，避免「核心优势」等 4 字栏目
            if (CN_NAME_LINE.matcher(t).matches() && t.length() <= 3) {
                String v = cleanName(t);
                if (v != null) {
                    return v;
                }
            }
            Matcher head = Pattern.compile("^([\\u4e00-\\u9fa5·]{2,3})(?:\\s{1,}|$)").matcher(t);
            if (head.find() && t.length() <= 30) {
                String v = cleanName(head.group(1));
                if (v != null && !t.contains("公司") && !t.contains("大学") && !t.contains("学院")) {
                    return v;
                }
            }
        }
        return null;
    }

    /**
     * 解析 Boss/猎聘导出文件名。命中序号-姓名 结构时返回非 null（至少带 name）。
     */
    static FileNameHints parseBossLiepinFileName(String fileName) {
        if (!StringUtils.hasText(fileName)) {
            return null;
        }
        String base = fileName.replaceAll("\\.[^.]+$", "");
        base = base.replaceAll("(?i)[\\(（]?\\d+[\\)）]$", "").trim(); // 去 (1)
        base = base.replaceAll("(?i)(resume|cv|个人简历|求职简历|简历)", " ").trim();

        Matcher serial = BOSS_SERIAL_NAME.matcher(base);
        if (!serial.matches()) {
            // 无序号：杨露-【小红书运营专员_上海 10-15K】杨露 3年
            Matcher noSerial = Pattern.compile(
                    "^([\\u4e00-\\u9fa5·]{2,4})(?:女士|先生)?[-_\\s]+(.*)$").matcher(base);
            if (!noSerial.matches()) {
                return null;
            }
            return buildHints(noSerial.group(1), noSerial.group(2));
        }
        return buildHints(serial.group(2), serial.group(3));
    }

    private static FileNameHints buildHints(String rawName, String rest) {
        String name = cleanName(rawName);
        if (name == null) {
            // 李女士 这类匿名：保留「X女士」
            String soft = rawName == null ? null : rawName.trim();
            if (soft != null && soft.matches("[\\u4e00-\\u9fa5·]{1,3}(?:女士|先生)")) {
                name = soft;
            } else {
                return null;
            }
        }
        FileNameHints hints = new FileNameHints();
        hints.name = name;
        String tail = rest == null ? "" : rest.trim();

        Matcher bracket = BOSS_BRACKET.matcher(tail);
        String jobBlob = null;
        if (bracket.find()) {
            jobBlob = bracket.group(1).trim();
            tail = (tail.substring(0, bracket.start()) + " " + tail.substring(bracket.end())).trim();
        }

        // 尾部再出现同名：】杨露 3年 / -王雅琪
        Matcher again = Pattern.compile(
                Pattern.quote(name) + "(?:女士|先生)?\\s*(.*)$").matcher(tail);
        String afterName = tail;
        if (again.find()) {
            afterName = again.group(1) == null ? "" : again.group(1).trim();
        } else {
            // 去重复名段
            Matcher dup = BOSS_DUP_NAME.matcher(name + tail);
            if (dup.find()) {
                afterName = tail.replace(name, " ").trim();
            }
        }

        Matcher years = BOSS_YEARS.matcher(afterName);
        if (years.find()) {
            hints.years = years.group(1).replaceAll("\\s+", "");
        }
        if (jobBlob == null && StringUtils.hasText(afterName)) {
            // 无【】：张三新媒体运营 / 王五-总经理秘书
            String jobish = afterName
                    .replaceAll(BOSS_YEARS.pattern(), " ")
                    .replaceAll("[-_\\s]+", " ")
                    .trim();
            if (StringUtils.hasText(jobish) && !jobish.matches("\\d+")) {
                jobBlob = jobish;
            }
        }
        fillJobCitySalary(hints, jobBlob);
        return hints;
    }

    private static void fillJobCitySalary(FileNameHints hints, String jobBlob) {
        if (!StringUtils.hasText(jobBlob)) {
            return;
        }
        String blob = jobBlob.trim();
        Matcher sal = BOSS_SALARY.matcher(blob);
        if (sal.find()) {
            hints.salary = sal.group(1).replaceAll("\\s+", "");
            blob = (blob.substring(0, sal.start()) + " " + blob.substring(sal.end())).trim();
        }
        // 城市：按 _ / 空格 切分后匹配城市词
        String[] parts = blob.split("[_\\s/｜|]+");
        List<String> jobParts = new ArrayList<>();
        for (String p : parts) {
            String t = p.trim();
            if (!StringUtils.hasText(t)) {
                continue;
            }
            if (CITY_WORDS.contains(t)) {
                if (!StringUtils.hasText(hints.city)) {
                    hints.city = t;
                }
                continue;
            }
            if (t.matches("\\d+") || BOSS_SALARY.matcher(t).matches()) {
                continue;
            }
            jobParts.add(t);
        }
        if (!jobParts.isEmpty()) {
            hints.job = String.join("", jobParts).replaceAll("^[\\-—_\\s]+|[\\-—_\\s]+$", "");
            // 过长则截断
            if (hints.job.length() > 40) {
                hints.job = hints.job.substring(0, 40);
            }
        }
    }

    /** 非 Boss 结构时的弱文件名兜底 */
    private static String nameFromFileNameFallback(String fileName) {
        if (!StringUtils.hasText(fileName)) {
            return null;
        }
        String base = fileName.replaceAll("\\.[^.]+$", "");
        base = base.replaceAll("(?i)(resume|cv|个人简历|求职简历|简历)", " ");
        base = base.replaceFirst("^\\d{1,4}[\\-_\\s.]+", "");
        Matcher fm = FILE_NAME_CN.matcher(base);
        while (fm.find()) {
            String v = cleanName(fm.group(1));
            if (v != null) {
                return v;
            }
        }
        return null;
    }

    private static String decodeFileName(String name) {
        if (!StringUtils.hasText(name) || !name.contains("%")) {
            return name;
        }
        try {
            return java.net.URLDecoder.decode(name, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return name;
        }
    }

    private static String cleanName(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String v = raw.trim().replaceAll("\\s+", " ");
        v = v.replaceAll("[|｜,，。；;].*$", "").trim();
        if (v.length() > 32) {
            v = v.substring(0, 32);
        }
        if (!looksLikeName(v)) {
            return null;
        }
        if (v.chars().anyMatch(ch -> ch >= 0x4e00 && ch <= 0x9fa5)) {
            v = v.replace(" ", "");
        }
        return v;
    }

    private static boolean looksLikeName(String v) {
        if (!StringUtils.hasText(v) || v.length() > 40) {
            return false;
        }
        if (v.contains("@") || v.matches(".*\\d{4,}.*")) {
            return false;
        }
        if (isResumeNoise(v) || NAME_STOP.contains(v)) {
            return false;
        }
        if (CN_NAME_TOKEN.matcher(v).matches()) {
            return !NAME_STOP.contains(v);
        }
        return v.matches("[A-Za-z][A-Za-z\\s·\\-.]{1,40}");
    }

    private static boolean isResumeNoise(String v) {
        String s = v.toLowerCase(Locale.ROOT);
        return s.contains("简历") || s.contains("resume") || s.contains("curriculum")
                || s.contains("求职") || s.contains("应聘") || s.contains("个人简介");
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    /** Boss/猎聘文件名解析结果 */
    static final class FileNameHints {
        String name;
        String job;
        String city;
        String salary;
        String years;
    }
}
