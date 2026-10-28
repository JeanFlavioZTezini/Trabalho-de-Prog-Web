package com.farmacia.farmaciaonline;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Sobe um PostgreSQL 16 descartável (Docker) para os testes que precisam de banco.
 *
 * O {@code @ServiceConnection} liga o DataSource da aplicação a este container
 * automaticamente, ignorando o {@code spring.datasource.*} do application.properties.
 * Assim os testes rodam sem nenhum Postgres instalado (só o Docker ligado) e com o mesmo
 * banco de produção: o Flyway aplica as migrações e o Hibernate faz o {@code validate}.
 *
 * Como usar em outro teste que precise de banco:
 * <pre>
 * &#64;SpringBootTest
 * &#64;Import(TestcontainersConfiguration.class)
 * class MeuTest { ... }
 *
 * &#64;DataJpaTest
 * &#64;AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
 * &#64;Import(TestcontainersConfiguration.class)
 * class MeuRepositoryTest { ... }
 * </pre>
 *
 * Testes de camada web ({@code @WebMvcTest}) não precisam disto.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgres() {
        return new PostgreSQLContainer<>(DockerImageName.parse("postgres:16"));
    }
}
