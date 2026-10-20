package com.base.admin.robot;

import java.util.List;

public record RobotReply(String markdown, List<CardButton> buttons) {

    public record CardButton(String title, String url) {
    }

    public static RobotReply text(String markdown) {
        return new RobotReply(markdown, List.of());
    }

    public static RobotReply card(String markdown, List<CardButton> buttons) {
        return new RobotReply(markdown, buttons == null ? List.of() : buttons);
    }
}
