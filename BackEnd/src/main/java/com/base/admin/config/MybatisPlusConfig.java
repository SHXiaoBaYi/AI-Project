package com.base.admin.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.base.admin.util.SecurityUtils;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;

@Configuration
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }

    @Bean
    public MetaObjectHandler metaObjectHandler() {
        return new MetaObjectHandler() {
            @Override
            public void insertFill(MetaObject metaObject) {
                this.strictInsertFill(metaObject, "createTime", LocalDateTime::now, LocalDateTime.class);
                this.strictInsertFill(metaObject, "updateTime", LocalDateTime::now, LocalDateTime.class);
                String username = SecurityUtils.getCurrentUsername();
                this.strictInsertFill(metaObject, "createBy", () -> username, String.class);
                this.strictInsertFill(metaObject, "updateBy", () -> username, String.class);
                this.strictInsertFill(metaObject, "isActive", () -> 1, Integer.class);
            }

            @Override
            public void updateFill(MetaObject metaObject) {
                // 不用 strictUpdateFill：updateById 时实体里已有旧 updateTime，strict 会跳过导致一直等于创建时间
                this.setFieldValByName("updateTime", LocalDateTime.now(), metaObject);
                String username = SecurityUtils.getCurrentUsername();
                this.setFieldValByName("updateBy", username, metaObject);
            }
        };
    }
}
