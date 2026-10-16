package com.base.admin.common;

public class Constants {

    public static final String TOKEN_PREFIX = "Bearer ";
    public static final String TOKEN_HEADER = "Authorization";
    public static final String ADMIN_ROLE_KEY = "admin";
    public static final String ADMIN_PERM = "*:*:*";
    /** 测试环境「同步生产数据 / 同步表结构」仅此账号可见可调 */
    public static final String ENV_SYNC_USERNAME = "wangfangyang";
    /** JDBC URL / host 中出现即视为生产 RDS，本地/测试严禁直连 */
    public static final String RDS_PROD_INSTANCE_MARKER = "rm-bp18jx12jxs1884h9";
    /** JDBC URL / host 中出现即视为测试 RDS */
    public static final String RDS_TEST_INSTANCE_MARKER = "rm-bp15f2a3k5b93293k";

    public static final String MENU_TYPE_DIR = "M";
    public static final String MENU_TYPE_MENU = "C";
    public static final String MENU_TYPE_BUTTON = "F";

    public static final int STATUS_ACTIVE = 0;
    public static final int STATUS_DISABLED = 1;

    /** 账号已在其他设备登录，需确认是否强制下线 */
    public static final int CODE_LOGIN_CONFLICT = 40901;
    /** 当前会话已被其他设备强制下线 */
    public static final int CODE_SESSION_KICKED = 4011;
    /** 登录已超时，需重新登录 */
    public static final int CODE_SESSION_EXPIRED = 4012;

    /** 日监测话题类型：日巡查 */
    public static final String TERM_TYPE_DAILY = "日巡查";
    /** 日监测话题类型：周巡查 */
    public static final String TERM_TYPE_WEEKLY = "周巡查";

    /** 平台类型：AI平台（日监测可选） */
    public static final String PLATFORM_TYPE_AI = "AI平台";
    /** 平台类型：内容发布平台 */
    public static final String PLATFORM_TYPE_CONTENT = "内容发布平台";

    /** 内容投放状态：投放成功 */
    public static final String CONTENT_PUBLISH_SUCCESS = "投放成功";
    /** 内容投放状态：审核未通过 */
    public static final String CONTENT_PUBLISH_REJECTED = "审核未通过";
    /** 内容投放状态：待投放 */
    public static final String CONTENT_PUBLISH_PENDING = "待投放";
    /** 内容投放状态：未投放 */
    public static final String CONTENT_PUBLISH_NONE = "未投放";

    /** 内容投放聚合进度：投放完成 */
    public static final String CONTENT_AGG_DONE = "投放完成";
    /** 内容投放聚合进度：部分投放 */
    public static final String CONTENT_AGG_PARTIAL = "部分投放";
    /** 内容投放聚合进度：未投放 */
    public static final String CONTENT_AGG_NONE = "未投放";
    /** 内容投放聚合进度：待投放（有附件但尚未投放） */
    public static final String CONTENT_AGG_PENDING = "待投放";

    /** @deprecated 兼容旧文案 */
    public static final String CONTENT_AGG_ALL_SUCCESS = CONTENT_AGG_DONE;
    /** @deprecated 兼容旧文案 */
    public static final String CONTENT_AGG_ALL_NONE = CONTENT_AGG_NONE;

    /** 内容投放来源：导入 */
    public static final String CONTENT_SOURCE_IMPORT = "导入";
    /** 内容投放来源：手动新增 */
    public static final String CONTENT_SOURCE_MANUAL = "手动新增";
    /** 内容投放来源：AI生成 */
    public static final String CONTENT_SOURCE_AI = "AI生成";

    /** 内容投放形态：图文 */
    public static final String CONTENT_FORM_ARTICLE = "图文";
    /** 内容投放形态：视频 */
    public static final String CONTENT_FORM_VIDEO = "视频";

    /** 内容投放待分配占位 */
    public static final String CONTENT_UNASSIGNED = "待分配";

