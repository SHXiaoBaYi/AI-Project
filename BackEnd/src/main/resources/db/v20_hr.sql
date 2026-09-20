CREATE TABLE IF NOT EXISTS hr_department (
  id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  parent_id       BIGINT       NOT NULL DEFAULT 0      COMMENT '父部门，0为根',
  name            VARCHAR(64)  NOT NULL                COMMENT '部门名称，同级唯一',
  ancestors       VARCHAR(512) NOT NULL DEFAULT '0'    COMMENT '根到父节点路径',
  leader_user_id  BIGINT       NULL                    COMMENT '部门负责人',
  sort_order      INT          NOT NULL DEFAULT 0      COMMENT '同级排序',
  status          TINYINT      NOT NULL DEFAULT 0      COMMENT '0正常 1停用',
  create_by       VARCHAR(50)  DEFAULT ''              COMMENT '创建者',
  create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_by       VARCHAR(50)  DEFAULT ''              COMMENT '更新者',
  update_time     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  is_active       TINYINT      NOT NULL DEFAULT 1      COMMENT '1有效 0删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_hr_dept_name (parent_id, name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='招聘部门树';

CREATE TABLE IF NOT EXISTS hr_owner_alias (
  id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  alias        VARCHAR(32)  NOT NULL                COMMENT '岗位括号简称',
  display_name VARCHAR(64)  NOT NULL                COMMENT '展示名',
  user_id      BIGINT       NULL                    COMMENT '系统用户',
  create_by    VARCHAR(50)  DEFAULT ''              COMMENT '创建者',
  create_time  DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_by    VARCHAR(50)  DEFAULT ''              COMMENT '更新者',
  update_time  DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  is_active    TINYINT      NOT NULL DEFAULT 1      COMMENT '1有效 0删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_hr_owner_alias (alias)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='招聘负责人简称';

CREATE TABLE IF NOT EXISTS hr_requisition (
  id                BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
  source_record_id  VARCHAR(64)   NULL                    COMMENT '钉钉记录ID',
  sheet_row_no      INT           NULL                    COMMENT 'Excel行号',
  job_name          VARCHAR(128)  NOT NULL                COMMENT '在招岗位',
  job_desc          MEDIUMTEXT    NULL                    COMMENT '职位描述',
  status            VARCHAR(16)   NOT NULL                COMMENT 'OPEN/DONE/STOPPED/PAUSED',
  location_code     VARCHAR(8)    NOT NULL                COMMENT 'SH/XJ',
  dept_id           BIGINT        NULL                    COMMENT '部门',
  headcount         INT           NOT NULL DEFAULT 1      COMMENT '需求人数',
  target_text       VARCHAR(64)   NOT NULL DEFAULT ''     COMMENT '目标到岗原文',
  priority          TINYINT       NULL                    COMMENT '1/2/3',
  received_date     DATE          NOT NULL                COMMENT '需求接收日',
  onboard_date      DATE          NULL                    COMMENT '需求级入职日',
  create_by         VARCHAR(50)   DEFAULT ''              COMMENT '创建者',
  create_time       DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_by         VARCHAR(50)   DEFAULT ''              COMMENT '更新者',
  update_time       DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  is_active         TINYINT       NOT NULL DEFAULT 1      COMMENT '1有效 0删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_hr_req_source (source_record_id),
  KEY idx_hr_req_received (received_date),
  KEY idx_hr_req_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='招聘需求';

CREATE TABLE IF NOT EXISTS hr_requisition_owner (
  id              BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
  requisition_id  BIGINT      NOT NULL                COMMENT '需求',
  alias           VARCHAR(32) NOT NULL                COMMENT '括号原文',
  user_id         BIGINT      NULL                    COMMENT '系统用户',
  sort_no         INT         NOT NULL DEFAULT 0      COMMENT '顺序',
  create_by       VARCHAR(50) DEFAULT ''              COMMENT '创建者',
  create_time     DATETIME    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_by       VARCHAR(50) DEFAULT ''              COMMENT '更新者',
  update_time     DATETIME    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  is_active       TINYINT     NOT NULL DEFAULT 1      COMMENT '1有效 0删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_hr_req_owner (requisition_id, alias)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='需求负责人';

CREATE TABLE IF NOT EXISTS hr_requisition_progress (
  id              BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
  requisition_id  BIGINT      NOT NULL                COMMENT '需求',
  grain           CHAR(4)     NOT NULL                COMMENT 'DAY/WEEK',
  content         TEXT        NOT NULL                COMMENT '进展原文',
  content_hash    CHAR(64)    NOT NULL                COMMENT '内容摘要',
  reported_at     DATETIME    NOT NULL                COMMENT '记录时间',
  create_by       VARCHAR(50) DEFAULT ''              COMMENT '创建者',
  create_time     DATETIME    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_by       VARCHAR(50) DEFAULT ''              COMMENT '更新者',
  update_time     DATETIME    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  is_active       TINYINT     NOT NULL DEFAULT 1      COMMENT '1有效 0删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_hr_req_progress (requisition_id, grain, content_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='需求进展日志';

CREATE TABLE IF NOT EXISTS hr_candidate (
  id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  display_name  VARCHAR(64)  NOT NULL                COMMENT '表格姓名',
  parsed_name   VARCHAR(64)  NULL                    COMMENT '解析姓名',
  phone         VARCHAR(32)  NULL                    COMMENT '电话',
  email         VARCHAR(128) NULL                    COMMENT '邮箱',
  create_by     VARCHAR(50)  DEFAULT ''              COMMENT '创建者',
  create_time   DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_by     VARCHAR(50)  DEFAULT ''              COMMENT '更新者',
  update_time   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  is_active     TINYINT      NOT NULL DEFAULT 1      COMMENT '1有效 0删除',
  PRIMARY KEY (id),
  KEY idx_hr_candidate_phone (phone),
  KEY idx_hr_candidate_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='候选人';

CREATE TABLE IF NOT EXISTS hr_channel (
  channel_code VARCHAR(32) NOT NULL COMMENT '渠道码',
  channel_name VARCHAR(32) NOT NULL COMMENT '渠道名',
  create_by    VARCHAR(50) DEFAULT '' COMMENT '创建者',
  create_time  DATETIME    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_by    VARCHAR(50) DEFAULT '' COMMENT '更新者',
  update_time  DATETIME    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  is_active    TINYINT     NOT NULL DEFAULT 1 COMMENT '1有效 0删除',
  PRIMARY KEY (channel_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='招聘渠道';

CREATE TABLE IF NOT EXISTS hr_application (
  id                  BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  requisition_id      BIGINT       NULL                    COMMENT '需求',
  candidate_id        BIGINT       NOT NULL                COMMENT '候选人',
  channel_code        VARCHAR(32)  NULL                    COMMENT '渠道',
  resume_status       VARCHAR(16)  NOT NULL                COMMENT 'PASS/DRAFT',
  screen_result       VARCHAR(16)  NULL                    COMMENT 'PASS或空',
  submitter_name      VARCHAR(64)  NOT NULL DEFAULT ''     COMMENT '简历提交人',
  submitter_user_id   BIGINT       NULL                    COMMENT '提交人用户',
  submitted_at        DATE         NOT NULL                COMMENT '创建日期',
  source_updated_at   DATETIME     NULL                    COMMENT '来源更新时间',
  current_stage       VARCHAR(32)  NOT NULL                COMMENT '当前阶段冗余',
  resume_source_id    VARCHAR(64)  NULL                    COMMENT '钉钉简历记录',
  interview_source_id VARCHAR(64)  NULL                    COMMENT '钉钉面试记录',
  sheet_row_no        INT          NULL                    COMMENT '简历行号',
  match_key           VARCHAR(255) NULL                    COMMENT '对齐键',
  create_by           VARCHAR(50)  DEFAULT ''              COMMENT '创建者',
  create_time         DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_by           VARCHAR(50)  DEFAULT ''              COMMENT '更新者',
  update_time         DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  is_active           TINYINT      NOT NULL DEFAULT 1      COMMENT '1有效 0删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_hr_app_resume_source (resume_source_id),
  KEY idx_hr_app_req (requisition_id),
  KEY idx_hr_app_submitted (submitted_at),
  KEY idx_hr_app_stage (current_stage)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='投递';

CREATE TABLE IF NOT EXISTS hr_resume_file (
  id              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
  application_id  BIGINT        NOT NULL                COMMENT '投递',
  file_name       VARCHAR(512)  NOT NULL                COMMENT '简历列原文',
  storage_path    VARCHAR(512)  NULL                    COMMENT '本地路径',
  file_ext        VARCHAR(8)    NULL                    COMMENT '扩展名',
  create_by       VARCHAR(50)   DEFAULT ''              COMMENT '创建者',
  create_time     DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_by       VARCHAR(50)   DEFAULT ''              COMMENT '更新者',
  update_time     DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  is_active       TINYINT       NOT NULL DEFAULT 1      COMMENT '1有效 0删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_hr_resume_file_app (application_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='简历文件';

CREATE TABLE IF NOT EXISTS hr_resume_parse (
  id                   BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
  application_id       BIGINT        NOT NULL                COMMENT '投递',
  parsed_name          VARCHAR(64)   NULL                    COMMENT '解析姓名',
  email                VARCHAR(128)  NULL                    COMMENT '邮箱',
  phone                VARCHAR(32)   NULL                    COMMENT '电话',
  last_company         VARCHAR(128)  NULL                    COMMENT '上家公司',
  school_name_raw      VARCHAR(128)  NULL                    COMMENT '学校原文',
  school_id            BIGINT        NULL                    COMMENT '国内院校',
  qs_university_id     BIGINT        NULL                    COMMENT 'QS院校',
  major                VARCHAR(64)   NULL                    COMMENT '专业',
  degree               VARCHAR(16)   NULL                    COMMENT '学历',
  school_tags          VARCHAR(64)   NULL                    COMMENT '院校标签',
  qs_rank              VARCHAR(32)   NULL                    COMMENT 'QS排名',
  job_desc_snapshot    MEDIUMTEXT    NULL                    COMMENT '投递时岗位描述',
  ai_score             INT           NULL                    COMMENT 'AI打分',
  ai_strength_text     MEDIUMTEXT    NULL                    COMMENT '优劣势',
  ai_interview_advice  MEDIUMTEXT    NULL                    COMMENT '面试建议',
  create_by            VARCHAR(50)   DEFAULT ''              COMMENT '创建者',
  create_time          DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_by            VARCHAR(50)   DEFAULT ''              COMMENT '更新者',
  update_time          DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  is_active            TINYINT       NOT NULL DEFAULT 1      COMMENT '1有效 0删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_hr_resume_parse_app (application_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='简历解析';

CREATE TABLE IF NOT EXISTS hr_reject_reason (
  reason_code VARCHAR(32) NOT NULL COMMENT '原因码',
  reason_name VARCHAR(32) NOT NULL COMMENT '原因名',
  sort_no     INT         NOT NULL DEFAULT 0 COMMENT '排序',
  create_by   VARCHAR(50) DEFAULT '' COMMENT '创建者',
  create_time DATETIME    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_by   VARCHAR(50) DEFAULT '' COMMENT '更新者',
  update_time DATETIME    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  is_active   TINYINT     NOT NULL DEFAULT 1 COMMENT '1有效 0删除',
  PRIMARY KEY (reason_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='淘汰原因';

CREATE TABLE IF NOT EXISTS hr_interview_round (
  id                   BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  application_id       BIGINT       NOT NULL                COMMENT '投递',
  round_no             TINYINT      NOT NULL                COMMENT '1一面 2二面 3终面',
  interviewer_name     VARCHAR(64)  NULL                    COMMENT '面试官姓名',
  interviewer_user_id  BIGINT       NULL                    COMMENT '面试官用户',
  interview_at         DATETIME     NULL                    COMMENT '面试时间',
  comment              TEXT         NULL                    COMMENT '评价原文',
  reject_reason_code   VARCHAR(32)  NULL                    COMMENT '淘汰原因',
  transcript_url       VARCHAR(512) NULL                    COMMENT '听记链接',
  transcript_text      MEDIUMTEXT   NULL                    COMMENT '听记解析',
  create_by            VARCHAR(50)  DEFAULT ''              COMMENT '创建者',
  create_time          DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_by            VARCHAR(50)  DEFAULT ''              COMMENT '更新者',
  update_time          DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  is_active            TINYINT      NOT NULL DEFAULT 1      COMMENT '1有效 0删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_hr_round (application_id, round_no),
  KEY idx_hr_round_at (interview_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='面试轮次';

CREATE TABLE IF NOT EXISTS hr_offer (
  id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  application_id  BIGINT       NOT NULL                COMMENT '投递',
  offered_at      DATETIME     NULL                    COMMENT '发Offer时间',
  status          VARCHAR(16)  NULL                    COMMENT 'ACCEPTED/REJECTED',
  reject_reason   VARCHAR(255) NULL                    COMMENT '拒绝原因',
  create_by       VARCHAR(50)  DEFAULT ''              COMMENT '创建者',
  create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_by       VARCHAR(50)  DEFAULT ''              COMMENT '更新者',
  update_time     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  is_active       TINYINT      NOT NULL DEFAULT 1      COMMENT '1有效 0删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_hr_offer_app (application_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Offer';

CREATE TABLE IF NOT EXISTS hr_onboard (
  id                BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
  application_id    BIGINT      NOT NULL                COMMENT '投递',
  onboard_date      DATE        NULL                    COMMENT '候选人入职日',
  probation_status  VARCHAR(32) NULL                    COMMENT '试用期',
  source            VARCHAR(16) NOT NULL                COMMENT 'CANDIDATE_STAGE',
  create_by         VARCHAR(50) DEFAULT ''              COMMENT '创建者',
  create_time       DATETIME    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_by         VARCHAR(50) DEFAULT ''              COMMENT '更新者',
  update_time       DATETIME    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  is_active         TINYINT     NOT NULL DEFAULT 1      COMMENT '1有效 0删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_hr_onboard_app (application_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='候选人入职';

CREATE TABLE IF NOT EXISTS hr_stage_def (
  stage_code      VARCHAR(32) NOT NULL COMMENT '阶段码',
  stage_name      VARCHAR(32) NOT NULL COMMENT '名称',
  sort_no         INT         NOT NULL COMMENT '漏斗顺序',
  funnel_visible  TINYINT     NOT NULL COMMENT '1出现在漏斗',
  data_ready      TINYINT     NOT NULL COMMENT '0未采集',
  terminal        TINYINT     NOT NULL COMMENT '1终态',
  create_by       VARCHAR(50) DEFAULT '' COMMENT '创建者',
  create_time     DATETIME    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_by       VARCHAR(50) DEFAULT '' COMMENT '更新者',
  update_time     DATETIME    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  is_active       TINYINT     NOT NULL DEFAULT 1 COMMENT '1有效 0删除',
  PRIMARY KEY (stage_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='招聘阶段';

CREATE TABLE IF NOT EXISTS hr_stage_map (
  id           BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
  source_field VARCHAR(64) NOT NULL                COMMENT 'resume_status/progress',
  source_value VARCHAR(64) NOT NULL                COMMENT '原文',
  stage_code   VARCHAR(32) NOT NULL                COMMENT '阶段码',
  create_by    VARCHAR(50) DEFAULT ''              COMMENT '创建者',
  create_time  DATETIME    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_by    VARCHAR(50) DEFAULT ''              COMMENT '更新者',
  update_time  DATETIME    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  is_active    TINYINT     NOT NULL DEFAULT 1      COMMENT '1有效 0删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_hr_stage_map (source_field, source_value)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='阶段映射';

CREATE TABLE IF NOT EXISTS hr_stage_event (
  id              BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
  application_id  BIGINT      NOT NULL                COMMENT '投递',
  stage_code      VARCHAR(32) NOT NULL                COMMENT '阶段',
  event_at        DATETIME    NOT NULL                COMMENT '到达时间',
  source_sheet    VARCHAR(32) NOT NULL                COMMENT 'RESUME/INTERVIEW/INVITE',
  create_by       VARCHAR(50) DEFAULT ''              COMMENT '创建者',
  create_time     DATETIME    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_by       VARCHAR(50) DEFAULT ''              COMMENT '更新者',
  update_time     DATETIME    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  is_active       TINYINT     NOT NULL DEFAULT 1      COMMENT '1有效 0删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_hr_stage_event (application_id, stage_code),
  KEY idx_hr_stage_code (stage_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='阶段事实';

CREATE TABLE IF NOT EXISTS hr_school (
  id            BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
  school_name   VARCHAR(128)  NOT NULL                COMMENT '学校名称',
  intro         MEDIUMTEXT    NULL                    COMMENT '简介',
  school_type   VARCHAR(32)   NOT NULL                COMMENT '类型',
  school_code   VARCHAR(16)   NOT NULL                COMMENT '标识码',
  tags          VARCHAR(64)   NULL                    COMMENT '标签',
  qs_rank_text  VARCHAR(32)   NULL                    COMMENT 'QS原文',
  authority     VARCHAR(64)   NOT NULL DEFAULT ''     COMMENT '主管部门',
  region        VARCHAR(64)   NOT NULL DEFAULT ''     COMMENT '所在地',
  edu_level     VARCHAR(16)   NULL                    COMMENT '办学层次',
  remark        VARCHAR(255)  NULL                    COMMENT '备注',
  create_by     VARCHAR(50)   DEFAULT ''              COMMENT '创建者',
  create_time   DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_by     VARCHAR(50)   DEFAULT ''              COMMENT '更新者',
  update_time   DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  is_active     TINYINT       NOT NULL DEFAULT 1      COMMENT '1有效 0删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_hr_school_name (school_name),
  UNIQUE KEY uk_hr_school_code (school_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='全国高等学校';

CREATE TABLE IF NOT EXISTS hr_qs_university (
  id        BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
  rank_no   INT           NOT NULL                COMMENT '排名',
  name_zh   VARCHAR(128)  NOT NULL                COMMENT '中文名',
  name_en   VARCHAR(256)  NOT NULL                COMMENT '英文名',
  abbr      VARCHAR(64)   NULL                    COMMENT '缩写',
  country   VARCHAR(64)   NOT NULL                COMMENT '国家地区',
  score     DECIMAL(6,1)  NOT NULL                COMMENT '综合得分',
  create_by VARCHAR(50)   DEFAULT ''              COMMENT '创建者',
  create_time DATETIME    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_by VARCHAR(50)   DEFAULT ''              COMMENT '更新者',
  update_time DATETIME    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  is_active TINYINT       NOT NULL DEFAULT 1      COMMENT '1有效 0删除',
  PRIMARY KEY (id),
  KEY idx_hr_qs_name (name_zh)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='QS500';

CREATE TABLE IF NOT EXISTS hr_school_alias (
  id                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  alias_name        VARCHAR(128) NOT NULL                COMMENT '简历叫法',
  school_id         BIGINT       NULL                    COMMENT '国内院校',
  qs_university_id  BIGINT       NULL                    COMMENT '海外院校',
  create_by         VARCHAR(50)  DEFAULT ''              COMMENT '创建者',
  create_time       DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_by         VARCHAR(50)  DEFAULT ''              COMMENT '更新者',
  update_time       DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  is_active         TINYINT      NOT NULL DEFAULT 1      COMMENT '1有效 0删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_hr_school_alias (alias_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学校别名';

CREATE TABLE IF NOT EXISTS hr_user_dingtalk (
  user_id            BIGINT      NOT NULL COMMENT '系统用户',
  dingtalk_user_id   VARCHAR(64) NOT NULL COMMENT '企业userid',
  dingtalk_union_id  VARCHAR(64) NOT NULL COMMENT 'unionId',
  create_by          VARCHAR(50) DEFAULT '' COMMENT '创建者',
  create_time        DATETIME    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_by          VARCHAR(50) DEFAULT '' COMMENT '更新者',
  update_time        DATETIME    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  is_active          TINYINT     NOT NULL DEFAULT 1 COMMENT '1有效 0删除',
  PRIMARY KEY (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户钉钉身份';

CREATE TABLE IF NOT EXISTS hr_interview_invite (
  id                   BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  application_id       BIGINT       NOT NULL                COMMENT '投递',
  round_no             TINYINT      NOT NULL                COMMENT '1一面 2二面 3终面',
  interviewer_user_id  BIGINT       NOT NULL                COMMENT '面试官',
  interview_at         DATETIME     NOT NULL                COMMENT '开始时间',
  duration_min         INT          NOT NULL DEFAULT 60     COMMENT '时长分钟',
  location             VARCHAR(255) NULL                    COMMENT '地点',
  status               VARCHAR(16)  NOT NULL                COMMENT 'SUCCESS/FAILED/CANCELLED/CANCEL_FAILED',
  fail_reason          VARCHAR(255) NULL                    COMMENT '失败原因',
  dingtalk_event_id    VARCHAR(64)  NULL                    COMMENT '日程ID',
  dingtalk_calendar_id VARCHAR(64)  NULL                    COMMENT '日历ID',
  invited_by           BIGINT       NOT NULL                COMMENT '发起人',
  cancelled_by         BIGINT       NULL                    COMMENT '取消人',
  cancelled_at         DATETIME     NULL                    COMMENT '取消时间',
  create_by            VARCHAR(50)  DEFAULT ''              COMMENT '创建者',
  create_time          DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_by            VARCHAR(50)  DEFAULT ''              COMMENT '更新者',
  update_time          DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  is_active            TINYINT      NOT NULL DEFAULT 1      COMMENT '1有效 0删除',
  PRIMARY KEY (id),
  KEY idx_hr_invite_app (application_id, round_no, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='面试邀约';

CREATE TABLE IF NOT EXISTS hr_interview_invite_log (
  id                 BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
  invite_id          BIGINT      NOT NULL                COMMENT '邀约单',
  action             VARCHAR(32) NOT NULL                COMMENT 'CREATE_CALENDAR/CANCEL_CALENDAR',
  result             VARCHAR(16) NOT NULL                COMMENT 'SUCCESS/FAILED',
  operator_id        BIGINT      NOT NULL                COMMENT '操作人',
  dingtalk_event_id  VARCHAR(64) NULL                    COMMENT '日程ID',
  request_body       JSON        NULL                    COMMENT '请求',
  response_body      JSON        NULL                    COMMENT '钉钉返回',
  operated_at        DATETIME    NOT NULL                COMMENT '操作时间',
  create_by          VARCHAR(50) DEFAULT ''              COMMENT '创建者',
  create_time        DATETIME    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_by          VARCHAR(50) DEFAULT ''              COMMENT '更新者',
  update_time        DATETIME    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  is_active          TINYINT     NOT NULL DEFAULT 1      COMMENT '1有效 0删除',
  PRIMARY KEY (id),
  KEY idx_hr_invite_log (invite_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='邀约过程存档';

CREATE TABLE IF NOT EXISTS hr_board_view (
  id          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
  user_id     BIGINT      NOT NULL                COMMENT '用户',
  view_name   VARCHAR(64) NOT NULL                COMMENT '视图名',
  filter_json JSON        NOT NULL                COMMENT '筛选条件',
  is_default  TINYINT     NOT NULL DEFAULT 0      COMMENT '1默认',
  create_by   VARCHAR(50) DEFAULT ''              COMMENT '创建者',
  create_time DATETIME    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_by   VARCHAR(50) DEFAULT ''              COMMENT '更新者',
  update_time DATETIME    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  is_active   TINYINT     NOT NULL DEFAULT 1      COMMENT '1有效 0删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_hr_board_view (user_id, view_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='看板保存视图';

INSERT INTO hr_channel (channel_code, channel_name, is_active) VALUES
('BOSS', 'BOSS', 1),
('LIEPIN', '猎聘', 1),
('HEADHUNTER', '猎头', 1)
ON DUPLICATE KEY UPDATE channel_name = VALUES(channel_name), is_active = 1;

INSERT INTO hr_reject_reason (reason_code, reason_name, sort_no, is_active) VALUES
('SKILL', '能力不符', 1, 1),
('SALARY', '薪资谈不拢', 2, 1),
('CANDIDATE_QUIT', '候选人放弃', 3, 1),
('JOB_PAUSED', '岗位暂停', 4, 1),
('OTHER', '其他', 5, 1)
ON DUPLICATE KEY UPDATE reason_name = VALUES(reason_name), is_active = 1;

INSERT INTO hr_stage_def (stage_code, stage_name, sort_no, funnel_visible, data_ready, terminal, is_active) VALUES
('ENTERED', '已录入', 10, 0, 1, 0, 1),
('SCREEN_PASS', '初筛通过', 20, 1, 1, 0, 1),
('INVITED', '邀约成功', 30, 1, 1, 0, 1),
('SHOW_UP', '到面', 40, 1, 0, 0, 1),
('FIRST_PENDING', '一面待定', 50, 0, 1, 0, 1),
('FIRST_ROUND', '一面', 60, 1, 1, 0, 1),
('FIRST_FAIL', '一面未通过', 70, 0, 1, 1, 1),
('SECOND_ROUND', '复试', 80, 1, 1, 0, 1),
('FINAL', '终面通过', 90, 1, 0, 0, 1),
('SALARY', '薪资沟通中', 91, 1, 1, 0, 1),
('BG_COLLECT', '背调资料收集中', 92, 1, 1, 0, 1),
('BG_CHECK', '背调中', 93, 1, 1, 0, 1),
('MEDICAL', '体检中', 94, 1, 1, 0, 1),
('OFFER_PENDING', '待发offer', 95, 1, 1, 0, 1),
('PENDING_ONBOARD', '待入职', 96, 1, 1, 0, 1),
('OFFER_SENT', '发放Offer', 100, 0, 0, 0, 1),
('OFFER_ACCEPTED', 'Offer接受', 110, 0, 0, 0, 1),
('CANDIDATE_REJECT', '候选人拒绝', 120, 0, 1, 1, 1),
('ONBOARDED', '入职', 130, 1, 1, 1, 1)
ON DUPLICATE KEY UPDATE stage_name = VALUES(stage_name), sort_no = VALUES(sort_no),
  funnel_visible = VALUES(funnel_visible), data_ready = VALUES(data_ready), terminal = VALUES(terminal), is_active = 1;

INSERT INTO hr_stage_map (source_field, source_value, stage_code, is_active) VALUES
('resume_status', '通过', 'SCREEN_PASS', 1),
('resume_status', '录入', 'ENTERED', 1),
('progress', '一面待定', 'FIRST_PENDING', 1),
('progress', '一面', 'FIRST_ROUND', 1),
('progress', '面试未通过', 'FIRST_FAIL', 1),
('progress', '候选人拒绝', 'CANDIDATE_REJECT', 1),
('progress', '已入职', 'ONBOARDED', 1)
ON DUPLICATE KEY UPDATE stage_code = VALUES(stage_code), is_active = 1;

CREATE TABLE IF NOT EXISTS hr_requisition_round (
  id                  BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
  requisition_id      BIGINT      NOT NULL                COMMENT '招聘需求',
  round_no            TINYINT     NOT NULL                COMMENT '1一面 2二面 3终面',
  interviewer_user_id BIGINT      NOT NULL                COMMENT '该轮面试官',
  sort_no             INT         NOT NULL DEFAULT 0      COMMENT '顺序',
  create_by           VARCHAR(50) DEFAULT ''              COMMENT '创建者',
  create_time         DATETIME    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_by           VARCHAR(50) DEFAULT ''              COMMENT '更新者',
  update_time         DATETIME    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  is_active           TINYINT     NOT NULL DEFAULT 1      COMMENT '1有效 0删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_hr_req_round (requisition_id, round_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='招聘需求面试流程';

UPDATE hr_requisition SET target_text = TRIM(target_text) WHERE target_text <> TRIM(target_text);

CREATE TABLE IF NOT EXISTS hr_requisition_round_interviewer (
  id                  BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
  requisition_id      BIGINT      NOT NULL                COMMENT '招聘需求',
  round_no            TINYINT     NOT NULL                COMMENT '轮次',
  interviewer_user_id BIGINT      NOT NULL                COMMENT '面试官',
  create_by           VARCHAR(50) DEFAULT ''              COMMENT '创建者',
  create_time         DATETIME    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_by           VARCHAR(50) DEFAULT ''              COMMENT '更新者',
  update_time         DATETIME    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  is_active           TINYINT     NOT NULL DEFAULT 1      COMMENT '1有效 0删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_hr_round_interviewer (requisition_id, round_no, interviewer_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='招聘需求每轮面试官';

INSERT INTO hr_requisition_round_interviewer (requisition_id, round_no, interviewer_user_id, create_by, is_active)
SELECT r.requisition_id, r.round_no, r.interviewer_user_id, 'hr-migrate', 1
FROM hr_requisition_round r
WHERE r.is_active = 1 AND r.interviewer_user_id IS NOT NULL
AND NOT EXISTS (
  SELECT 1 FROM hr_requisition_round_interviewer i
  WHERE i.requisition_id = r.requisition_id AND i.round_no = r.round_no AND i.interviewer_user_id = r.interviewer_user_id
);

CREATE TABLE IF NOT EXISTS hr_interview_record (
  id                   BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  application_id       BIGINT       NOT NULL                COMMENT '候选人投递',
  requisition_id       BIGINT       NULL                    COMMENT '招聘需求',
  invite_id            BIGINT       NULL                    COMMENT '面试邀约',
  round_no             TINYINT      NOT NULL                COMMENT '轮次',
  interviewer_user_id  BIGINT       NOT NULL                COMMENT '面试官',
  conclusion           VARCHAR(16)  NULL                    COMMENT 'PASS/FAIL/PENDING',
  comment              TEXT         NULL                    COMMENT '评语',
  interviewed_at       DATETIME     NULL                    COMMENT '面试时间',
  create_by            VARCHAR(50)  DEFAULT ''              COMMENT '创建者',
  create_time          DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_by            VARCHAR(50)  DEFAULT ''              COMMENT '更新者',
  update_time          DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  is_active            TINYINT      NOT NULL DEFAULT 1      COMMENT '1有效 0删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_hr_interview_record (application_id, round_no, interviewer_user_id),
  KEY idx_hr_record_invite (invite_id),
  KEY idx_hr_record_req (requisition_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='面试记录';

CREATE TABLE IF NOT EXISTS hr_application_ai (
  id                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  application_id    BIGINT       NOT NULL                COMMENT '投递',
  requisition_id    BIGINT       NULL                    COMMENT '招聘需求',
  provider          VARCHAR(32)  NOT NULL                COMMENT '厂商标识',
  provider_name     VARCHAR(64)  NOT NULL                COMMENT '模型展示名',
  model_name        VARCHAR(64)  NULL                    COMMENT '模型名',
  score             INT          NOT NULL                COMMENT 'AI打分 0-100',
  pros_cons         MEDIUMTEXT   NOT NULL                COMMENT '优劣势',
  interview_advice  MEDIUMTEXT   NOT NULL                COMMENT '面试建议',
  create_by         VARCHAR(50)  DEFAULT ''              COMMENT '创建者',
  create_time       DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_by         VARCHAR(50)  DEFAULT ''              COMMENT '更新者',
  update_time       DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  is_active         TINYINT      NOT NULL DEFAULT 1      COMMENT '1有效 0删除',
  PRIMARY KEY (id),
  KEY idx_hr_app_ai (application_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='候选人AI分析，可多份';

INSERT INTO hr_interview_record (application_id, requisition_id, round_no, interviewer_user_id, comment, interviewed_at, create_by, is_active)
SELECT ir.application_id, a.requisition_id, ir.round_no, ir.interviewer_user_id, ir.comment, ir.interview_at, 'hr-migrate', 1
FROM hr_interview_round ir
JOIN hr_application a ON a.id = ir.application_id
WHERE ir.is_active = 1 AND ir.interviewer_user_id IS NOT NULL
AND NOT EXISTS (
  SELECT 1 FROM hr_interview_record rec
  WHERE rec.application_id = ir.application_id AND rec.round_no = ir.round_no AND rec.interviewer_user_id = ir.interviewer_user_id
);

CREATE TABLE IF NOT EXISTS hr_target_option (
  id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  name         VARCHAR(64)  NOT NULL                COMMENT '目标到岗',
  sort_no      INT          NOT NULL DEFAULT 0      COMMENT '排序',
  create_by    VARCHAR(50)  DEFAULT ''              COMMENT '创建者',
  create_time  DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_by    VARCHAR(50)  DEFAULT ''              COMMENT '更新者',
  update_time  DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  is_active    TINYINT      NOT NULL DEFAULT 1      COMMENT '1有效 0删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_hr_target_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='目标到岗选项';

INSERT INTO hr_target_option (name, sort_no, create_by, is_active) VALUES
('紧急-尽快', 1, 'hr-seed', 1),
('尽快', 2, 'hr-seed', 1),
('7月-尽快', 3, 'hr-seed', 1),
('常规节奏持续招聘', 4, 'hr-seed', 1)
ON DUPLICATE KEY UPDATE is_active = 1;
