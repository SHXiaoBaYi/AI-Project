package com.base.admin.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresPermission {
    /**
     * 任一权限命中即可（OR）。
     * 支持通配：{@code hr:*} 表示任意招聘权限；{@code hr:record:*} 表示该模块下任意权限。
     */
    String[] value();
}