    /** 稿件状态：草稿 */
    public static final String CONTENT_STATUS_DRAFTING = "drafting";
    /** 稿件状态：待审核 */
    public static final String CONTENT_STATUS_PENDING_REVIEW = "pending_review";
    /** 稿件状态：修改中 */
    public static final String CONTENT_STATUS_REVISING = "revising";
    /** 稿件状态：已通过 */
    public static final String CONTENT_STATUS_APPROVED = "approved";
    /** 稿件状态：一审已通过，可发起二审 */
    public static final String CONTENT_STATUS_STAGE1_PASSED = "stage1_passed";
    /** 稿件状态：待投放（审核通过，回到投放管理维护投放） */
    public static final String CONTENT_STATUS_QUEUE = "queue";
    /** 文章审核阶段：一审 */
    public static final int CONTENT_REVIEW_STAGE1 = 1;
    /** 文章审核阶段：二审 */
    public static final int CONTENT_REVIEW_STAGE2 = 2;
    /** 稿件状态：已发布（投放管理中已有投放成功记录） */
    public static final String CONTENT_STATUS_PUBLISHED = "published";
    /** 在线正文格式（一期锁定） */
    public static final String CONTENT_FORMAT_LAKE = "text/lake";
    /** 平台文章正文来源：沿用问题统一稿 */
    public static final String CONTENT_MODE_SHARED = "shared";
    /** 平台文章正文来源：该平台独立稿 */
    public static final String CONTENT_MODE_CUSTOM = "custom";

    /** 任务关联业务：GEO内容投放 */
    public static final String TASK_BIZ_GEO_CONTENT_PLACEMENT = "geo_content_placement";
    /** 任务关联业务：GEO文章审核轮次 */
    public static final String TASK_BIZ_GEO_CONTENT_REVIEW = "geo_content_review";
    /** 任务类型：文章撰写 */
    public static final String TASK_TYPE_GEO_WRITE = "文章撰写";
    /** 任务类型：审核文章 */
    public static final String TASK_TYPE_GEO_REVIEW = "审核文章";
    /** 任务类型：文章发布 */
    public static final String TASK_TYPE_GEO_PUBLISH = "文章发布";
    /** 任务类型：待分配发布人 */
    public static final String TASK_TYPE_GEO_ASSIGN_PUBLISHER = "GEO文章待分配发布人";
    /** 任务类型：待分配撰写人 */
    public static final String TASK_TYPE_GEO_ASSIGN_WRITER = "GEO文章待分配撰写人";
    /** 撰写任务：已有正文时的进度 */
    public static final int TASK_PROGRESS_WRITE_DRAFTED = 50;
    /** 撰写任务：一审通过后的进度（二审通过才 100%） */
    public static final int TASK_PROGRESS_WRITE_STAGE1_PASSED = 80;
    /**
     * 在线撰写功能上线时间（含当日）。
     * 「我的撰写记录」排除上线前仅因指定撰写人而存在的历史投放；
     * 上线后新建投放、上线后派发的撰写任务、以及上线后实际保存过正文的投放仍会展示。
     */
    public static final java.time.LocalDateTime GEO_ONLINE_WRITE_LAUNCH_AT =
            java.time.LocalDateTime.of(2026, 10, 13, 0, 0, 0);
    /** 分配回写字段：发布人 */
    public static final String TASK_ASSIGN_FIELD_PUBLISHER = "publisher";
    /** 分配回写字段：撰写人 */
    public static final String TASK_ASSIGN_FIELD_WRITER = "writer";

    /** 招聘普通账号（钉钉扫码自动注册默认角色） */
    public static final String HR_USER_ROLE_KEY = "hr_user";

    /** 招聘基础数据：工作地址 */
    public static final String HR_DICT_WORK_ADDRESS = "WORK_ADDRESS";
    /** 招聘基础数据：背调不通过原因 */
    public static final String HR_DICT_BG_FAIL = "BG_FAIL";
    /** 招聘基础数据：offer 未发原因 */
    public static final String HR_DICT_OFFER_UNSENT = "OFFER_UNSENT";
    /** 招聘基础数据：电话&面试不合适理由 */
    public static final String HR_DICT_UNSUITABLE = "UNSUITABLE";
    /** 招聘基础数据：岗位优先级（独立于紧急/优先/常规） */
    public static final String HR_DICT_JOB_PRIORITY = "JOB_PRIORITY";

    /** GEO 模块选人角色：运营录入 */
    public static final String GEO_ROLE_OPS_ENTRY = "GEO运营录入";
    /** GEO 模块选人角色：管理复盘 */
    public static final String GEO_ROLE_MGMT_REVIEW = "GEO管理复盘";
    public static final String GEO_ROLE_KEY_OPS_ENTRY = "geo_ops_entry";
    public static final String GEO_ROLE_KEY_MGMT_REVIEW = "geo_mgmt_review";

    /** 演示数据标记：create_by / 备注前缀 */
    public static final String DEMO_MARKER = "【演示】";
    /** 演示数据 create_by */
    public static final String DEMO_CREATE_BY = "demo";

    private Constants() {}
}
