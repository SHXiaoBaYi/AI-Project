package com.base.admin.robot;

/**
 * 机器人产品定数（链接监控 / 闲忙 / 告警）。
 */
public final class RobotProductRules {

    private RobotProductRules() {
    }

    /** 监控列表默认全局可见 */
    public static final String SCOPE_GLOBAL = "GLOBAL";

    /**
     * 链接探测：按自然日，每天 09:30（Asia/Shanghai）跑一轮。
     * interval_sec 仅作兼容字段，调度不再按它滚动。
     */
    public static final String PROBE_CRON = "0 30 9 * * ?";
    public static final String PROBE_ZONE = "Asia/Shanghai";
    public static final int DEFAULT_INTERVAL_SEC = 24 * 60 * 60;

    /** 单聊告警兜底通知人（添加人无钉钉 userid 时） */
    public static final String DM_ALERT_USERNAME = "wangfangyang";

    /** 群内任何人可删监控 */
    public static final boolean GROUP_ANYONE_CAN_DELETE = true;

    /** 闲忙允许在群里用（首次需 @；多轮进行中可免 @） */
    public static final boolean CALENDAR_ALLOWED_IN_GROUP = true;

    /**
     * 群里多轮免 @：用户已有非 IDLE 会话时，未 @ 也处理（前提是钉钉把消息推到 Stream）。
     * 新开意图仍需 @，避免刷屏误触。
     */
    public static final boolean GROUP_MULTI_TURN_SKIP_AT = true;

    /** 会话保留的近期对话轮数（user+assistant 各算一条） */
    public static final int HISTORY_MAX_TURNS = 20;

    public static final String SLOT_HISTORY = "_history";

    public static final String STATE_IDLE = "IDLE";
    public static final String STATE_WAIT_URL = "WAIT_URL";
    public static final String STATE_WAIT_MORE = "WAIT_MORE";

    public static final String INTENT_CALENDAR_FREEBUSY = "calendar.freebusy";
    public static final String INTENT_LINK_ADD = "link_monitor.add";
    public static final String INTENT_LINK_DELETE = "link_monitor.delete";
    public static final String INTENT_HELP = "robot.help";

    public static final String CMD_LM_DEL_PREFIX = "__robot_lm_del__:";
}
