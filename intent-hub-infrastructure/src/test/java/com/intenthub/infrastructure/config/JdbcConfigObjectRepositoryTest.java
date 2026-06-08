package com.intenthub.infrastructure.config;

import com.intenthub.application.config.ConfigObjectType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcConfigObjectRepositoryTest {
    private JdbcConfigObjectRepository repository;
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:config_object;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
                "sa",
                ""
        );
        jdbcTemplate = new JdbcTemplate(dataSource);
        repository = new JdbcConfigObjectRepository(jdbcTemplate);
        jdbcTemplate.execute("drop table if exists intent_definition");
        jdbcTemplate.execute("""
                create table intent_definition (
                    id bigserial primary key,
                    tenant_id varchar(64) not null,
                    scene_id varchar(64) not null,
                    version varchar(64) not null,
                    intent_code varchar(128) not null,
                    intent_name varchar(256) not null,
                    enabled boolean not null default true,
                    definition jsonb not null default '{}'::jsonb,
                    created_at timestamp not null default now(),
                    unique (tenant_id, scene_id, version, intent_code)
                )
                """);
    }

    @Test
    void listsAndDeletesIntentConfigObject() {
        insertIntent("ORDER_QUERY", "订单查询");
        insertIntent("ORDER_CANCEL", "订单取消");

        assertThat(repository.list("demo", "order-scene", "v1", ConfigObjectType.INTENT)).hasSize(2);
        assertThat(repository.delete("demo", "order-scene", "v1", ConfigObjectType.INTENT, "ORDER_QUERY")).isTrue();
        assertThat(repository.delete("demo", "order-scene", "v1", ConfigObjectType.INTENT, "ORDER_MISSING")).isFalse();
        assertThat(repository.list("demo", "order-scene", "v1", ConfigObjectType.INTENT))
                .extracting(row -> row.get("intent_code"))
                .containsExactly("ORDER_CANCEL");
    }

    private void insertIntent(String intentCode, String intentName) {
        jdbcTemplate.update("""
                        insert into intent_definition (tenant_id, scene_id, version, intent_code, intent_name, enabled, definition)
                        values (?, ?, ?, ?, ?, ?, cast(? as jsonb))
                        """,
                "demo",
                "order-scene",
                "v1",
                intentCode,
                intentName,
                true,
                ConfigJsonSupport.objectMap(Map.of("confidence", BigDecimal.valueOf(0.85)))
        );
    }
}
