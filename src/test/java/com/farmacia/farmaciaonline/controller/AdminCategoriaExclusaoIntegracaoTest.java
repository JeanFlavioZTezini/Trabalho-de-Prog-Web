package com.farmacia.farmaciaonline.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.farmacia.farmaciaonline.TestcontainersConfiguration;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * UC13, exclusão de categoria pela tela (até aqui sem teste). Seed (V2): Medicamentos (1) tem subcategorias; Gripe e
 * resfriado (12) tem só o produto 4, que também está em Dor e febre (11); Alergias (13) tem 2 produtos.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
@WithMockUser(roles = "ADMIN")
class AdminCategoriaExclusaoIntegracaoTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    /** As requisições do MockMvc rodam na transação do teste: o JDBC só vê o que já foi enviado ao banco. */
    @PersistenceContext
    private EntityManager em;

    private int contar(String sql) {
        em.flush();
        return jdbc.queryForObject(sql, Integer.class);
    }

    private String categoriasDoProduto(long produtoId) {
        em.flush();
        return jdbc.queryForObject("select string_agg(categoria_id::text, ',' order by categoria_id) from produto_categoria "
                + "where produto_id = ?", String.class, produtoId);
    }

    @Test
    @DisplayName("tela de exclusão: um h1, o resumo e o formulário de reatribuir quando há produtos")
    void telaDeExclusaoMostraOResumo() throws Exception {
        String html = mvc.perform(get("/admin/categorias/12/excluir")).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        assertThat(StringUtils.countOccurrencesOf(html, "<h1")).isEqualTo(1);
        assertThat(html).contains("<strong>Gripe e resfriado</strong>").contains("0 subcategoria(s) e 1 produto(s)")
                .contains("action=\"/admin/categorias/12/reatribuir\"").contains("<label for=\"destinoId\">")
                .doesNotContain("action=\"/admin/categorias/12/excluir\"");
    }

    @Test
    @DisplayName("reatribuir para uma categoria em que o produto já está: exclui a origem e não duplica o vínculo")
    void reatribuirParaCategoriaQueOProdutoJaTem() throws Exception {
        mvc.perform(post("/admin/categorias/12/reatribuir").with(csrf()).param("destinoId", "11"))
                .andExpect(redirectedUrl("/admin/categorias"))
                .andExpect(flash().attribute("sucesso", "Produtos reatribuídos e categoria excluída."));

        assertThat(contar("select count(*) from categoria where id = 12")).isZero();
        assertThat(categoriasDoProduto(4)).isEqualTo("11");
    }

    @Test
    @DisplayName("reatribuir sem destino, para destino inexistente ou inativo: volta à tela com o motivo e nada muda")
    void reatribuirRecusado() throws Exception {
        mvc.perform(post("/admin/categorias/13/reatribuir").with(csrf()))
                .andExpect(redirectedUrl("/admin/categorias/13/excluir"))
                .andExpect(flash().attribute("erro", "Escolha outra categoria como destino dos produtos."));
        mvc.perform(post("/admin/categorias/13/reatribuir").with(csrf()).param("destinoId", "99999"))
                .andExpect(flash().attribute("erro", "Categoria de destino inexistente."));
        jdbc.update("update categoria set ativa = false where id = 14");
        em.clear();
        mvc.perform(post("/admin/categorias/13/reatribuir").with(csrf()).param("destinoId", "14"))
                .andExpect(flash().attribute("erro", "A categoria de destino está inativa."));

        assertThat(contar("select count(*) from categoria where id = 13")).isOne();
    }

    @Test
    @DisplayName("excluir categoria com subcategorias ou com produtos é recusado com o motivo")
    void excluirRecusado() throws Exception {
        mvc.perform(post("/admin/categorias/1/excluir").with(csrf()))
                .andExpect(redirectedUrl("/admin/categorias/1/excluir"))
                .andExpect(flash().attribute("erro", "Não é possível excluir: a categoria possui subcategorias. "
                        + "Exclua-as antes ou inative a categoria."));
        mvc.perform(post("/admin/categorias/13/excluir").with(csrf()))
                .andExpect(flash().attribute("erro", "Não é possível excluir: a categoria possui produtos. "
                        + "Reatribua-os a outra categoria ou inative esta."));
    }

    @Test
    @DisplayName("excluir categoria vazia: some da lista")
    void excluirCategoriaVazia() throws Exception {
        mvc.perform(post("/admin/categorias/24/excluir").with(csrf()))
                .andExpect(redirectedUrl("/admin/categorias"))
                .andExpect(flash().attribute("sucesso", "Categoria excluída."));

        assertThat(contar("select count(*) from categoria where id = 24")).isZero();
    }
}
