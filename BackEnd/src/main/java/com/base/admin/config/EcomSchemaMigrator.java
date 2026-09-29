package com.base.admin.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 35)
@RequiredArgsConstructor
public class EcomSchemaMigrator implements ApplicationRunner {

    private final JdbcTemplate jdbc;

    @Override
    public void run(ApplicationArguments args) {
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS ecom_shop (
                  id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
                  platform      VARCHAR(32)  NOT NULL                COMMENT '平台：jd/tmall/douyin',
                  shop_code     VARCHAR(64)  NOT NULL                COMMENT '平台店铺编码，如京东11623441',
                  shop_name     VARCHAR(128) NOT NULL DEFAULT ''     COMMENT '店铺名称（可从文件名推断）',
                  remark        VARCHAR(500) DEFAULT ''              COMMENT '备注',
                  create_by     VARCHAR(50)  DEFAULT ''              COMMENT '创建者',
                  create_time   DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                  update_by     VARCHAR(50)  DEFAULT ''              COMMENT '更新者',
                  update_time   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                  is_active     TINYINT      NOT NULL DEFAULT 1      COMMENT '是否有效',
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_ecom_shop (platform, shop_code)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='电商店铺（仅导入自动建店）'
                """);

        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS ecom_import_batch (
                  id              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
                  platform        VARCHAR(32)   NOT NULL                COMMENT '平台',
                  shop_id         BIGINT        DEFAULT NULL            COMMENT '店铺ID',
                  report_type     VARCHAR(64)   NOT NULL                COMMENT '报表类型',
                  period_start    DATE          DEFAULT NULL            COMMENT '数据起始日',
                  period_end      DATE          DEFAULT NULL            COMMENT '数据结束日',
                  file_name       VARCHAR(512)  NOT NULL DEFAULT ''     COMMENT '原始文件名',
                  file_hash       VARCHAR(64)   DEFAULT ''              COMMENT '文件摘要',
                  import_mode     VARCHAR(32)   NOT NULL DEFAULT 'overwrite' COMMENT 'overwrite/skip_locked/force_archive',
                  status          VARCHAR(32)   NOT NULL DEFAULT 'success' COMMENT 'success/failed/partial',
                  total_rows      INT           NOT NULL DEFAULT 0      COMMENT '总行数',
                  success_rows    INT           NOT NULL DEFAULT 0      COMMENT '成功行数',
                  skipped_rows    INT           NOT NULL DEFAULT 0      COMMENT '跳过锁行数',
                  fail_rows       INT           NOT NULL DEFAULT 0      COMMENT '失败行数',
                  skipped_detail  MEDIUMTEXT                            COMMENT '跳过锁行明细JSON',
                  message         VARCHAR(1000) DEFAULT ''              COMMENT '结果说明',
                  create_by       VARCHAR(50)   DEFAULT ''              COMMENT '导入人',
                  create_time     DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                  is_active       TINYINT       NOT NULL DEFAULT 1      COMMENT '是否有效',
                  PRIMARY KEY (id),
                  KEY idx_ecom_batch_shop (shop_id, report_type, create_time)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='电商导入批次'
                """);

        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS ecom_stat_period (
                  id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
                  platform      VARCHAR(32)  NOT NULL                COMMENT '平台',
                  shop_id       BIGINT       NOT NULL                COMMENT '店铺ID',
                  period_type   VARCHAR(16)  NOT NULL                COMMENT 'week/month/year',
                  period_key    VARCHAR(32)  NOT NULL                COMMENT '如 2026-W39',
                  period_label  VARCHAR(64)  NOT NULL DEFAULT ''     COMMENT '展示名',
                  period_start  DATE         NOT NULL                COMMENT '周期开始',
                  period_end    DATE         NOT NULL                COMMENT '周期结束',
                  archived_by   VARCHAR(50)  DEFAULT ''              COMMENT '归档人',
                  archived_at   DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '归档时间',
                  is_active     TINYINT      NOT NULL DEFAULT 1      COMMENT '是否有效',
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_ecom_stat_period (platform, shop_id, period_type, period_key)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='电商已归档统计周期（手工归档）'
                """);

        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS ecom_fact_shop_day (
                  id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
                  platform        VARCHAR(32)  NOT NULL                COMMENT '平台',
                  shop_id         BIGINT       NOT NULL                COMMENT '店铺',
                  report_type     VARCHAR(64)  NOT NULL                COMMENT '报表类型',
                  biz_date        DATE         NOT NULL                COMMENT '业务日',
                  metrics_json    JSON         NOT NULL                COMMENT '主指标JSON（不含对比/同比）',
                  stat_locked     TINYINT      NOT NULL DEFAULT 0      COMMENT '1=已归档锁定',
                  import_batch_id BIGINT       DEFAULT NULL            COMMENT '最近导入批次',
                  create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                  update_time     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                  is_active       TINYINT      NOT NULL DEFAULT 1      COMMENT '是否有效',
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_ecom_shop_day (platform, shop_id, report_type, biz_date),
                  KEY idx_ecom_shop_day_lock (shop_id, biz_date, stat_locked)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='电商店×日概况类事实（交易/商品/流量/用户）'
                """);

        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS ecom_fact_traffic_source (
                  id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
                  platform        VARCHAR(32)  NOT NULL                COMMENT '平台',
                  shop_id         BIGINT       NOT NULL                COMMENT '店铺',
                  biz_date        DATE         NOT NULL                COMMENT '业务日',
                  channel_l1      VARCHAR(128) NOT NULL DEFAULT ''     COMMENT '一级渠道',
                  channel_l2      VARCHAR(128) NOT NULL DEFAULT ''     COMMENT '二级渠道',
                  channel_l3      VARCHAR(128) NOT NULL DEFAULT ''     COMMENT '三级渠道',
                  channel_l4      VARCHAR(128) NOT NULL DEFAULT ''     COMMENT '四级渠道',
                  metrics_json    JSON         NOT NULL                COMMENT '主指标JSON',
                  stat_locked     TINYINT      NOT NULL DEFAULT 0      COMMENT '1=已归档锁定',
                  import_batch_id BIGINT       DEFAULT NULL            COMMENT '最近导入批次',
                  create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                  update_time     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                  is_active       TINYINT      NOT NULL DEFAULT 1      COMMENT '是否有效',
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_ecom_traffic_src (shop_id, biz_date, channel_l1, channel_l2, channel_l3, channel_l4),
                  KEY idx_ecom_traffic_src_date (shop_id, biz_date)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='电商流量来源事实'
                """);

        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS ecom_fact_spu_day (
                  id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
                  platform        VARCHAR(32)  NOT NULL                COMMENT '平台',
                  shop_id         BIGINT       NOT NULL                COMMENT '店铺',
                  biz_date        DATE         NOT NULL                COMMENT '业务日',
                  spu             VARCHAR(64)  NOT NULL DEFAULT ''     COMMENT 'SPU',
                  spu_name        VARCHAR(256) NOT NULL DEFAULT ''     COMMENT 'SPU名称',
                  cate_l1         VARCHAR(128) DEFAULT ''              COMMENT '一级类目',
                  cate_l2         VARCHAR(128) DEFAULT ''              COMMENT '二级类目',
                  cate_l3         VARCHAR(128) DEFAULT ''              COMMENT '三级类目',
                  goods_no        VARCHAR(128) DEFAULT ''              COMMENT '货号',
                  metrics_json    JSON         NOT NULL                COMMENT '主指标JSON',
                  stat_locked     TINYINT      NOT NULL DEFAULT 0      COMMENT '1=已归档锁定',
                  import_batch_id BIGINT       DEFAULT NULL            COMMENT '最近导入批次',
                  create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                  update_time     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                  is_active       TINYINT      NOT NULL DEFAULT 1      COMMENT '是否有效',
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_ecom_spu_day (shop_id, biz_date, spu),
                  KEY idx_ecom_spu_day_date (shop_id, biz_date)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='电商商品明细事实'
                """);

        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS ecom_fact_ad_plan (
                  id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
                  platform        VARCHAR(32)  NOT NULL                COMMENT '平台',
                  shop_id         BIGINT       NOT NULL                COMMENT '店铺',
                  plan_id         VARCHAR(64)  NOT NULL DEFAULT ''     COMMENT '计划ID',
                  plan_name       VARCHAR(256) NOT NULL DEFAULT ''     COMMENT '计划名称',
                  plan_type       VARCHAR(64)  DEFAULT ''              COMMENT '计划类型',
                  period_start    DATE         DEFAULT NULL            COMMENT '文件区间起',
                  period_end      DATE         DEFAULT NULL            COMMENT '文件区间止',
                  metrics_json    JSON         NOT NULL                COMMENT '主指标JSON',
                  stat_locked     TINYINT      NOT NULL DEFAULT 0      COMMENT '1=已归档锁定',
                  import_batch_id BIGINT       DEFAULT NULL            COMMENT '最近导入批次',
                  create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                  update_time     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                  is_active       TINYINT      NOT NULL DEFAULT 1      COMMENT '是否有效',
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_ecom_ad_plan (shop_id, plan_id, period_start, period_end),
                  KEY idx_ecom_ad_plan_shop (shop_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='电商推广计划快照'
                """);

        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS ecom_fact_ad_effect (
                  id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
                  platform        VARCHAR(32)  NOT NULL                COMMENT '平台',
                  shop_id         BIGINT       NOT NULL                COMMENT '店铺',
                  click_date      DATE         NOT NULL                COMMENT '点击日',
                  plan_id         VARCHAR(64)  NOT NULL DEFAULT ''     COMMENT '计划ID',
                  plan_name       VARCHAR(256) NOT NULL DEFAULT ''     COMMENT '推广计划',
                  metrics_json    JSON         NOT NULL                COMMENT '主指标JSON',
                  stat_locked     TINYINT      NOT NULL DEFAULT 0      COMMENT '1=已归档锁定',
                  import_batch_id BIGINT       DEFAULT NULL            COMMENT '最近导入批次',
                  create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                  update_time     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                  is_active       TINYINT      NOT NULL DEFAULT 1      COMMENT '是否有效',
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_ecom_ad_effect (shop_id, click_date, plan_id),
                  KEY idx_ecom_ad_effect_date (shop_id, click_date)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='电商推广效果明细'
                """);

        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS ecom_acl (
                  user_id         BIGINT       NOT NULL                COMMENT '被授权用户',
                  enabled         TINYINT      NOT NULL DEFAULT 1      COMMENT '1=可进入电商运营',
                  all_platforms   TINYINT      NOT NULL DEFAULT 0      COMMENT '1=全部平台',
                  all_shops       TINYINT      NOT NULL DEFAULT 0      COMMENT '1=全部店铺（受平台切片约束）',
                  platforms_json  TEXT                                  COMMENT '平台切片 JSON，如 ["jd","tmall"]',
                  shop_ids_json   TEXT                                  COMMENT '店铺切片 JSON，如 [1,2]',
                  remark          VARCHAR(500) DEFAULT ''              COMMENT '备注',
                  grant_by        VARCHAR(50)  DEFAULT ''              COMMENT '授权人',
                  grant_time      DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '授权时间',
                  update_time     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                  is_active       TINYINT      NOT NULL DEFAULT 1      COMMENT '是否有效',
                  PRIMARY KEY (user_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='电商运营访问授权（Bella管理，可按平台/店铺切片）'
                """);
        ensureEcomAclColumn("all_platforms",
                "ALTER TABLE ecom_acl ADD COLUMN all_platforms TINYINT NOT NULL DEFAULT 0 COMMENT '1=全部平台' AFTER enabled");
        ensureEcomAclColumn("all_shops",
                "ALTER TABLE ecom_acl ADD COLUMN all_shops TINYINT NOT NULL DEFAULT 0 COMMENT '1=全部店铺' AFTER all_platforms");
        ensureEcomAclColumn("platforms_json",
                "ALTER TABLE ecom_acl ADD COLUMN platforms_json TEXT NULL COMMENT '平台切片 JSON' AFTER all_shops");
        ensureEcomAclColumn("shop_ids_json",
                "ALTER TABLE ecom_acl ADD COLUMN shop_ids_json TEXT NULL COMMENT '店铺切片 JSON' AFTER platforms_json");

        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS ecom_board_period_stat (
                  id              BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键',
                  platform        VARCHAR(32)    NOT NULL                COMMENT '平台，all=跨平台汇总行不用',
                  shop_id         BIGINT         NOT NULL DEFAULT 0      COMMENT '店铺，0=平台汇总',
                  period_type     VARCHAR(16)    NOT NULL                COMMENT 'week/month',
                  period_key      VARCHAR(32)    NOT NULL                COMMENT '周期键',
                  period_label    VARCHAR(64)    NOT NULL DEFAULT ''     COMMENT '展示名',
                  period_start    DATE           NOT NULL                COMMENT '开始',
                  period_end      DATE           NOT NULL                COMMENT '结束',
                  gmv             DECIMAL(18,2)  NOT NULL DEFAULT 0     COMMENT '成交金额',
                  order_cnt       DECIMAL(18,2)  NOT NULL DEFAULT 0     COMMENT '成交单量',
                  buyer_cnt       DECIMAL(18,2)  NOT NULL DEFAULT 0     COMMENT '成交客户数',
                  visitor_cnt     DECIMAL(18,2)  NOT NULL DEFAULT 0     COMMENT '店铺访客数',
                  refund_amt      DECIMAL(18,2)  NOT NULL DEFAULT 0     COMMENT '退款金额',
                  metrics_json    JSON                                   COMMENT '扩展指标',
                  locked_at       DATETIME       DEFAULT NULL            COMMENT '落库时间',
                  create_time     DATETIME       DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                  update_time     DATETIME       DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                  is_active       TINYINT        NOT NULL DEFAULT 1      COMMENT '是否有效',
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_ecom_board (platform, shop_id, period_type, period_key)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='电商周/月看板落库'
                """);

        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS ecom_target (
                  id              BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键',
                  platform        VARCHAR(32)    NOT NULL DEFAULT 'all'  COMMENT '平台 all/jd/tmall/douyin',
                  shop_id         BIGINT         NOT NULL DEFAULT 0      COMMENT '店铺，0=平台级',
                  period_type     VARCHAR(16)    NOT NULL                COMMENT 'week/month/year',
                  period_key      VARCHAR(32)    NOT NULL                COMMENT '周期键',
                  period_label    VARCHAR(64)    NOT NULL DEFAULT ''     COMMENT '展示名',
                  target_gmv      DECIMAL(18,2)  NOT NULL DEFAULT 0     COMMENT '成交金额目标',
                  target_order    DECIMAL(18,2)  NOT NULL DEFAULT 0     COMMENT '成交单量目标',
                  remark          VARCHAR(500)   DEFAULT ''              COMMENT '备注',
                  create_by       VARCHAR(50)    DEFAULT ''              COMMENT '创建者',
                  create_time     DATETIME       DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                  update_by       VARCHAR(50)    DEFAULT ''              COMMENT '更新者',
                  update_time     DATETIME       DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                  is_active       TINYINT        NOT NULL DEFAULT 1      COMMENT '是否有效',
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_ecom_target (platform, shop_id, period_type, period_key)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='电商目标'
                """);

        log.info("电商运营表结构已同步");
    }

    private void ensureEcomAclColumn(String column, String ddl) {
        Integer exists = jdbc.queryForObject("""
                SELECT COUNT(1) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ecom_acl' AND COLUMN_NAME = ?
                """, Integer.class, column);
        if (exists != null && exists > 0) {
            return;
        }
        jdbc.execute(ddl);
        log.info("已为 ecom_acl 增加 {} 字段", column);
    }
}
