package com.farmacia.farmaciaonline;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * F-19: upgrade de um banco que parou na V2 (com o seed e dados de uso) até a última migration, sem apagar nada. Roda num
 * banco à parte no mesmo container, para não mexer no banco que os outros testes usam.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class MigracaoUpgradeIntegracaoTest {

    private static final String BANCO = "upgrade_teste";

    @Autowired
    private PostgreSQLContainer<?> postgres;

    @Autowired
    private JdbcTemplate principal;

    private DriverManagerDataSource antigo;

    @BeforeEach
    void criarBanco() {
        principal.execute("drop database if exists " + BANCO);
        principal.execute("create database " + BANCO);
        String url = postgres.getJdbcUrl().replace("/" + postgres.getDatabaseName(), "/" + BANCO);
        antigo = new DriverManagerDataSource(url, postgres.getUsername(), postgres.getPassword());
    }

    @AfterEach
    void apagarBanco() {
        principal.execute("drop database if exists " + BANCO + " with (force)");
    }

    private Flyway flyway(String alvo) {
        var configuracao = Flyway.configure().dataSource(antigo).locations("classpath:db/migration");
        return (alvo == null ? configuracao : configuracao.target(alvo)).load();
    }

    @Test
    @DisplayName("banco na V2 com dados de uso sobe até a última versão e mantém tudo, preenchendo as colunas novas")
    void upgradeDesdeAV2() {
        flyway("2").migrate();
        JdbcTemplate banco = new JdbcTemplate(antigo);
        Long cliente = banco.queryForObject("select id from usuario where email = 'cliente@farma.com'", Long.class);
        banco.update("insert into endereco (usuario_id, destinatario, cep, logradouro, numero, bairro, cidade, uf) "
                + "values (?, 'Cliente', '79002000', 'Rua Antiga', '1', 'Centro', 'Campo Grande', 'MS')", cliente);
        Long pedido = banco.queryForObject("insert into pedido (usuario_id, status, tipo_entrega, loja_retirada_id, "
                + "subtotal, frete, total) values (?, 'ENTREGUE', 'RETIRADA', (select min(id) from loja), 42.00, 0, 42.00) "
                + "returning id", Long.class, cliente);
        int produtos = banco.queryForObject("select count(*) from produto", Integer.class);

        var resultado = flyway(null).migrate();

        assertThat(resultado.success).isTrue();
        List<String> versoes = banco.queryForList(
                "select version from flyway_schema_history where success order by installed_rank", String.class);
        assertThat(versoes).containsExactly("1", "2", "3", "4", "5", "6", "7", "8", "9");

        assertThat(banco.queryForObject("select count(*) from produto", Integer.class)).isEqualTo(produtos);
        Map<String, Object> linha = banco.queryForMap(
                "select total, versao, estorno_pendente, status from pedido where id = ?", pedido);
        assertThat((BigDecimal) linha.get("total")).isEqualByComparingTo("42.00");
        assertThat(linha).containsEntry("versao", 0L).containsEntry("estorno_pendente", false)
                .containsEntry("status", "ENTREGUE");

        assertThat(banco.queryForObject("select principal from endereco where usuario_id = ?", Boolean.class, cliente))
                .isTrue();
        assertThat(banco.queryForObject("select email_confirmado_em is not null from usuario where id = ?", Boolean.class,
                cliente)).isTrue();
        assertThat(banco.queryForObject(
                "select email_confirmado_em is null from usuario where email = 'pendente@farma.com'", Boolean.class))
                .isTrue();
        assertThat(banco.queryForObject("select unaccent('Dipirôna')", String.class)).isEqualTo("Dipirona");
    }

    @Test
    @DisplayName("rodar o Flyway de novo num banco já atualizado não aplica nada")
    void segundaExecucaoNaoFazNada() {
        flyway(null).migrate();

        assertThat(flyway(null).migrate().migrationsExecuted).isZero();
    }
}
