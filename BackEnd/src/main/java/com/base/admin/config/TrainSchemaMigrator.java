package com.base.admin.config;

import com.base.admin.util.TrainQaAnswerSanitizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 收银操作培训：文档 + 版本表、管理菜单。
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 35)
@RequiredArgsConstructor
public class TrainSchemaMigrator implements ApplicationRunner {

    private final JdbcTemplate jdbc;

    @Override
    public void run(ApplicationArguments args) {
        ensureTables();
        seedMenus();
        restoreFalseCodeQuarantine();
        quarantineCodeAnswers();
        log.info("收银培训表结构与菜单已同步");
    }

    private void ensureTables() {
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS train_doc (
                  id BIGINT PRIMARY KEY AUTO_INCREMENT,
                  title VARCHAR(128) NOT NULL COMMENT '文档标题',
                  category VARCHAR(64) NOT NULL DEFAULT 'cashier' COMMENT '分类 cashier=收银操作',
                  description VARCHAR(512) NULL,
                  latest_version_id BIGINT NULL,
                  status TINYINT NOT NULL DEFAULT 0 COMMENT '0正常 1停用',
                  create_by VARCHAR(64) NULL,
                  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  is_active TINYINT NOT NULL DEFAULT 1,
                  KEY idx_train_doc_cat (category, is_active)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='培训文档'
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS train_doc_version (
                  id BIGINT PRIMARY KEY AUTO_INCREMENT,
                  doc_id BIGINT NOT NULL,
                  version_no INT NOT NULL COMMENT '从1递增',
                  version_label VARCHAR(64) NULL COMMENT '展示用版本号',
                  file_name VARCHAR(255) NOT NULL,
                  file_path VARCHAR(512) NOT NULL,
                  content_text LONGTEXT NULL COMMENT '抽取文本供AI检索',
                  content_html LONGTEXT NULL COMMENT '预览 HTML',
                  file_size BIGINT NULL,
                  remark VARCHAR(512) NULL,
                  create_by VARCHAR(64) NULL,
                  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                  is_active TINYINT NOT NULL DEFAULT 1,
                  UNIQUE KEY uk_train_doc_ver (doc_id, version_no),
                  KEY idx_train_doc_ver_doc (doc_id, is_active)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='培训文档版本'
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS train_qa_calibrate (
                  id BIGINT PRIMARY KEY AUTO_INCREMENT,
                  doc_id BIGINT NOT NULL COMMENT '关联培训文档',
                  version_id BIGINT NOT NULL COMMENT '关联文档版本；换版后旧记录不再优先命中',
                  category VARCHAR(64) NOT NULL DEFAULT 'cashier',
                  user_question VARCHAR(512) NOT NULL COMMENT '用户原问/标准问法',
                  intent_norm VARCHAR(512) NOT NULL COMMENT '归一化意图键',
                  aliases_json TEXT NULL COMMENT '同义问法 JSON 数组',
                  topic_title VARCHAR(512) NULL COMMENT '文档话题标题',
                  answer_text MEDIUMTEXT NULL COMMENT '答法纯文本',
                  answer_html MEDIUMTEXT NULL COMMENT '答法图文 HTML',
                  images_json TEXT NULL COMMENT '配图 URL JSON 数组',
                  status VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT 'APPROVED准 REJECTED不准 PENDING待审 STALE过期',
                  hit_count INT NOT NULL DEFAULT 0 COMMENT '校准命中次数',
                  source VARCHAR(32) NULL COMMENT 'ASSISTANT_MARK/ADMIN_EDIT/RE_RECOGNIZE',
                  remark VARCHAR(512) NULL,
                  handle_status VARCHAR(32) NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN未处理 INTENT_BAD用户意图不准 IGNORE忽略 DOC_MISSING培训文档缺失',
                  handle_by VARCHAR(64) NULL COMMENT '处理人',
                  handle_time DATETIME NULL COMMENT '处理时间',
                  create_by VARCHAR(64) NULL,
                  update_by VARCHAR(64) NULL,
                  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  is_active TINYINT NOT NULL DEFAULT 1,
                  KEY idx_train_qa_doc_ver (doc_id, version_id, status, is_active),
                  KEY idx_train_qa_intent (intent_norm, version_id, status, is_active),
                  KEY idx_train_qa_handle (handle_status, is_active)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='培训答疑人工校准库'
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS train_qa_miss (
                  id BIGINT PRIMARY KEY AUTO_INCREMENT,
                  category VARCHAR(64) NOT NULL DEFAULT 'cashier',
                  question VARCHAR(512) NOT NULL COMMENT '用户原问',
                  intent_tokens VARCHAR(512) NULL COMMENT '分词意图',
                  user_id BIGINT NULL COMMENT '提问人用户ID',
                  username VARCHAR(64) NULL COMMENT '登录账号',
                  nickname VARCHAR(64) NULL COMMENT '提问人姓名',
                  status VARCHAR(16) NOT NULL DEFAULT 'NOT_FOUND' COMMENT 'NEED_OLD/NOT_FOUND',
                  doc_id BIGINT NULL,
                  version_id BIGINT NULL,
                  handle_status VARCHAR(32) NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN未处理 INTENT_BAD用户意图不准 IGNORE忽略 DOC_MISSING培训文档缺失',
                  handle_by VARCHAR(64) NULL COMMENT '处理人',
                  handle_time DATETIME NULL COMMENT '处理时间',
                  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                  is_active TINYINT NOT NULL DEFAULT 1,
                  KEY idx_train_qa_miss_time (create_time),
                  KEY idx_train_qa_miss_user (user_id, create_time),
                  KEY idx_train_qa_miss_handle (handle_status, is_active)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='培训答疑未命中提问'
                """);
        ensureColumn("train_qa_calibrate", "handle_status", """
                ALTER TABLE train_qa_calibrate
                  ADD COLUMN handle_status VARCHAR(32) NOT NULL DEFAULT 'OPEN'
                    COMMENT 'OPEN未处理 INTENT_BAD用户意图不准 IGNORE忽略 DOC_MISSING培训文档缺失' AFTER remark,
                  ADD COLUMN handle_by VARCHAR(64) NULL COMMENT '处理人' AFTER handle_status,
                  ADD COLUMN handle_time DATETIME NULL COMMENT '处理时间' AFTER handle_by,
                  ADD KEY idx_train_qa_handle (handle_status, is_active)
                """);
        ensureColumn("train_qa_miss", "handle_status", """
                ALTER TABLE train_qa_miss
                  ADD COLUMN handle_status VARCHAR(32) NOT NULL DEFAULT 'OPEN'
                    COMMENT 'OPEN未处理 INTENT_BAD用户意图不准 IGNORE忽略 DOC_MISSING培训文档缺失' AFTER version_id,
                  ADD COLUMN handle_by VARCHAR(64) NULL COMMENT '处理人' AFTER handle_status,
                  ADD COLUMN handle_time DATETIME NULL COMMENT '处理时间' AFTER handle_by,
                  ADD KEY idx_train_qa_miss_handle (handle_status, is_active)
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS train_qa_item (
                  id BIGINT PRIMARY KEY AUTO_INCREMENT,
                  doc_id BIGINT NOT NULL,
                  version_id BIGINT NOT NULL,
                  category VARCHAR(64) NOT NULL DEFAULT 'cashier',
                  sort_no INT NOT NULL DEFAULT 0,
                  question VARCHAR(512) NOT NULL COMMENT '题干',
                  aliases_json TEXT NULL COMMENT '同义问法 JSON 数组',
                  answer_text MEDIUMTEXT NULL,
                  answer_html MEDIUMTEXT NULL,
                  images_json TEXT NULL,
                  embedding MEDIUMBLOB NULL COMMENT 'L2 归一后的题干向量',
                  embed_model VARCHAR(64) NULL,
                  content_hash CHAR(64) NULL,
                  is_active TINYINT NOT NULL DEFAULT 1,
                  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  KEY idx_train_qa_item_ver (version_id, is_active),
                  KEY idx_train_qa_item_doc (doc_id, is_active)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='培训文档拆出的问答'
                """);
        ensureColumn("train_qa_item", "file_id", """
                ALTER TABLE train_qa_item
                  ADD COLUMN file_id BIGINT NULL COMMENT '所属收银文档文件' AFTER version_id,
                  ADD KEY idx_train_qa_item_file (file_id, is_active)
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS train_doc_file (
                  id BIGINT PRIMARY KEY AUTO_INCREMENT,
                  doc_id BIGINT NOT NULL,
                  version_id BIGINT NOT NULL COMMENT '所属版本',
                  title VARCHAR(128) NULL COMMENT '收银文档名称',
                  file_name VARCHAR(255) NOT NULL,
                  file_path VARCHAR(512) NOT NULL,
                  file_size BIGINT NULL,
                  sort_no INT NOT NULL DEFAULT 0,
                  remark VARCHAR(512) NULL,
                  create_by VARCHAR(64) NULL,
                  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                  is_active TINYINT NOT NULL DEFAULT 1,
                  KEY idx_train_doc_file_ver (version_id, is_active),
                  KEY idx_train_doc_file_doc (doc_id, is_active)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='版本下的收银文档文件'
                """);
        backfillVersionFiles();
    }

    /** 已有版本各有一份原文件，补成「版本 → 收银文档」；题目挂到这份文档上。 */
    private void backfillVersionFiles() {
        jdbc.update("""
                INSERT INTO train_doc_file (doc_id, version_id, title, file_name, file_path, file_size, sort_no, create_by, is_active)
                SELECT v.doc_id, v.id, v.file_name, v.file_name, v.file_path, v.file_size, 1, v.create_by, 1
                FROM train_doc_version v
                WHERE v.is_active = 1
                  AND NOT EXISTS (
                    SELECT 1 FROM train_doc_file f WHERE f.version_id = v.id AND f.is_active = 1
                  )
                """);
        jdbc.update("""
                UPDATE train_qa_item i
                JOIN train_doc_file f ON f.version_id = i.version_id AND f.is_active = 1 AND f.sort_no = 1
                SET i.file_id = f.id
                WHERE i.is_active = 1 AND i.file_id IS NULL
                """);
    }

    private void ensureColumn(String table, String column, String ddl) {
        Integer cnt = jdbc.queryForObject("""
                SELECT COUNT(1) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?
                """, Integer.class, table, column);
        if (cnt == null || cnt == 0) {
            jdbc.execute(ddl);
        }
    }

    private void seedMenus() {
        menu(300, "收银培训", 0, 8, "/train", "", "M", "", "ReadOutlined", "收银操作培训文档与答疑");
        menu(301, "培训文档", 300, 1, "train/doc", "train/doc/index", "C", "train:doc:list", "FileTextOutlined", "培训文档上传与版本管理");
        menu(302, "文档新增", 301, 1, "", "", "F", "train:doc:add", "#", "");
        menu(303, "文档编辑", 301, 2, "", "", "F", "train:doc:edit", "#", "");
        menu(304, "版本上传", 301, 3, "", "", "F", "train:doc:upload", "#", "");
        menu(305, "文档删除", 301, 4, "", "", "F", "train:doc:delete", "#", "");
        // 320/321：310 是文章生成知识库，311 是审稿知识库，不能占用
        menu(320, "题目", 300, 2, "train/item", "train/item/index", "C", "train:doc:list", "ProfileOutlined", "版本下的收银文档拆出的问答");
        menu(321, "题目编辑", 320, 1, "", "", "F", "train:doc:edit", "#", "");
        menu(306, "答疑校准", 300, 3, "train/qa", "train/qa/index", "C", "train:qa:list", "CheckCircleOutlined", "人工校准准/不准答案");
        menu(307, "校准编辑", 306, 1, "", "", "F", "train:qa:edit", "#", "");
        menu(308, "重新识别", 306, 2, "", "", "F", "train:qa:recognize", "#", "");
        menu(309, "未命中提问", 300, 4, "train/miss", "train/miss/index", "C", "train:qa:list", "QuestionCircleOutlined", "没有找到答案的用户提问");

        Long admin = roleId("admin");
        if (admin != null) {
            grant(admin, 300, 301, 302, 303, 304, 305, 306, 307, 308, 309, 320, 321);
        }
        Long hrAdmin = roleId("hr_admin");
        if (hrAdmin != null) {
            grant(hrAdmin, 300, 301, 302, 303, 304, 305, 306, 307, 308, 309, 320, 321);
        }
    }

    /** 上一版卫士过严，把手册正文误标为代码：中文操作说明恢复为「准」。 */
    private void restoreFalseCodeQuarantine() {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id, LEFT(IFNULL(answer_text, ''), 8000) answer_text,
                       LEFT(IFNULL(answer_html, ''), 8000) answer_html, remark
                FROM train_qa_calibrate
                WHERE is_active = 1 AND status = 'REJECTED'
                  AND IFNULL(remark, '') LIKE '%系统拦截：答案疑似程序代码%'
                """);
        int n = 0;
        for (Map<String, Object> row : rows) {
            String text = row.get("answer_text") == null ? "" : String.valueOf(row.get("answer_text"));
            String html = row.get("answer_html") == null ? "" : String.valueOf(row.get("answer_html"));
            if (TrainQaAnswerSanitizer.looksLikeSourceCode(text, html)) {
                continue;
            }
            Number id = (Number) row.get("id");
            if (id == null) {
                continue;
            }
            jdbc.update("""
                    UPDATE train_qa_calibrate
                    SET status = 'APPROVED',
                        remark = REPLACE(IFNULL(remark, ''), '系统拦截：答案疑似程序代码，已禁止下发前台', '已恢复：手册正文误判已纠正')
                    WHERE id = ? AND is_active = 1 AND status = 'REJECTED'
                    """, id.longValue());
            n++;
        }
        if (n > 0) {
            log.info("已恢复误判为代码的答疑校准记录 count={}", n);
        }
    }

    /** 已入库的代码类答案禁止继续作为「准」命中前台。 */
    private void quarantineCodeAnswers() {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id, LEFT(IFNULL(answer_text, ''), 8000) answer_text,
                       LEFT(IFNULL(answer_html, ''), 8000) answer_html
                FROM train_qa_calibrate
                WHERE is_active = 1 AND status IN ('APPROVED', 'PENDING')
                """);
        int n = 0;
        for (Map<String, Object> row : rows) {
            String text = row.get("answer_text") == null ? "" : String.valueOf(row.get("answer_text"));
            String html = row.get("answer_html") == null ? "" : String.valueOf(row.get("answer_html"));
            if (!TrainQaAnswerSanitizer.looksLikeSourceCode(text, html)) {
                continue;
            }
            Number id = (Number) row.get("id");
            if (id == null) {
                continue;
            }
            jdbc.update("""
                    UPDATE train_qa_calibrate
                    SET status = 'REJECTED',
                        remark = LEFT(CONCAT(IFNULL(CONCAT(remark, '；'), ''), '系统拦截：答案疑似程序代码，已禁止下发前台'), 512)
                    WHERE id = ? AND is_active = 1 AND status IN ('APPROVED', 'PENDING')
                    """, id.longValue());
            n++;
        }
        if (n > 0) {
            log.warn("已拦截疑似代码的答疑校准记录 count={}", n);
        }
    }

    private Long roleId(String key) {
        List<Long> ids = jdbc.query("SELECT role_id FROM sys_role WHERE role_key = ? AND is_active = 1 LIMIT 1",
                (rs, i) -> rs.getLong(1), key);
        return ids.isEmpty() ? null : ids.get(0);
    }

    private void menu(int id, String name, int parent, int sort, String path, String component, String type,
                      String perms, String icon, String remark) {
        jdbc.update("""
                INSERT IGNORE INTO sys_menu (menu_id, menu_name, parent_id, sort_order, path, component, menu_type, perms, icon, visible, status, remark, is_active)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0, 0, ?, 1)
                ON DUPLICATE KEY UPDATE
                  menu_name = VALUES(menu_name), parent_id = VALUES(parent_id), sort_order = VALUES(sort_order),
                  path = VALUES(path), component = VALUES(component), menu_type = VALUES(menu_type),
                  perms = VALUES(perms), icon = VALUES(icon), remark = VALUES(remark)
                """, id, name, parent, sort, path, component, type, perms, icon, remark);
    }

    private void grant(long roleId, int... menuIds) {
        for (int menuId : menuIds) {
            jdbc.update("""
                    INSERT IGNORE INTO sys_role_menu (role_id, menu_id, is_active) VALUES (?, ?, 1);""", roleId, menuId);
        }
    }
}
