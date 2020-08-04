package com.xiyu.config.mybatis.interceptor;

import com.xiyu.config.datasource.DynamicDataSource;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.executor.keygen.NoKeyGenerator;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

/**
 * 主从拦截器，根据读写逻辑，分别配置数据源
 * 主库负责写，从库负责读
 */
@Intercepts({
        @Signature(type = Executor.class, method = "update", args = {MappedStatement.class, Object.class}),
        @Signature(type = Executor.class, method = "query", args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class})
})
@Slf4j
public class MasterSlaveSourceInterceptor implements Interceptor {


    private static final String regex = ".*insert\\u0020.*|.*delete\\u0020.*|.*update\\u0020.*";


    /**
     *
     * @param invocation
     * @return
     * @throws Throwable
     */
    @Override
    public Object intercept(Invocation invocation) throws Throwable {

        // 当前是否有实际事务处于活动状态 true 是
        boolean synchronizationActive = TransactionSynchronizationManager.isActualTransactionActive();
        //获取sql的资源变量参数（增删改查的一些参数）
        Object[] objects = invocation.getArgs();
        //MappedStatement 可以获取到到底是增加还是删除 还是修改的操作
        MappedStatement mappedStatement = (MappedStatement) objects[0];



        String lookupKey = DynamicDataSource.DB_MASTER;
        // 如果当前不存在存活事务，根据操作类型指定数据源
        // 如果当前不存在存活事务，根据操作类型指定数据源
        if (!synchronizationActive){
            // 如果是select方法，可能需要切换到从库
            if (mappedStatement.getSqlCommandType().equals(SqlCommandType.SELECT)) {
                // 若主键生成器不是NoKeyGenerator，需要调用主库生产主键。（NoKeyGenerator表示不对主键进行处理）
                if (mappedStatement.getKeyGenerator() != null &&
                        ! (mappedStatement.getKeyGenerator() instanceof NoKeyGenerator)){
                    lookupKey = DynamicDataSource.DB_MASTER;
                } else {

                    BoundSql boundSql = mappedStatement.getSqlSource().getBoundSql(objects[1]);
                    String sqlStr = boundSql.getSql();
                    // 主要是避免加锁SQL， for update；这种类型是select，但是需要读主库
                    String sql = sqlStr.toLowerCase(Locale.CHINA).replaceAll("[\\t\\n\\r]", " ");
                    if (sql.matches(regex)){
                        lookupKey = DynamicDataSource.DB_MASTER;
                    } else {
                        lookupKey = DynamicDataSource.DB_SLAVE;
                    }
                }
            }
        }
        log.debug("拦截方法:[{}]; 使用数据源:[{}]; sql类型:[{}]......................... " , mappedStatement.getId(), lookupKey, mappedStatement.getSqlCommandType());
        //最终决定使用的数据源
        DynamicDataSource.setDbType(lookupKey);
        return invocation.proceed();
    }

}