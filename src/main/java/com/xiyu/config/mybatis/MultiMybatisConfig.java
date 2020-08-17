package com.xiyu.config.mybatis;


import com.xiyu.config.mybatis.interceptor.MasterSlaveSourceInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import javax.sql.DataSource;

/**
 * 配置multi数据源的SQLsession
 */
@Configuration
@MapperScan(basePackages = {"com.xiyu.dao.mapper.multi"}, sqlSessionFactoryRef = "multiSqlSessionFactory")
@Slf4j
public class MultiMybatisConfig {


    // 当前系统自己的数据库的SQLsession
    @Bean(name = "multiSqlSessionFactory")
    @Primary
    public SqlSessionFactory multiSqlSessionFactory(@Qualifier("masterDataSource")DataSource dataSource) {
        SqlSessionFactory sqlSessionFactory = null;
        try {
            ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            SqlSessionFactoryBean sessionFactory = new SqlSessionFactoryBean();


            // 动态数据源拦截器
            MasterSlaveSourceInterceptor masterSlaveSourceInterceptor = new MasterSlaveSourceInterceptor();
            Interceptor[] plugins = {masterSlaveSourceInterceptor};

            // 设置默认数据源
            sessionFactory.setDataSource(dataSource);
            sessionFactory.setMapperLocations(resolver.getResources("classpath:/mapper/multi/**/*.xml"));
            sessionFactory.setPlugins(plugins);
            sqlSessionFactory = sessionFactory.getObject();
        } catch (Exception e) {
            log.error("fail to init MyBatis sqlSessionFactory!", e);
        }
        return sqlSessionFactory;
    }

    @Bean(name = "multiTransactionManager")
    public DataSourceTransactionManager qkTransactionManager(@Qualifier("dynamicDataSource") DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }
}
