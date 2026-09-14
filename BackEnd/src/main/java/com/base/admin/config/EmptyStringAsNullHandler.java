package com.base.admin.config;

import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.DeserializationProblemHandler;

import java.io.IOException;

/**
 * 将 ""（空字符串）统一转为 null，覆盖所有类型（Integer / Long / Date / Enum …）。
 * Jackson 在反序列化遇到类型不匹配时会回调此 Handler；
 * 如果原始字符串为空，直接返回 null 而非抛出异常。
 */
public class EmptyStringAsNullHandler extends DeserializationProblemHandler {

    @Override
    public Object handleWeirdStringValue(DeserializationContext ctxt, Class<?> targetType,
                                          String valueToConvert, String failureMsg) throws IOException {
        if (valueToConvert.isEmpty()) {
            return null;
        }
        return NOT_HANDLED;
    }
}
