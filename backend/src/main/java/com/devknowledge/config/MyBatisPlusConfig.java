package com.devknowledge.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.devknowledge.model.StringArrayTypeHandler;
import com.devknowledge.model.UuidTypeHandler;
import com.devknowledge.model.VectorTypeHandler;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.UUID;

@Configuration
public class MyBatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor());
        return interceptor;
    }

    @Bean
    public org.apache.ibatis.type.TypeHandlerRegistry typeHandlerRegistry(SqlSessionFactory sqlSessionFactory) {
        org.apache.ibatis.type.TypeHandlerRegistry registry = sqlSessionFactory.getConfiguration().getTypeHandlerRegistry();

        // UUID 类型处理器
        registry.register(UUID.class, org.apache.ibatis.type.JdbcType.VARCHAR, UuidTypeHandler.class);
        registry.register(UUID.class, UuidTypeHandler.class);

        // PostgreSQL text[] 数组类型处理器
        registry.register(String[].class, StringArrayTypeHandler.class);

        // pgvector vector 类型处理器
        registry.register(VectorTypeHandler.class);

        return registry;
    }
}
