package com.farmacia.farmaciaonline;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * O teste mais importante da Fase 1: sobe a aplicação inteira contra um PostgreSQL de verdade
 * (Testcontainers). Se passar, o Flyway aplicou todas as migrações e o
 * {@code spring.jpa.hibernate.ddl-auto=validate} confirmou que as entidades JPA batem com as
 * tabelas. Se alguém mudar uma coluna da entidade (ou da migração) sem acertar o outro lado,
 * este teste falha.
 *
 * Precisa do Docker ligado; não usa o banco do {@code docker compose}.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class FarmaciaOnlineApplicationTests {

	@Test
	void contextLoads() {
	}

}
