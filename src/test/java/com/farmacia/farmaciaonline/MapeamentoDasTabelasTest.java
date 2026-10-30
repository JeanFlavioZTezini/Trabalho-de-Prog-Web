package com.farmacia.farmaciaonline;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Table;

/**
 * Toda tabela criada pelas migrações precisa ter uma entidade JPA (e vice-versa). O
 * {@code ddl-auto=validate} só confere as entidades que existem: se uma entidade sumir do código, o
 * contexto continua subindo. Este teste pega esse caso.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class MapeamentoDasTabelasTest {

    /** Tabela de junção do @ManyToMany de Produto e a tabela de controle do Flyway: não têm entidade. */
    private static final Set<String> SEM_ENTIDADE = Set.of("flyway_schema_history", "produto_categoria");

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("toda tabela da V1 tem uma entidade JPA, e toda entidade aponta para uma tabela existente")
    void todaTabelaTemEntidade() {
        Set<String> tabelasDasEntidades = new TreeSet<>();
        entityManagerFactory.getMetamodel().getEntities()
                .forEach(entidade -> tabelasDasEntidades.add(entidade.getJavaType().getAnnotation(Table.class).name()));

        Set<String> tabelasDoBanco = new TreeSet<>(jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' AND table_type = 'BASE TABLE'",
                String.class));
        tabelasDoBanco.removeAll(SEM_ENTIDADE);

        assertThat(tabelasDasEntidades).containsExactlyInAnyOrderElementsOf(tabelasDoBanco);
    }
}
