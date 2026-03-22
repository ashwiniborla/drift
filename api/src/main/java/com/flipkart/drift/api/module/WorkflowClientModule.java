package com.flipkart.drift.api.module;

import com.codahale.metrics.MetricRegistry;
import com.flipkart.drift.api.config.DriftConfiguration;
import com.flipkart.drift.persistence.dao.ConnectionType;
import com.flipkart.drift.persistence.dao.IConnectionProvider;
import com.flipkart.drift.api.exception.JerseyViolationInformativeExceptionMapper;
import com.flipkart.drift.api.config.RedisConfiguration;
import com.flipkart.drift.api.exception.mapper.ApiExceptionMapper;
import com.google.inject.*;
import com.google.inject.name.Names;
import com.netflix.config.DynamicProperty;
import io.dropwizard.setup.Environment;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.HConstants;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.ConnectionFactory;
import org.apache.hadoop.security.UserGroupInformation;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisSentinelPool;
import redis.clients.jedis.Protocol;

import java.time.Instant;
import java.util.*;

@Slf4j
public class WorkflowClientModule extends AbstractModule {
    private final RedisConfiguration redisConfiguration;
    private final JedisSentinelPool jedisSentinelPool;
    private final Environment environment;
    private final DriftConfiguration driftConfiguration;


    public WorkflowClientModule(DriftConfiguration driftConfiguration, Environment environment, MetricRegistry metricRegistry) {
        this.redisConfiguration = driftConfiguration.getRedisConfiguration();
        this.environment = environment;
        this.driftConfiguration = driftConfiguration;
        this.jedisSentinelPool = provideJedisPool();
    }

    private JedisSentinelPool provideJedisPool() {
        try {
            final RedisConfiguration redisConfiguration = this.redisConfiguration;
            String hosts = redisConfiguration.getSentinels();
            StringTokenizer strTkn = new StringTokenizer(hosts, ",");
            List<String> hostList = new ArrayList<>();
            while (strTkn.hasMoreTokens())
                hostList.add(strTkn.nextToken());
            Set<String> sentinels = new HashSet<>(hostList);
            GenericObjectPoolConfig<?> genericObjectPoolConfig = getGenericObjectPoolConfig(redisConfiguration);
            int database = redisConfiguration.getDatabase();
            JedisSentinelPool pool = new JedisSentinelPool(redisConfiguration.getMaster(), sentinels, genericObjectPoolConfig,
                    Protocol.DEFAULT_TIMEOUT, redisConfiguration.getPassword(), database);
            log.info("Redis JedisSentinelPool ready: logicalDatabase={}, sentinelMaster={}, keyPrefix={}",
                    database, redisConfiguration.getMaster(), redisConfiguration.getPrefix());
            writeStartupDbVerificationKey(pool, redisConfiguration);
            return pool;
        } catch (Exception e) {
            log.error("Failed to Connected to RedisDao Server {}", e.getMessage(), e);
            return null;
            // TODO : Clean this up ... Change made so that bootstrap shouldn't be failing on redis failure
//            throw new RedisStoreException(Response.Status.INTERNAL_SERVER_ERROR, "Unable to init redis config", e.getMessage());
        }
    }

    /** Temporary: remove after Redis DB verification. */
    private static void writeStartupDbVerificationKey(JedisSentinelPool pool, RedisConfiguration redisConfiguration) {
        if (pool == null) {
            return;
        }
        int database = redisConfiguration.getDatabase();
        String prefix = StringUtils.isNotBlank(redisConfiguration.getPrefix()) ? redisConfiguration.getPrefix() : "drift";
        String key = prefix + ":drift_db_startup_verify";
        int ttl = 604800;
        String value = "Hello from DB" + database + " at " + Instant.now();
        try (Jedis jedis = pool.getResource()) {
            jedis.setex(key, ttl, value);
            log.info("Redis DB verification key written: db={}, key={}, ttl={}s. Example: redis-cli -h <host> -n {} GET {}",
                    database, key, ttl, database, key);
        } catch (Exception e) {
            log.warn("Redis DB verification key could not be written: {}", e.getMessage());
        }
    }

    private static GenericObjectPoolConfig<?> getGenericObjectPoolConfig(RedisConfiguration redisConfiguration) {
        GenericObjectPoolConfig<?> genericObjectPoolConfig = new GenericObjectPoolConfig<>();
        genericObjectPoolConfig.setTimeBetweenEvictionRunsMillis(-1);
        genericObjectPoolConfig.setMaxTotal(redisConfiguration.getMaxTotal());
        genericObjectPoolConfig.setTestOnBorrow(redisConfiguration.isTestOnBorrow());
        genericObjectPoolConfig.setMaxWaitMillis(redisConfiguration.getMaxWaitMillis());
        genericObjectPoolConfig.setBlockWhenExhausted(redisConfiguration.isBlockWhenExhausted());
        genericObjectPoolConfig.setMaxIdle(redisConfiguration.getMaxIdle());
        genericObjectPoolConfig.setMinIdle(redisConfiguration.getMinIdle());
        return genericObjectPoolConfig;
    }

