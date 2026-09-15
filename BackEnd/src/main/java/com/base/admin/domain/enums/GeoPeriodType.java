package com.base.admin.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum GeoPeriodType {
    WEEK("WEEK"),
    MONTH("MONTH"),
    YEAR("YEAR");

    private final String code;
}
