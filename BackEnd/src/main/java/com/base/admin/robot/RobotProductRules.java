package com.base.admin.robot;

/**
 * 机器人产品定数（链接监控 / 闲忙 / 告警）。
 */
public final class RobotProductRules {

    private RobotProductRules() {
    }

    /** 监控列表默认全局可见 */
    public static final String SCOPE_GLOBAL = "GLOBAL";

    /** 「24 小时监控」= 每隔 24 小时探测一次 */
    public static final int DEFAULT_INTERVAL_SEC = 24 * 60 * 60;

    /** 单聊添加的监控：仅后台告警，并通知该系统用户 */
    public static final String DM_ALERT_USERNAME = "wangfangyang";

    /** 群内任何人可删监控（全局列表场景下：绑定群内成员可删） */
    public static final boolean GROUP_ANYONE_CAN_DELETE = true;

    /** 闲忙允许在群里用（需 @ 机器人） */
    public static final boolean CALENDAR_ALLOWED_IN_GROUP = true;

    public static final String STATE_IDLE = "IDLE";
    public static final String STATE_WAIT_URL = "WAIT_URL";
    public static final String STATE_WAIT_MORE = "WAIT_MORE";

    public static final String INTENT_CALENDAR_FREEBUSY = "calendar.freebusy";
    public static final String INTENT_LINK_ADD = "link_monitor.add";
    public static final String INTENT_LINK_DELETE = "link_monitor.delete";
    public static final String INTENT_HELP = "robot.help";

    public static final String CMD_LM_DEL_PREFIX = "__robot_lm_del__:";
}
