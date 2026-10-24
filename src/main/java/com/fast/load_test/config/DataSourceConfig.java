package com.fast.load_test.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;

import javax.sql.DataSource;

/**
 * Sends read-only transactions (e.g. Spring Data's findById on a cache miss) to the Postgres
 * replica and everything else to the primary. LazyConnectionDataSourceProxy picks the target
 * at the first statement, after the transaction has marked the connection read-only.
 * Without app.datasource.replica.jdbc-url, everything uses the primary.
 */
@Configuration
public class DataSourceConfig {

    @Bean
    @ConfigurationProperties("spring.datasource.hikari")
    public HikariDataSource primaryDataSource(DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
    }

    @Bean
    @ConfigurationProperties("app.datasource.replica")
    @ConditionalOnProperty("app.datasource.replica.jdbc-url")
    public HikariDataSource replicaDataSource() {
        return new HikariDataSource();
    }

    @Bean
    @Primary
    public DataSource dataSource(@Qualifier("primaryDataSource") DataSource primary,
                                 @Qualifier("replicaDataSource") ObjectProvider<DataSource> replica) {
        LazyConnectionDataSourceProxy proxy = new LazyConnectionDataSourceProxy(primary);
        replica.ifAvailable(r -> proxy.setReadOnlyDataSource(new ReplicaFallbackDataSource(r, primary)));
        return proxy;
    }
}
