package com.base.admin.service.taskbiz;

import com.base.admin.exception.BusinessException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class TaskBizFieldWriteDispatcher {

    private final Map<String, TaskBizFieldWriter> writers;

    public TaskBizFieldWriteDispatcher(List<TaskBizFieldWriter> writerList) {
        this.writers = writerList.stream()
                .collect(Collectors.toMap(TaskBizFieldWriter::bizType, Function.identity(), (a, b) -> a));
    }

    public void write(String bizType, Long bizId, String assignField, Long userId, String displayName) {
        if (!StringUtils.hasText(bizType) || bizId == null || !StringUtils.hasText(assignField)) {
            return;
        }
        TaskBizFieldWriter writer = writers.get(bizType.trim());
        if (writer == null) {
            throw new BusinessException("未注册的业务回写处理器: " + bizType);
        }
        writer.writeAssignedUser(bizId, assignField.trim(), userId, displayName);
    }

    public boolean supports(String bizType) {
        return StringUtils.hasText(bizType) && writers.containsKey(bizType.trim());
    }
}
