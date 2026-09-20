package com.base.admin.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
@RequiredArgsConstructor
public class HrWorkbookSeeder implements ApplicationRunner {

    private static final Pattern OWNER_GROUP = Pattern.compile("[（(]([^）)]+)[）)]");
    private static final DateTimeFormatter[] DATES = {
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("yyyy/M/d"),
            DateTimeFormatter.ofPattern("yyyy-M-d")
    };
    private static final DateTimeFormatter[] DATE_TIMES = {
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy/M/d H:mm"),
            DateTimeFormatter.ofPattern("yyyy/M/d H:mm:ss")
    };

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactionTemplate;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        Integer existing = jdbc.queryForObject("SELECT COUNT(1) FROM hr_requisition", Integer.class);
        if (existing != null && existing > 0) {
            log.info("招聘基础数据已存在，跳过 Excel 灌数");
            return;
        }
        Path workbook = findWorkbook();
        if (workbook == null) {
            log.warn("未找到招聘基础 Excel，跳过灌数");
            return;
        }
        log.info("开始灌入招聘基础数据 {}", workbook);
        Parsed parsed = parse(workbook);
        transactionTemplate.executeWithoutResult(status -> write(parsed));
        log.info("招聘基础数据灌入完成：高校 {}，QS {}，需求 {}，投递 {}，面试轮次 {}",
                parsed.schools.size(), parsed.qs.size(), parsed.jobs.size(), parsed.apps.size(), parsed.rounds);
    }

    private Path findWorkbook() throws Exception {
        List<Path> roots = List.of(Path.of("HR"), Path.of("..", "HR"), Path.of("..", "..", "HR"));
        for (Path root : roots) {
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (var walk = Files.walk(root)) {
                Path found = walk.filter(path -> {
                    String name = path.getFileName().toString();
                    return name.endsWith(".xlsx") && !name.startsWith("~$") && name.contains("HR");
                }).findFirst().orElse(null);
                if (found != null) {
                    return found.toAbsolutePath().normalize();
                }
            }
        }
        return null;
    }

    private Parsed parse(Path workbook) throws Exception {
        Map<String, Path> files = new HashMap<>();
        try (var walk = Files.walk(workbook.getParent())) {
            walk.filter(Files::isRegularFile).forEach(path -> files.putIfAbsent(path.getFileName().toString(), path.toAbsolutePath()));
        }
        DataFormatter formatter = new DataFormatter();
        Parsed parsed = new Parsed();
        parsed.files = files;
        try (InputStream in = Files.newInputStream(workbook); Workbook book = new XSSFWorkbook(in)) {
            readSchools(book.getSheet("全国高等学校名单"), formatter, parsed);
            readQs(book.getSheet("全球大学综合排名 - QS500(2026)"), formatter, parsed);
            readJobs(book.getSheet("招聘需求填报"), formatter, parsed);
            readApps(book.getSheet("AI简历分拣"), formatter, parsed);
            readInterviews(book.getSheet("面试流程管理"), formatter, parsed);
        }
        return parsed;
    }

    private void readSchools(Sheet sheet, DataFormatter formatter, Parsed parsed) {
        if (sheet == null) {
            return;
        }
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            String name = cell(row, 0, formatter);
            String code = cell(row, 3, formatter);
            if (name.isEmpty() || code.isEmpty() || !seen.add(code) || !seen.add("n:" + name)) {
                continue;
            }
            School school = new School();
            school.name = cut(name, 128);
            school.intro = cut(cell(row, 1, formatter), 8000);
            school.type = cut(blank(cell(row, 2, formatter), "普通高等学校"), 32);
            school.code = cut(code, 16);
            school.tags = emptyToNull(cut(cell(row, 4, formatter), 64));
            school.qs = emptyToNull(cut(cell(row, 5, formatter), 32));
            school.authority = cut(cell(row, 6, formatter), 64);
            school.region = cut(cell(row, 7, formatter), 64);
            school.level = emptyToNull(cut(cell(row, 8, formatter), 16));
            school.remark = emptyToNull(cut(cell(row, 9, formatter), 255));
            parsed.schools.add(school);
        }
    }

    private void readQs(Sheet sheet, DataFormatter formatter, Parsed parsed) {
        if (sheet == null) {
            return;
        }
        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            String name = cell(row, 1, formatter);
            if (name.isEmpty()) {
                continue;
            }
            Qs qs = new Qs();
            qs.rank = parseInt(cell(row, 0, formatter), i);
            qs.nameZh = cut(name, 128);
            qs.nameEn = cut(blank(cell(row, 2, formatter), name), 256);
            qs.abbr = emptyToNull(cut(cell(row, 3, formatter), 64));
            qs.country = cut(blank(cell(row, 4, formatter), "未知"), 64);
            qs.score = parseScore(cell(row, 5, formatter));
            parsed.qs.add(qs);
        }
    }

    private void readJobs(Sheet sheet, DataFormatter formatter, Parsed parsed) {
        if (sheet == null) {
            return;
        }
        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            String name = cell(row, 0, formatter);
            if (name.isEmpty()) {
                continue;
            }
            Job job = new Job();
            job.rowNo = i + 1;
            job.name = cut(name, 128);
            job.desc = emptyToNull(cell(row, 1, formatter));
            job.status = statusOf(cell(row, 2, formatter));
            job.location = locationOf(cell(row, 3, formatter));
            job.headcount = Math.max(parseInt(cell(row, 4, formatter), 1), 1);
            job.target = cut(blank(cell(row, 5, formatter), "尽快"), 64);
            String priority = cell(row, 6, formatter);
            job.priority = priority.matches("[123]") ? Integer.parseInt(priority) : null;
            job.received = parseDate(cell(row, 7, formatter));
            if (job.received == null) {
                job.received = LocalDate.of(2026, 1, 1);
            }
            job.onboard = parseDate(cell(row, 10, formatter));
            job.dayNote = emptyToNull(cell(row, 11, formatter));
            job.weekNote = emptyToNull(cell(row, 12, formatter));
            job.owners = ownersOf(name);
            parsed.jobs.add(job);
        }
    }

    private void readApps(Sheet sheet, DataFormatter formatter, Parsed parsed) {
        if (sheet == null) {
            return;
        }
        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            String name = cell(row, 0, formatter);
            String job = cell(row, 1, formatter);
            if (name.isEmpty() || job.isEmpty()) {
                continue;
            }
            App app = new App();
            app.rowNo = i + 1;
            app.name = cut(name, 64);
            app.job = cut(job, 128);
            app.channel = channelOf(cell(row, 2, formatter));
            app.fileName = emptyToNull(cut(cell(row, 3, formatter), 512));
            app.aiScore = parseInteger(cell(row, 4, formatter));
            app.aiStrength = emptyToNull(cell(row, 5, formatter));
            app.aiAdvice = emptyToNull(cell(row, 6, formatter));
            app.resumeStatus = "录入".equals(cell(row, 7, formatter)) ? "DRAFT" : "PASS";
            app.screenPass = "通过筛选".equals(cell(row, 8, formatter));
            app.parsedName = unknown(cell(row, 10, formatter));
            app.email = unknown(cell(row, 11, formatter));
            app.phone = unknown(cell(row, 12, formatter));
            app.company = unknown(cut(cell(row, 13, formatter), 128));
            app.school = unknown(cut(cell(row, 14, formatter), 128));
            app.major = unknown(cut(cell(row, 17, formatter), 64));
            app.degree = degreeOf(cell(row, 18, formatter));
            app.jobDesc = emptyToNull(cell(row, 19, formatter));
            app.updatedAt = parseDateTime(cell(row, 20, formatter));
            app.submitter = cut(blank(cell(row, 21, formatter), ""), 64);
            app.submitted = parseDate(cell(row, 22, formatter));
            if (app.submitted == null) {
                app.submitted = LocalDate.of(2026, 7, 1);
            }
            parsed.apps.add(app);
        }
    }

    private void readInterviews(Sheet sheet, DataFormatter formatter, Parsed parsed) {
        if (sheet == null) {
            return;
        }
        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            String name = cell(row, 1, formatter);
            String job = cell(row, 2, formatter);
            if (name.isEmpty() || job.isEmpty()) {
                continue;
            }
            Interview interview = new Interview();
            interview.name = cut(name, 64);
            interview.job = cut(job, 128);
            interview.aiAdvice = emptyToNull(cell(row, 4, formatter));
            interview.aiStrength = emptyToNull(cell(row, 5, formatter));
            interview.aiScore = parseInteger(cell(row, 6, formatter));
            interview.email = unknown(cell(row, 7, formatter));
            interview.progress = cell(row, 9, formatter);
            interview.firstName = emptyToNull(cut(cell(row, 10, formatter), 64));
            interview.firstAt = parseDateTime(cell(row, 11, formatter));
            interview.firstComment = emptyToNull(cell(row, 14, formatter));
            interview.secondAt = parseDateTime(cell(row, 15, formatter));
            interview.secondName = emptyToNull(cut(cell(row, 16, formatter), 64));
            interview.secondComment = emptyToNull(cell(row, 17, formatter));
            parsed.interviews.add(interview);
        }
    }

    private void write(Parsed parsed) {
        Map<String, Long> users = new HashMap<>();
        jdbc.query("SELECT user_id, nickname FROM sys_user WHERE is_active = 1", rs -> {
            users.put(rs.getString("nickname"), rs.getLong("user_id"));
        });
        Map<String, Long> aliasUsers = new HashMap<>();
        jdbc.query("SELECT alias, user_id FROM hr_owner_alias WHERE is_active = 1", rs -> {
            long userId = rs.getLong("user_id");
            if (!rs.wasNull()) {
                aliasUsers.put(rs.getString("alias"), userId);
            }
        });

        insertSchools(parsed.schools);
        insertQs(parsed.qs);
        Map<String, SchoolHit> schools = new HashMap<>();
        jdbc.query("SELECT id, school_name, tags, qs_rank_text FROM hr_school WHERE is_active = 1", rs -> {
            schools.put(rs.getString("school_name"), new SchoolHit(rs.getLong("id"), rs.getString("tags"), rs.getString("qs_rank_text")));
        });
        Map<String, Long> qsIds = new HashMap<>();
        jdbc.query("SELECT id, name_zh FROM hr_qs_university WHERE is_active = 1", rs -> {
            qsIds.putIfAbsent(rs.getString("name_zh"), rs.getLong("id"));
        });

        Map<String, List<Long>> jobsByName = new HashMap<>();
        for (Job job : parsed.jobs) {
            jdbc.update("""
                    INSERT INTO hr_requisition (sheet_row_no, job_name, job_desc, status, location_code, headcount, target_text, priority, received_date, onboard_date, create_by, is_active)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'hr-seed', 1)
                    """, job.rowNo, job.name, job.desc, job.status, job.location, job.headcount, job.target, job.priority, job.received, job.onboard);
            Long id = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
            jobsByName.computeIfAbsent(job.name, key -> new ArrayList<>()).add(id);
            int sort = 0;
            for (String owner : job.owners) {
                jdbc.update("""
                        INSERT INTO hr_requisition_owner (requisition_id, alias, user_id, sort_no, create_by, is_active)
                        VALUES (?, ?, ?, ?, 'hr-seed', 1)
                        """, id, owner, aliasUsers.get(owner), sort++);
            }
            insertProgress(id, "DAY", job.dayNote);
            insertProgress(id, "WEEK", job.weekNote);
        }

        Map<String, Long> byEmailJob = new HashMap<>();
        Map<String, Long> byNameJob = new HashMap<>();
        for (App app : parsed.apps) {
            Long candidateId = findCandidate(app);
            if (candidateId == null) {
                jdbc.update("""
                        INSERT INTO hr_candidate (display_name, parsed_name, phone, email, create_by, is_active)
                        VALUES (?, ?, ?, ?, 'hr-seed', 1)
                        """, app.name, app.parsedName, app.phone, app.email);
                candidateId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
            }
            Long requisitionId = pickJob(jobsByName.get(app.job), parsed.jobs, app.job);
            jdbc.update("""
                    INSERT INTO hr_application (requisition_id, candidate_id, channel_code, resume_status, screen_result, submitter_name, submitter_user_id, submitted_at, source_updated_at, current_stage, sheet_row_no, match_key, create_by, is_active)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'ENTERED', ?, ?, 'hr-seed', 1)
                    """, requisitionId, candidateId, app.channel, app.resumeStatus, app.screenPass ? "PASS" : null,
                    app.submitter, users.get(app.submitter), app.submitted, app.updatedAt, app.rowNo, app.email + "|" + app.job);
            Long applicationId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
            app.id = applicationId;
            if (app.email != null) {
                byEmailJob.putIfAbsent(app.email + "|" + app.job, applicationId);
            }
            byNameJob.putIfAbsent(app.name + "|" + app.job, applicationId);
            if (app.fileName != null) {
                Path file = parsed.files.get(app.fileName);
                String ext = app.fileName.contains(".") ? app.fileName.substring(app.fileName.lastIndexOf('.') + 1) : null;
                jdbc.update("""
                        INSERT INTO hr_resume_file (application_id, file_name, storage_path, file_ext, create_by, is_active)
                        VALUES (?, ?, ?, ?, 'hr-seed', 1)
                        """, applicationId, app.fileName, file == null ? null : file.toString(), ext == null ? null : cut(ext, 8));
            }
            SchoolHit school = app.school == null ? null : schools.get(app.school);
            Long qsId = app.school == null ? null : qsIds.get(app.school);
            jdbc.update("""
                    INSERT INTO hr_resume_parse (application_id, parsed_name, email, phone, last_company, school_name_raw, school_id, qs_university_id, major, degree, school_tags, qs_rank, job_desc_snapshot, ai_score, ai_strength_text, ai_interview_advice, create_by, is_active)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'hr-seed', 1)
                    """, applicationId, app.parsedName, app.email, app.phone, app.company, app.school,
                    school == null ? null : school.id, qsId, app.major, app.degree,
                    school == null ? null : school.tags, school == null ? null : school.qs, app.jobDesc,
                    app.aiScore, app.aiStrength, app.aiAdvice);
        }

        int rounds = 0;
        for (Interview interview : parsed.interviews) {
            Long applicationId = interview.email == null ? null : byEmailJob.get(interview.email + "|" + interview.job);
            if (applicationId == null) {
                applicationId = byNameJob.get(interview.name + "|" + interview.job);
            }
            if (applicationId == null) {
                continue;
            }
            if (interview.firstName != null || interview.firstAt != null || interview.firstComment != null) {
                insertRound(applicationId, 1, interview.firstName, users.get(interview.firstName), interview.firstAt, interview.firstComment);
                rounds++;
            }
            if (interview.secondName != null || interview.secondAt != null || interview.secondComment != null) {
                insertRound(applicationId, 2, interview.secondName, users.get(interview.secondName), interview.secondAt, interview.secondComment);
                rounds++;
            }
            if (interview.aiScore != null || interview.aiStrength != null || interview.aiAdvice != null) {
                jdbc.update("""
                        UPDATE hr_resume_parse
                        SET ai_score = COALESCE(ai_score, ?), ai_strength_text = COALESCE(ai_strength_text, ?), ai_interview_advice = COALESCE(ai_interview_advice, ?)
                        WHERE application_id = ?
                        """, interview.aiScore, interview.aiStrength, interview.aiAdvice, applicationId);
            }
            interview.applicationId = applicationId;
        }
        parsed.rounds = rounds;
        for (App app : parsed.apps) {
            Interview interview = parsed.interviews.stream()
                    .filter(item -> app.id != null && app.id.equals(item.applicationId))
                    .findFirst().orElse(null);
            writeStages(app, interview);
        }
    }

    private void writeStages(App app, Interview interview) {
        String progress = interview == null ? "" : interview.progress;
        boolean round1 = interview != null && (interview.firstAt != null || interview.firstName != null);
        boolean round2 = interview != null && interview.secondAt != null;
        LocalDateTime submitted = app.submitted.atStartOfDay();
        event(app.id, "ENTERED", submitted, "RESUME");
        if ("PASS".equals(app.resumeStatus) || app.screenPass) {
            event(app.id, "SCREEN_PASS", submitted, "RESUME");
        }
        if ("一面待定".equals(progress)) {
            event(app.id, "FIRST_PENDING", or(interview.firstAt, submitted), "INTERVIEW");
        }
        if (round1 || "一面".equals(progress) || "面试未通过".equals(progress) || "已入职".equals(progress)) {
            event(app.id, "FIRST_ROUND", or(interview == null ? null : interview.firstAt, submitted), "INTERVIEW");
        }
        if (round2) {
            event(app.id, "SECOND_ROUND", interview.secondAt, "INTERVIEW");
        }
        if ("面试未通过".equals(progress)) {
            event(app.id, "FIRST_FAIL", or(interview.firstAt, submitted), "INTERVIEW");
        }
        if ("候选人拒绝".equals(progress)) {
            event(app.id, "CANDIDATE_REJECT", or(interview == null ? null : interview.firstAt, submitted), "INTERVIEW");
        }
        if ("已入职".equals(progress)) {
            event(app.id, "ONBOARDED", or(interview == null ? null : interview.firstAt, submitted), "INTERVIEW");
            jdbc.update("""
                    INSERT INTO hr_onboard (application_id, onboard_date, source, create_by, is_active)
                    VALUES (?, ?, 'CANDIDATE_STAGE', 'hr-seed', 1)
                    ON DUPLICATE KEY UPDATE is_active = 1
                    """, app.id, interview.firstAt == null ? app.submitted : interview.firstAt.toLocalDate());
        }
        String current = currentStage(progress, round2, app);
        jdbc.update("UPDATE hr_application SET current_stage = ? WHERE id = ?", current, app.id);
    }

    private String currentStage(String progress, boolean round2, App app) {
        if ("已入职".equals(progress)) {
            return "ONBOARDED";
        }
        if ("候选人拒绝".equals(progress)) {
            return "CANDIDATE_REJECT";
        }
        if ("面试未通过".equals(progress)) {
            return "FIRST_FAIL";
        }
        if (round2) {
            return "SECOND_ROUND";
        }
        if ("一面".equals(progress)) {
            return "FIRST_ROUND";
        }
        if ("一面待定".equals(progress)) {
            return "FIRST_PENDING";
        }
        if ("PASS".equals(app.resumeStatus) || app.screenPass) {
            return "SCREEN_PASS";
        }
        return "ENTERED";
    }

    private void event(Long applicationId, String stage, LocalDateTime at, String source) {
        jdbc.update("""
                INSERT INTO hr_stage_event (application_id, stage_code, event_at, source_sheet, create_by, is_active)
                VALUES (?, ?, ?, ?, 'hr-seed', 1)
                ON DUPLICATE KEY UPDATE event_at = VALUES(event_at)
                """, applicationId, stage, at, source);
    }

    private void insertRound(Long applicationId, int roundNo, String name, Long userId, LocalDateTime at, String comment) {
        jdbc.update("""
                INSERT INTO hr_interview_round (application_id, round_no, interviewer_name, interviewer_user_id, interview_at, comment, create_by, is_active)
                VALUES (?, ?, ?, ?, ?, ?, 'hr-seed', 1)
                ON DUPLICATE KEY UPDATE interviewer_name = VALUES(interviewer_name), interview_at = VALUES(interview_at), comment = VALUES(comment)
                """, applicationId, roundNo, name, userId, at, comment);
    }

    private void insertProgress(Long requisitionId, String grain, String content) {
        if (content == null) {
            return;
        }
        jdbc.update("""
                INSERT INTO hr_requisition_progress (requisition_id, grain, content, content_hash, reported_at, create_by, is_active)
                VALUES (?, ?, ?, ?, NOW(), 'hr-seed', 1)
                """, requisitionId, grain, content, sha(grain + content));
    }

    private Long findCandidate(App app) {
        if (app.phone != null) {
            List<Long> ids = jdbc.query("SELECT id FROM hr_candidate WHERE phone = ? AND is_active = 1",
                    (rs, row) -> rs.getLong(1), app.phone);
            if (ids.size() == 1) {
                return ids.get(0);
            }
        }
        return null;
    }

    private Long pickJob(List<Long> ids, List<Job> jobs, String name) {
        if (ids == null || ids.isEmpty()) {
            return null;
        }
        if (ids.size() == 1) {
            return ids.get(0);
        }
        List<Job> matched = jobs.stream().filter(job -> name.equals(job.name)).toList();
        Job chosen = matched.stream().filter(job -> "OPEN".equals(job.status))
                .max(Comparator.comparing(job -> job.received))
                .orElseGet(() -> matched.stream().max(Comparator.comparing(job -> job.received)).orElse(null));
        int index = matched.indexOf(chosen);
        return index >= 0 && index < ids.size() ? ids.get(index) : ids.get(0);
    }

    private void insertSchools(List<School> schools) {
        jdbc.batchUpdate("""
                INSERT INTO hr_school (school_name, intro, school_type, school_code, tags, qs_rank_text, authority, region, edu_level, remark, create_by, is_active)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'hr-seed', 1)
                """, schools, 300, (ps, school) -> {
            ps.setString(1, school.name);
            ps.setString(2, school.intro);
            ps.setString(3, school.type);
            ps.setString(4, school.code);
            ps.setString(5, school.tags);
            ps.setString(6, school.qs);
            ps.setString(7, school.authority);
            ps.setString(8, school.region);
            ps.setString(9, school.level);
            ps.setString(10, school.remark);
        });
    }

    private void insertQs(List<Qs> rows) {
        jdbc.batchUpdate("""
                INSERT INTO hr_qs_university (rank_no, name_zh, name_en, abbr, country, score, create_by, is_active)
                VALUES (?, ?, ?, ?, ?, ?, 'hr-seed', 1)
                """, rows, 300, (ps, qs) -> {
            ps.setInt(1, qs.rank);
            ps.setString(2, qs.nameZh);
            ps.setString(3, qs.nameEn);
            ps.setString(4, qs.abbr);
            ps.setString(5, qs.country);
            ps.setBigDecimal(6, java.math.BigDecimal.valueOf(qs.score));
        });
    }

    private static List<String> ownersOf(String jobName) {
        Matcher matcher = OWNER_GROUP.matcher(jobName);
        if (!matcher.find()) {
            return List.of();
        }
        String[] parts = matcher.group(1).split("&|＆|、|,|，");
        List<String> owners = new ArrayList<>();
        for (String part : parts) {
            String alias = part.trim();
            if (!alias.isEmpty()) {
                owners.add(alias);
            }
        }
        return owners;
    }

    private static String statusOf(String text) {
        return switch (text) {
            case "已完成" -> "DONE";
            case "停止招聘" -> "STOPPED";
            case "暂缓" -> "PAUSED";
            default -> "OPEN";
        };
    }

    private static String locationOf(String text) {
        if (text.contains("新疆")) {
            return "XJ";
        }
        return "SH";
    }

    private static String channelOf(String text) {
        if (text.contains("猎聘")) {
            return "LIEPIN";
        }
        if (text.contains("猎头")) {
            return "HEADHUNTER";
        }
        if (text.contains("BOSS")) {
            return "BOSS";
        }
        return null;
    }

    private static String degreeOf(String text) {
        return switch (text) {
            case "本科" -> "BACHELOR";
            case "大专" -> "COLLEGE";
            case "硕士" -> "MASTER";
            default -> "UNKNOWN";
        };
    }

    private static String unknown(String text) {
        if (text == null || text.isBlank() || "未知".equals(text.trim())) {
            return null;
        }
        return text.trim();
    }

    private static String cell(Row row, int index, DataFormatter formatter) {
        if (row == null) {
            return "";
        }
        Cell cell = row.getCell(index);
        if (cell == null) {
            return "";
        }
        return formatter.formatCellValue(cell).replace('\u00A0', ' ').trim();
    }

    private static String cut(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max);
    }

    private static String blank(String text, String fallback) {
        return text == null || text.isBlank() ? fallback : text;
    }

    private static String emptyToNull(String text) {
        return text == null || text.isBlank() ? null : text;
    }

    private static int parseInt(String text, int fallback) {
        try {
            return Integer.parseInt(text.replace(".0", "").trim());
        } catch (Exception ex) {
            return fallback;
        }
    }

    private static Integer parseInteger(String text) {
        try {
            return Integer.parseInt(text.replace(".0", "").trim());
        } catch (Exception ex) {
            return null;
        }
    }

    private static double parseScore(String text) {
        try {
            return Double.parseDouble(text.trim());
        } catch (Exception ex) {
            return 0;
        }
    }

    private static LocalDate parseDate(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String value = text.length() >= 10 ? text.substring(0, 10) : text;
        for (DateTimeFormatter formatter : DATES) {
            try {
                return LocalDate.parse(value, formatter);
            } catch (DateTimeParseException ignored) {
                // try next
            }
        }
        return null;
    }

    private static LocalDateTime parseDateTime(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        for (DateTimeFormatter formatter : DATE_TIMES) {
            try {
                return LocalDateTime.parse(text, formatter);
            } catch (DateTimeParseException ignored) {
                // try next
            }
        }
        LocalDate date = parseDate(text);
        return date == null ? null : date.atStartOfDay();
    }

    private static LocalDateTime or(LocalDateTime value, LocalDateTime fallback) {
        return value == null ? fallback : value;
    }

    private static String sha(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes()));
        } catch (Exception ex) {
            return Integer.toHexString(text.hashCode());
        }
    }

    private static final class Parsed {
        private final List<School> schools = new ArrayList<>();
        private final List<Qs> qs = new ArrayList<>();
        private final List<Job> jobs = new ArrayList<>();
        private final List<App> apps = new ArrayList<>();
        private final List<Interview> interviews = new ArrayList<>();
        private Map<String, Path> files = Map.of();
        private int rounds;
    }

    private static final class School {
        private String name;
        private String intro;
        private String type;
        private String code;
        private String tags;
        private String qs;
        private String authority;
        private String region;
        private String level;
        private String remark;
    }

    private record SchoolHit(long id, String tags, String qs) {
    }

    private static final class Qs {
        private int rank;
        private String nameZh;
        private String nameEn;
        private String abbr;
        private String country;
        private double score;
    }

    private static final class Job {
        private int rowNo;
        private String name;
        private String desc;
        private String status;
        private String location;
        private int headcount;
        private String target;
        private Integer priority;
        private LocalDate received;
        private LocalDate onboard;
        private String dayNote;
        private String weekNote;
        private List<String> owners = List.of();
    }

    private static final class App {
        private Long id;
        private int rowNo;
        private String name;
        private String job;
        private String channel;
        private String fileName;
        private Integer aiScore;
        private String aiStrength;
        private String aiAdvice;
        private String resumeStatus;
        private boolean screenPass;
        private String parsedName;
        private String email;
        private String phone;
        private String company;
        private String school;
        private String major;
        private String degree;
        private String jobDesc;
        private LocalDateTime updatedAt;
        private String submitter;
        private LocalDate submitted;
    }

    private static final class Interview {
        private Long applicationId;
        private String name;
        private String job;
        private String email;
        private String progress;
        private String firstName;
        private LocalDateTime firstAt;
        private String firstComment;
        private String secondName;
        private LocalDateTime secondAt;
        private String secondComment;
        private Integer aiScore;
        private String aiStrength;
        private String aiAdvice;
    }
}
