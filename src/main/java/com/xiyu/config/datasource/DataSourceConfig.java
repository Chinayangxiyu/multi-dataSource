package com.xiyu.config.datasource;

import com.alibaba.druid.pool.DruidDataSource;
import com.alibaba.druid.pool.DruidDataSourceFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

/**
 * @description 数据源配置（多数据源）、事务配置
 */
@Configuration
@EnableTransactionManagement(proxyTargetClass=true)
@Component
public class DataSourceConfig {

    private Logger logger = LoggerFactory.getLogger(DataSourceConfig.class);

    @Resource
    private DataSourceProperties properties;


    /**
`     * @description: 主库数据源
     */
    @Bean(name="masterDataSource", destroyMethod = "close", initMethod = "init")
    @ConfigurationProperties(prefix = "jdbc.master.multi")
    public DruidDataSource masterDataSource(DataSourceProperties properties) throws Exception {
        Map<String, Object> map = getDataSourceMap(properties);
        return  (DruidDataSource)DruidDataSourceFactory.createDataSource(map);
    }



    /**
     * @description: 从库数据源
     */
    @Bean(name="slaveDataSource", destroyMethod = "close", initMethod = "init")
    @ConfigurationProperties(prefix = "jdbc.slave.multi")
    public DruidDataSource slaveDataSource(DataSourceProperties properties) throws Exception {
        Map<String, Object> map = getDataSourceMap(properties);
        return  (DruidDataSource)DruidDataSourceFactory.createDataSource(map);
    }




    /**
     * @description: OTHER主库数据源
     */
    @Bean(name="otherDataSource", destroyMethod = "close", initMethod = "init")
    @ConfigurationProperties(prefix = "jdbc.master.other")
    public DruidDataSource otherDataSource(DataSourceProperties properties) throws Exception {
        Map<String, Object> map = getDataSourceMap(properties);
        return  (DruidDataSource)DruidDataSourceFactory.createDataSource(map);
    }



    /**
     * 主从动态数据源配置
     */
    @Bean(name="dynamicDataSource")
    @Primary
    public DynamicDataSource dynamicDataSource()throws Exception{

        DynamicDataSource dynamicDataSource = new DynamicDataSource();
        Map<Object, Object> targetDataSources = new HashMap<>();
        targetDataSources.put(DynamicDataSource.DB_MASTER, masterDataSource(properties));
        targetDataSources.put(DynamicDataSource.DB_SLAVE, slaveDataSource(properties));

        // 设置数据源集合，SQLsession在获取数据源的时候
        dynamicDataSource.setTargetDataSources(targetDataSources);

        // 默认数据源
        dynamicDataSource.setDefaultTargetDataSource(masterDataSource(properties));
        return dynamicDataSource;
    }


    /**
     * 封装数据源信息
     * @param properties
     * @return
     */
    private Map<String, Object> getDataSourceMap(DataSourceProperties properties) {
        Map<String, Object> map = new HashMap<>();
        map.put("url", properties.getUrl());
        map.put("driverClassName", properties.getDriverClassName());
        map.put("username", properties.getUsername());
        map.put("password", properties.getPassword());
        map.put("initialSize", "1");
        map.put("maxActive", "20");
        map.put("maxWait", "60000");
        map.put("timeBetweenEvictionRunsMillis", "60000");
        map.put("validationQuery", "SELECT 'x'");
        map.put("testWhileIdle", "true");
        map.put("testOnBorrow", "false");
        map.put("testOnReturn", "false");
        map.put("poolPreparedStatements", "false");
        map.put("initConnectionSqls", "set names utf8mb4");
        return map;
    }
}
