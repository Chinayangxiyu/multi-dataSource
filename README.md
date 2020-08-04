## 基于springboot,mybatis,mysql多数据源配置
概述：项目开发中，有时会遇到多数据源的配置；分别有两种场景去配置多数据源。一是mysql主从分离后
，我们需要进行主从的数据源配置；二是可能需要调用其他数据库配置数据源。这两种场景可能会叠加。


## 1、配置读写分离数据源
1-1、继承AbstractRoutingDataSource，重写determineCurrentLookupKey（）方法；
```
@Component
@Slf4j
public class DynamicDataSource extends AbstractRoutingDataSource {

 /**
     * 动态数据源路由方法
     * @return
     */
    @Override
    protected Object determineCurrentLookupKey() {
        return DynamicDataSource.getDbType();
    }
}
```
1-2、配置数据源，配置的dynamicDataSource主要是起路由适配的作用，dynamicDataSource需要加注解@Primary
```
@Configuration
@EnableTransactionManagement(proxyTargetClass=true)
@Component
public class DataSourceConfig {


    /**
     * @description: 主库数据源
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
     * 主从动态数据源配置
     */
    @Bean(name="dynamicDataSource")
    @Primary
    public DynamicDataSource dynamicDataSource()throws Exception{

        DynamicDataSource dynamicDataSource = new DynamicDataSource();
        Map<Object, Object> targetDataSources = new HashMap<>();
        targetDataSources.put(DynamicDataSource.DB_MASTER, masterDataSource(properties));
        targetDataSources.put(DynamicDataSource.DB_SLAVE, slaveDataSource(properties));

        // 设置实际的数据源集合
        dynamicDataSource.setTargetDataSources(targetDataSources);

        // 默认数据源（必须设置为主库）
        dynamicDataSource.setDefaultTargetDataSource(masterDataSource(properties));
        return dynamicDataSource;
    }


    /**
     * 封装配置信息
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
```

1-3、拦截器配置 MasterSlaveSourceInterceptor
```
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
```

1-4、总结
本案例读写分离，重写AbstractRoutingDataSource，通过mybatis拦截器，根据当前执行的SQL类型去动态选择需要的数据源。
该方案的优势是开发人员无感知，但是扩展性较差，如果切换成其他数据库可能会不适用。
其他方案一：自定义注解，和AOP；每一个需要选择其他数据源的方法上都需要加上这个注解，指定特定的数据源。
其他方案二：复用注解@Transactional内部的readOnly属性；配置自定义事务，继承DataSourceTransactionManager；根据事务的属性选择数据源。
方案一、二都需要实现DynamicDataSource，只是不同于本方案是的区别是，本方案是基于拦截器实现的。

## 2、多数据源配置
当当前系统需要连接其他数据库时，需要配置多数据源。在设计的时候应该基于相应的规范，将mapper接口和文件根据数据源的不同进行分类。
多数据主要是配置不同数据源的SQLsession
2-1、multi数据源配置SQLsession，扫描指定包下的接口和文件
```
@Configuration
@MapperScan(basePackages = {"com.xiyu.dao.mapper.multi"})
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
}
```

2-2、other数据源配置SQLsession
```
@Configuration
@MapperScan(basePackages = {"com.xiyu.dao.mapper.other"})
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
}
```

2-3、总结：  
（1）多数据源的配置核心是AbstractRoutingDataSource；  
（2）不同数据源的接口应该按包区分，基于约定开发；  
（3）多数据源基于SqlSessionFactory，读写分离基于mybatis拦截器或者AOP。
