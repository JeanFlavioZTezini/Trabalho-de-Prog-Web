package com.farmacia.farmaciaonline;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.util.StringUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class FarmaciaOnlineApplicationTests {

	@Autowired
	private MockMvc mvc;

	@Test
	void contextLoads() {
	}

	@Test
	void homeExibeProdutosDoSeedCategoriasPrecoEIndicadores() throws Exception {
		MvcResult resultado = mvc.perform(get("/"))
				.andExpect(status().isOk())
				.andReturn();
		String html = resultado.getResponse().getContentAsString().replace('\u00a0', ' ');

		assertThat(html).contains("Novalgina Dipirona 1g");
		assertThat(html).contains("R$ 30,99");
		assertThat(html).contains("/produtos/1");
		assertThat(html).contains("Indisponível");
		assertThat(html).contains("Exige receita");
		assertThat(html).contains("Dor e febre");
		assertThat(html).doesNotContain("Loratadina 10mg");
		assertThat(StringUtils.countOccurrencesOf(html, "<article class=\"card\"")).isEqualTo(16);
		assertThat(html).contains("aria-label=\"Mostrar subcategorias de Medicamentos\"");
	}

}
