package org.tiglor.system.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.tiglor.common.core.AutoFillHandler;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
// common-log 的 OperLogMapper / LoginLogMapper 刻意不自我装配（不加 @MapperScan），
// 由唯一读写审计表的进程——本服务——在这里认领；其它服务引入 common-log 只用来上报。
@MapperScan({"org.tiglor.system.mapper", "org.tiglor.common.log.mapper"})
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }

    @Bean
    public AutoFillHandler autoFillHandler() {
        return new AutoFillHandler();
    }
}
