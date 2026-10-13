package com.base.admin.common;

public class Constants {

    public static final String TOKEN_PREFIX = "Bearer ";
    public static final String TOKEN_HEADER = "Authorization";
    public static final String ADMIN_ROLE_KEY = "admin";
    public static final String ADMIN_PERM = "*:*:*";

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
    /** 稿件状态：待投放（审核通过，进入文章列表维护投放） */
    public static final String CONTENT_STATUS_QUEUE = "queue";
    /** 稿件状态：已发布（文章列表已有投放成功记录） */
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
