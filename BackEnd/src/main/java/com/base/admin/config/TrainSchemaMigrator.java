package com.base.admin.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

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
                  create_by VARCHAR(64) NULL,
                  update_by VARCHAR(64) NULL,
                  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  is_active TINYINT NOT NULL DEFAULT 1,
                  KEY idx_train_qa_doc_ver (doc_id, version_id, status, is_active),
                  KEY idx_train_qa_intent (intent_norm, version_id, status, is_active)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='培训答疑人工校准库'
                """);
    }

    private void seedMenus() {
        menu(300, "收银培训", 0, 8, "/train", "", "M", "", "ReadOutlined", "收银操作培训文档与答疑");
        menu(301, "培训文档", 300, 1, "train/doc", "train/doc/index", "C", "train:doc:list", "FileTextOutlined", "培训文档上传与版本管理");
        menu(302, "文档新增", 301, 1, "", "", "F", "train:doc:add", "#", "");
        menu(303, "文档编辑", 301, 2, "", "", "F", "train:doc:edit", "#", "");
        menu(304, "版本上传", 301, 3, "", "", "F", "train:doc:upload", "#", "");
        menu(305, "文档删除", 301, 4, "", "", "F", "train:doc:delete", "#", "");
        menu(306, "答疑校准", 300, 2, "train/qa", "train/qa/index", "C", "train:qa:list", "CheckCircleOutlined", "人工校准准/不准答案");
        menu(307, "校准编辑", 306, 1, "", "", "F", "train:qa:edit", "#", "");
        menu(308, "重新识别", 306, 2, "", "", "F", "train:qa:recognize", "#", "");

        Long admin = roleId("admin");
        if (admin != null) {
            grant(admin, 300, 301, 302, 303, 304, 305, 306, 307, 308);
        }
        Long hrAdmin = roleId("hr_admin");
        if (hrAdmin != null) {
            grant(hrAdmin, 300, 301, 302, 303, 304, 305, 306, 307, 308);
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
                INSERT INTO sys_menu (menu_id, menu_name, parent_id, sort_order, path, component, menu_type, perms, icon, visible, status, remark, is_active)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0, 0, ?, 1)
                ON DUPLICATE KEY UPDATE
                  menu_name = VALUES(menu_name), parent_id = VALUES(parent_id), sort_order = VALUES(sort_order),
                  path = VALUES(path), component = VALUES(component), menu_type = VALUES(menu_type),
                  perms = VALUES(perms), icon = VALUES(icon), remark = VALUES(remark), is_active = 1
                """, id, name, parent, sort, path, component, type, perms, icon, remark);
    }

    private void grant(long roleId, int... menuIds) {
        for (int menuId : menuIds) {
            jdbc.update("""
                    INSERT INTO sys_role_menu (role_id, menu_id, is_active) VALUES (?, ?, 1)
                    ON DUPLICATE KEY UPDATE is_active = 1
                    """, roleId, menuId);
        }
    }
}
