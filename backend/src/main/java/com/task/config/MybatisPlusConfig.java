package com.task.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置
 *
 * <p>P2.9: 注册 {@link OptimisticLockerInnerInterceptor}，
 * 启用 @Version 乐观锁。{@code TaskStateMachineService} 5 个状态流转方法
 * 依赖此拦截器实现并发控制（防止两个请求同时改同一任务状态）。</p>
 *
 * <p>同时注册分页拦截器以支持 MyBatis-Plus 的 {@code IPage} 分页查询。</p>
 *
 * @author Mavis
 */
@Configuration
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 分页拦截器（MySQL 8.0）
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        // P2.9: 乐观锁拦截器（@Version 字段自动带 WHERE version=? 条件）
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        return interceptor;
    }
}
