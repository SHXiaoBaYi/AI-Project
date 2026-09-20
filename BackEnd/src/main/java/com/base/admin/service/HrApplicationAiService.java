package com.base.admin.service;

import com.base.admin.domain.vo.GeoAiProviderOptionVO;
import com.base.admin.domain.vo.HrApplicationAiVO;
import com.base.admin.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.base.admin.util.SecurityUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class HrApplicationAiService {

    private final JdbcTemplate jdbc;
    private final AiChatService aiChatService;
    private final SysAiProviderService aiProviderService;
    private final ObjectMapper objectMapper;

    public List<GeoAiProviderOptionVO> providers() {
        return aiProviderService.listReadyOptions().stream()
                .filter(item -> item.isAvailable() && !"local".equalsIgnoreCase(item.getProvider()))
                .toList();
    }

    public List<HrApplicationAiVO> list(Long applicationId) {
        return jdbc.query("""
                SELECT id, application_id, requisition_id, provider, provider_name, model_name, score, pros_cons, interview_advice, create_time
                FROM hr_application_ai
                WHERE application_id = ? AND is_active = 1
                ORDER BY id DESC
                """, (rs, row) -> {
            HrApplicationAiVO vo = new HrApplicationAiVO();
            vo.setId(rs.getLong("id"));
            vo.setApplicationId(rs.getLong("application_id"));
            vo.setRequisitionId(rs.getObject("requisition_id") == null ? null : rs.getLong("requisition_id"));
            vo.setProvider(rs.getString("provider"));
            vo.setProviderName(rs.getString("provider_name"));
            vo.setModelName(rs.getString("model_name"));
            vo.setScore(rs.getInt("score"));
            vo.setProsCons(rs.getString("pros_cons"));
            vo.setInterviewAdvice(rs.getString("interview_advice"));
            vo.setCreateTime(rs.getTimestamp("create_time").toLocalDateTime());
            return vo;
        }, applicationId);
    }

    @Transactional
    public HrApplicationAiVO generate(Long applicationId, String provider) {
        GeoAiProviderOptionVO option = providers().stream()
                .filter(item -> item.getProvider().equalsIgnoreCase(provider))
                .findFirst()
                .orElseThrow(() -> new BusinessException("这个 AI 模型还没配置，请到「系统管理 → AI模型配置」中填写"));
        Context context = loadContext(applicationId);
        String system = """
                你是招聘助手。请根据候选人信息和招聘需求，评估这个人适不适合这个岗位。
                只输出一个 JSON 对象，不要 markdown，不要解释。格式：
                {"score":85,"prosCons":"优势：...\\n劣势：...","interviewAdvice":"..."}
                score 是 0 到 100 的整数。prosCons 写清优势和劣势。interviewAdvice 写面试时建议追问的点。
                信息不足时降低分数，并在优劣势里说明缺了什么，不要编造简历里没有的经历。
                """;
        String content = aiChatService.chat(system, context.prompt(), option.getProvider());
        Parsed parsed = parse(content);
        jdbc.update("""
                INSERT INTO hr_application_ai (application_id, requisition_id, provider, provider_name, model_name, score, pros_cons, interview_advice, create_by, is_active)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 1)
                """, applicationId, context.requisitionId(), option.getProvider(), option.getLabel(),
                blank(option.getModel()), parsed.score(), parsed.prosCons(), parsed.advice(), SecurityUtils.getCurrentUsername());
        Long id = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        return list(applicationId).stream().filter(item -> item.getId().equals(id)).findFirst()
                .orElseThrow(() -> new BusinessException("分析已生成，但读取失败"));
    }

    private Context loadContext(Long applicationId) {
        return jdbc.query("""
                SELECT a.requisition_id, c.display_name, c.phone, c.email, COALESCE(r.job_name, '') job_name,
                       COALESCE(r.job_desc, '') job_desc, COALESCE(r.location_code, '') location_code,
                       COALESCE(p.school_name_raw, '') school_name, COALESCE(p.major, '') major, COALESCE(p.degree, '') degree,
                       COALESCE(p.last_company, '') last_company, COALESCE(p.school_tags, '') school_tags,
                       COALESCE(f.file_name, '') file_name
                FROM hr_application a
                JOIN hr_candidate c ON c.id = a.candidate_id
                LEFT JOIN hr_requisition r ON r.id = a.requisition_id
                LEFT JOIN hr_resume_parse p ON p.application_id = a.id AND p.is_active = 1
                LEFT JOIN hr_resume_file f ON f.application_id = a.id AND f.is_active = 1
                WHERE a.id = ? AND a.is_active = 1
                """, rs -> {
            if (!rs.next()) {
                throw new BusinessException("候选人不存在");
            }
            if (rs.getObject("requisition_id") == null) {
                throw new BusinessException("候选人还没有对应的招聘需求，不能打分");
            }
            String prompt = "候选人：" + rs.getString("display_name")
                    + "\n电话：" + blank(rs.getString("phone"))
                    + "\n邮箱：" + blank(rs.getString("email"))
                    + "\n岗位：" + rs.getString("job_name")
                    + "\n工作地：" + ("XJ".equals(rs.getString("location_code")) ? "新疆" : "上海")
                    + "\n岗位职责：" + blank(rs.getString("job_desc"))
                    + "\n学校：" + blank(rs.getString("school_name"))
                    + "\n专业：" + blank(rs.getString("major"))
                    + "\n学历：" + blank(rs.getString("degree"))
                    + "\n院校标签：" + blank(rs.getString("school_tags"))
                    + "\n上家公司：" + blank(rs.getString("last_company"))
                    + "\n简历文件名：" + blank(rs.getString("file_name"));
            return new Context(rs.getLong("requisition_id"), prompt);
        }, applicationId);
    }

    private Parsed parse(String content) {
        String text = content == null ? "" : content.trim();
        if (text.startsWith("```")) {
            text = text.replaceFirst("^```json", "").replaceFirst("^```", "");
            int fence = text.lastIndexOf("```");
            if (fence >= 0) {
                text = text.substring(0, fence);
            }
            text = text.trim();
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new BusinessException("AI 没有按约定返回打分结果");
        }
        try {
            JsonNode node = objectMapper.readTree(text.substring(start, end + 1));
            int score = node.path("score").asInt(-1);
            if (score < 0 || score > 100) {
                throw new BusinessException("AI 打分不在 0 到 100 之间");
            }
            String pros = node.path("prosCons").asText("").trim();
            String advice = node.path("interviewAdvice").asText("").trim();
            if (pros.isEmpty() || advice.isEmpty()) {
                throw new BusinessException("AI 没有写完优劣势或面试建议");
            }
            return new Parsed(score, pros, advice);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException("AI 返回内容无法解析");
        }
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? "未提供" : value.trim();
    }

    private record Context(Long requisitionId, String prompt) {}

    private record Parsed(int score, String prosCons, String advice) {}
}
