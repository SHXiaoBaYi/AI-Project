package com.base.admin.service;

import com.base.admin.domain.vo.DingTalkBusyUserOptionVO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DingTalkRobotAssistantServiceTest {

    private final DingTalkRobotAssistantService service = new DingTalkRobotAssistantService(
            null, null, null, null, null, null, null, null, null);

    @Test
    void samePhoneIsOnePersonAndPrefersDingtalkBound() {
        List<DingTalkBusyUserOptionVO> users = List.of(
                user(1L, "18702167186", "王方扬", "18702167186", 0),
                user(2L, "18702167186_1", "王方扬", "187-0216-7186", 0),
                user(3L, "18702167186_2", "王方扬", "+86 18702167186", 1));

        DingTalkRobotAssistantService.ResolveResult result = service.resolveTarget("查王方扬的日程", users);

        assertNull(result.error());
        assertEquals(1, result.users().size());
        assertEquals(3L, result.users().getFirst().getUserId());
    }

    @Test
    void sameNameWithDifferentPhonesStaysAmbiguous() {
        List<DingTalkBusyUserOptionVO> users = List.of(
                user(1L, "wang1", "王方扬", "18702167186", 1),
                user(2L, "wang2", "王方扬", "13800000000", 1));

        DingTalkRobotAssistantService.ResolveResult result = service.resolveTarget("查王方扬的日程", users);

        assertTrue(result.error().contains("匹配到多位同事"));
        assertTrue(result.users().isEmpty());
    }

    @Test
    void missingPhoneDoesNotMergeSameName() {
        List<DingTalkBusyUserOptionVO> users = List.of(
                user(1L, "wang1", "王方扬", "18702167186", 1),
                user(2L, "wang2", "王方扬", null, 1));

        DingTalkRobotAssistantService.ResolveResult result = service.resolveTarget("查王方扬的日程", users);

        assertTrue(result.error().contains("匹配到多位同事"));
    }

    @Test
    void multiPersonAskCollapsesOnlyDuplicatePhones() {
        List<DingTalkBusyUserOptionVO> users = List.of(
                user(1L, "18702167186", "王方扬", "18702167186", 0),
                user(3L, "18702167186_2", "王方扬", "18702167186", 1),
                user(8L, "li", "李四", "13900000000", 1));

        DingTalkRobotAssistantService.ResolveResult result =
                service.resolveTarget("查王方扬、李四的共同空闲", users);

        assertNull(result.error());
        assertEquals(List.of(3L, 8L), result.users().stream().map(DingTalkBusyUserOptionVO::getUserId).toList());
    }

    private static DingTalkBusyUserOptionVO user(Long id, String username, String nickname, String phone, int bound) {
        DingTalkBusyUserOptionVO vo = new DingTalkBusyUserOptionVO();
        vo.setUserId(id);
        vo.setUsername(username);
        vo.setNickname(nickname);
        vo.setPhone(phone);
        vo.setDingtalkBound(bound);
        return vo;
    }
}
