package com.xiyu.config.mybatis;



import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import javax.sql.DataSource;

/**
 * other数据源配置SQLsession
 */
@Configuration
@MapperScan(basePackages = {"com.xiyu.dao.mapper.other"}, sqlSessionFactoryRef = "otherSqlSessionFactory")
@Slf4j
public class OtherMybatisConfig {


    /**
     *
     * @param dataSource
     * @return
     * @throws Exception
     */
    @Bean(name = "otherSqlSessionFactory")
    public SqlSessionFactory otherSqlSessionFactory(@Qualifier("otherDataSource")DataSource dataSource) throws Exception {
        SqlSessionFactory sqlSessionFactory = null;
        try {
            ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            SqlSessionFactoryBean sessionFactory = new SqlSessionFactoryBean();


            // 设置默认数据源
            sessionFactory.setDataSource(dataSource);
            sessionFactory.setMapperLocations(resolver.getResources("classpath:/mapper/other/**/*.xml"));
            sqlSessionFactory = sessionFactory.getObject();
        } catch (Exception e) {
            log.error("fail to init MyBatis sqlSessionFactory!", e);
        }
        return sqlSessionFactory;
    }

    @Bean(name = "otherTransactionManager")
    public DataSourceTransactionManager qkTransactionManager(@Qualifier("otherDataSource") DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }
}