    private void addExceptionMappers() {
        environment.jersey().getResourceConfig().register(ApiExceptionMapper.class);
        environment.jersey().getResourceConfig().register(JerseyViolationInformativeExceptionMapper.class);
    }

    public static class ConnectionProvider implements IConnectionProvider {
        private final Injector injector;

        @Inject
        public ConnectionProvider(Injector injector) {
            this.injector = injector;
        }

        @Override
        public Connection getConnection(ConnectionType connectionType) {
            return injector.getInstance(Key.get(Connection.class, Names.named(connectionType.name())));
        }

        @Override
        public boolean isDegraded() {
            return false;
        }
    }

    public static class ConnectionProviderWorker implements Provider<Connection> {
        private final DriftConfiguration driftConfiguration;

        @Inject
        public ConnectionProviderWorker(DriftConfiguration driftConfiguration) {
            this.driftConfiguration = driftConfiguration;
        }

        @Override
        public Connection get() {
            String zkQuorum;
            try {
                zkQuorum = DynamicProperty.getInstance("zookeeper.quorum.hot").getString();
            } catch (Exception e) {
                throw new RuntimeException("Failed to fetch zookeeperQuorumHot from config", e);
            }

            if (StringUtils.isEmpty(zkQuorum)) {
                log.error("ZOOKEEPER_QUORUM_WORKER not found in config service property");
                throw new RuntimeException("ZOOKEEPER_QUORUM_WORKER not found in config service property");
            }
            Configuration configuration = HBaseConfiguration.create();
            configuration.set(HConstants.ZOOKEEPER_QUORUM, zkQuorum);
            applyHadoopIdentity(driftConfiguration);
            populateBasicFields(configuration);

            try {
                return ConnectionFactory.createConnection(configuration);
            } catch (Exception e) {
                log.error("Error while creating connection to HBase", e);
                throw new RuntimeException("Failed to create HBase connection", e);
            }
        }
    }

    private static void applyHadoopIdentity(DriftConfiguration driftConfiguration) {
        try {
            String userName = driftConfiguration.getHadoopUserName();
            if (StringUtils.isNotBlank(userName)) {
                System.setProperty("HADOOP_USER_NAME", userName);
            }
            String loginUser = driftConfiguration.getHadoopLoginUser();
            if (StringUtils.isNotBlank(loginUser)) {
                UserGroupInformation.setLoginUser(UserGroupInformation.createRemoteUser(loginUser));
            }
        } catch (Exception e) {
            // Keep non-fatal; log and proceed without overriding identity
            log.warn("Unable to apply Hadoop identity from configuration: {}", e.getMessage());
        }
    }

    private static void populateBasicFields(Configuration configuration) {
        configuration.set("hbase.zookeeper.property.clientPort", "2181");
        configuration.set(HConstants.ZK_SESSION_TIMEOUT, "60000");
        configuration.set("zookeeper.recovery.retry", "3");
        configuration.set(HConstants.HBASE_CLIENT_RETRIES_NUMBER, "3");
        configuration.set(HConstants.HBASE_RPC_TIMEOUT_KEY, "6000");
        configuration.set(HConstants.HBASE_CLIENT_IPC_POOL_SIZE, "20");
        configuration.set(HConstants.HBASE_CLIENT_OPERATION_TIMEOUT, "6000");
        configuration.set(HConstants.HBASE_RPC_SHORTOPERATION_TIMEOUT_KEY, "4000");
        configuration.set(HConstants.HBASE_CLIENT_SCANNER_TIMEOUT_PERIOD, "120000");
        configuration.set(HConstants.HBASE_CLIENT_MAX_PERSERVER_TASKS, "40");
        configuration.set(HConstants.HBASE_CLIENT_MAX_PERREGION_TASKS, "4");
        configuration.set(HConstants.HBASE_CLIENT_IPC_POOL_TYPE, "RoundRobinPool");
        configuration.set("hbase.hconnection.threads.max", "128");
    }

    @Override
    protected void configure() {
        addExceptionMappers();
        bind(Connection.class).annotatedWith(Names.named(ConnectionType.HOT.name())).toProvider(ConnectionProviderWorker.class).asEagerSingleton();
        bind(IConnectionProvider.class).to(ConnectionProvider.class).asEagerSingleton();
    }

    @Provides
    @Singleton
    public JedisSentinelPool getJedisSentinelPool() {
        return this.jedisSentinelPool;
    }

    @Provides
    @Singleton
    public DriftConfiguration getDriftConfiguration() {
        return this.driftConfiguration;
    }

}
