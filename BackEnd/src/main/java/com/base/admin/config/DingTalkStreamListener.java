package com.base.admin.config;

import com.base.admin.service.DingTalkAppService;
import com.base.admin.service.DingTalkRobotAssistantService;
import com.dingtalk.open.app.api.OpenDingTalkClient;
import com.dingtalk.open.app.api.OpenDingTalkStreamClientBuilder;
import com.dingtalk.open.app.api.callback.DingTalkStreamTopics;
import com.dingtalk.open.app.api.callback.OpenDingTalkCallbackListener;
import com.dingtalk.open.app.api.models.bot.ChatbotMessage;
import com.dingtalk.open.app.api.security.AuthClientCredential;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 钉钉 Stream：订阅机器人单聊消息，转给日程助手桥接服务。
 */
@Slf4j
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
@RequiredArgsConstructor
public class DingTalkStreamListener implements ApplicationRunner {

    private final DingTalkProperties dingTalkProperties;
    private final DingTalkAppService dingTalkAppService;
    private final DingTalkRobotAssistantService robotAssistantService;

    private volatile OpenDingTalkClient client;

    @Override
    public void run(ApplicationArguments args) {
        if (dingTalkProperties.getRobot() == null || !dingTalkProperties.getRobot().isStreamEnabled()) {
            log.info("钉钉机器人 Stream 已关闭（dingtalk.robot.stream-enabled=false）");
            return;
        }
        DingTalkAppService.Credential credential = dingTalkAppService.credential();
        if (credential == null) {
            log.info("钉钉应用未启用或缺少 ClientId/Secret，跳过机器人 Stream 连接");
            return;
        }
        try {
            OpenDingTalkClient streamClient = OpenDingTalkStreamClientBuilder.custom()
                    .credential(new AuthClientCredential(credential.clientId(), credential.clientSecret()))
                    .registerCallbackListener(
                            DingTalkStreamTopics.BOT_MESSAGE_TOPIC,
                            (OpenDingTalkCallbackListener<ChatbotMessage, Map<String, Object>>) message -> {
                                try {
                                    robotAssistantService.handleBotMessage(message);
                                } catch (Exception ex) {
                                    log.warn("钉钉机器人消息处理异常: {}", ex.getMessage(), ex);
                                }
                                return new HashMap<>();
                            })
                    .build();
            streamClient.start();
            this.client = streamClient;
            log.info("钉钉机器人 Stream 已连接（topic={}），单聊消息将转日程助手", DingTalkStreamTopics.BOT_MESSAGE_TOPIC);
        } catch (Exception ex) {
            log.error("钉钉机器人 Stream 启动失败: {}", ex.getMessage(), ex);
        }
    }

    @PreDestroy
    public void stop() {
        OpenDingTalkClient c = this.client;
        if (c == null) {
            return;
        }
        try {
            c.stop();
            log.info("钉钉机器人 Stream 已断开");
        } catch (Exception ex) {
            log.warn("钉钉机器人 Stream 断开异常: {}", ex.getMessage());
        }
    }
}
