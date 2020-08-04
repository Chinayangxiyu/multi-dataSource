package com.xiyu.config.datasource;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.stereotype.Component;


/**
 * 使用ThreadLocal绑定数据源；在获取数据源前可以通过拦截器等功能去选择需要的数据源
 *
 * AbstractRoutingDataSource作用：
 * 1、我们在注册determineTargetDataSource数据源的时候，需要设置targetDataSources；targetDataSources表示我们实际配置的数据源；
 * 2、运行时调用getConnection()的时候通过determineCurrentLookupKey 去我们设置的实际数据源map配置中查找当前需要的数据源。
 */
@Slf4j
public class DynamicDataSource extends AbstractRoutingDataSource {



    /**
     * 线程安全的本地线程类
     */
    private static ThreadLocal<String> contextHolder = new ThreadLocal<String>();

    /**
     * 主库
     */
    public static final String DB_MASTER = "master";

    /**
     * 从库
     */
    public static final String DB_SLAVE = "slave";



    /**
     * 获取数据源
     * @return
     */
    public static String getDbType(){
        String db = contextHolder.get();
        log.debug("getDbType方法中从线程安全的里面获取到：" + db);
        if (db == null){
            db = DB_MASTER;
        }
        return db;
    }

    /**
     * 注入线程的数据源
     */
    public static void setDbType(String str){
        log.debug("所注入使用的数据源：" + str);
        contextHolder.set(str);
    }

    /**
     * 清理连接
     */
    public static void clearDBType(){
        contextHolder.remove();
    }


    /**
     * 动态数据源路由方法
     * @return
     */
    @Override
    protected Object determineCurrentLookupKey() {
        return DynamicDataSource.getDbType();
    }

}