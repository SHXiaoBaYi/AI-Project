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
    /** 内容投放状态：未投放 */
    public static final String CONTENT_PUBLISH_NONE = "未投放";

    /** 内容投放聚合进度：投放完成 */
    public static final String CONTENT_AGG_DONE = "投放完成";
    /** 内容投放聚合进度：部分投放 */
    public static final String CONTENT_AGG_PARTIAL = "部分投放";
    /** 内容投放聚合进度：未投放 */
    public static final String CONTENT_AGG_NONE = "未投放";

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

    private Constants() {}
}
