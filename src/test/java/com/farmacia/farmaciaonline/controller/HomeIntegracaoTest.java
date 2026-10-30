package com.farmacia.farmaciaonline.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.farmacia.farmaciaonline.TestcontainersConfiguration;
import com.farmacia.farmaciaonline.model.Apresentacao;
import com.farmacia.farmaciaonline.model.Categoria;
import com.farmacia.farmaciaonline.model.Produto;
import com.farmacia.farmaciaonline.repository.ApresentacaoRepository;
import com.farmacia.farmaciaonline.repository.CategoriaRepository;
import com.farmacia.farmaciaonline.repository.ProdutoRepository;

/**
 * A home e o menu lidos do banco real, incluindo dados que o seed não cobre.
 * O que o teste grava é desfeito no fim (@Transactional).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class HomeIntegracaoTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ProdutoRepository produtoRepository;

    @Autowired
    private ApresentacaoRepository apresentacaoRepository;

    @Autowired
    private CategoriaRepository categoriaRepository;

    private String home() throws Exception {
        return mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString()
                .replace('\u00a0', ' ');
    }

    @Test
    @DisplayName("produto sem nenhuma imagem aparece na home com o placeholder, sem derrubar a página")
    void produtoSemImagemUsaOPlaceholder() throws Exception {
        Produto produto = new Produto();
        produto.setNome("Produto sem foto de teste");
        produto.setMarca("Marca de teste");
        produto.setVisualizacoes(99_999);
        produtoRepository.saveAndFlush(produto);

        Apresentacao apresentacao = new Apresentacao();
        apresentacao.setProduto(produto);
        apresentacao.setDescricao("1 unidade");
        apresentacao.setSku("TESTE-SEM-FOTO-1");
        apresentacao.setEan("9990000000017");
        apresentacao.setPreco(new BigDecimal("10.00"));
        apresentacao.setEstoque(5);
        apresentacaoRepository.saveAndFlush(apresentacao);
        produto.getApresentacoes().add(apresentacao);

        String html = home();

        assertThat(html).contains("Produto sem foto de teste");
        assertThat(html).contains("R$ 10,00");
        assertThat(html).contains("/images/produtos/placeholder.svg");
    }

    @Test
    @DisplayName("o menu mostra categoria nova ativa e esconde a inativa")
    void menuMostraAtivaEEscondeInativa() throws Exception {
        categoriaRepository.saveAndFlush(categoria("Categoria nova de teste", true));
        categoriaRepository.saveAndFlush(categoria("Categoria inativa de teste", false));

        String html = home();

        assertThat(html).contains("Categoria nova de teste");
        assertThat(html).doesNotContain("Categoria inativa de teste");
    }

    private Categoria categoria(String nome, boolean ativa) {
        Categoria categoria = new Categoria();
        categoria.setNome(nome);
        categoria.setOrdem(99);
        categoria.setAtiva(ativa);
        return categoria;
    }
}
