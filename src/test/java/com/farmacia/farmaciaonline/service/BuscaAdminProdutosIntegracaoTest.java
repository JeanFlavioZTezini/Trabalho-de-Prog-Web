package com.farmacia.farmaciaonline.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.farmacia.farmaciaonline.TestcontainersConfiguration;
import com.farmacia.farmaciaonline.model.Produto;

/** Busca da lista de produtos do admin (UC12): % e _ digitados são texto, como já é na busca de clientes. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class BuscaAdminProdutosIntegracaoTest {

    @Autowired
    private ProdutoService service;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("buscar \"50%\" acha só o produto com 50% no nome, não o catálogo inteiro")
    void porcentagemEhTextoComum() {
        jdbc.update("update produto set nome = 'Álcool em Gel 50% Teste' where id = 17");

        assertThat(service.buscar("50%", "todos", PageRequest.of(0, 100)).getContent())
                .extracting(Produto::getId).containsExactly(17L);
    }

    @Test
    @DisplayName("sublinhado também é texto comum")
    void sublinhadoEhTextoComum() {
        assertThat(service.buscar("_", "todos", PageRequest.of(0, 100)).getContent()).isEmpty();
    }
}
