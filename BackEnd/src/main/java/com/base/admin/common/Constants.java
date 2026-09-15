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

    /** 日监测长短词：日巡查 */
    public static final String TERM_TYPE_DAILY = "日巡查";
    /** 日监测长短词：周巡查 */
    public static final String TERM_TYPE_WEEKLY = "周巡查";

    /** 平台类型：AI平台（日监测可选） */
    public static final String PLATFORM_TYPE_AI = "AI平台";
    /** 平台类型：内容发布平台 */
    public static final String PLATFORM_TYPE_CONTENT = "内容发布平台";

    private Constants() {}
}
