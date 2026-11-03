package com.farmacia.farmaciaonline.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import com.farmacia.farmaciaonline.config.SecurityConfig;
import com.farmacia.farmaciaonline.repository.CategoriaRepository;
import com.farmacia.farmaciaonline.service.CategoriaService;
import com.farmacia.farmaciaonline.service.CategoriaService.ResumoExclusao;

/** UC13: todos os endpoints de categoria são só do ADMIN, inclusive POST direto. Sem banco. */
@WebMvcTest(AdminCategoriaController.class)
@Import(SecurityConfig.class)
class AdminCategoriaSegurancaTest {

    private static final List<String> GETS = List.of(
            "/admin/categorias", "/admin/categorias/nova",
            "/admin/categorias/1/editar", "/admin/categorias/1/excluir");

    private static final List<String> POSTS = List.of(
            "/admin/categorias", "/admin/categorias/1", "/admin/categorias/1/excluir",
            "/admin/categorias/1/inativar", "/admin/categorias/1/reatribuir");

    @MockBean
    private CategoriaService service;

    @MockBean
    private CategoriaRepository categoriaRepository; // usado pelo NavegacaoAdvice (menu)

    @Autowired
    private MockMvc mvc;

    @Test
    void visitanteEhMandadoParaOLoginEmTodosOsEndpoints() throws Exception {
        for (String url : GETS) {
            mvc.perform(get(url)).andExpect(redirectedUrlPattern("**/login*"));
        }
        for (String url : POSTS) {
            mvc.perform(post(url).with(csrf()).param("nome", "X")).andExpect(redirectedUrlPattern("**/login*"));
        }
    }

    @Test
    @WithMockUser(roles = "CLIENTE")
    void clienteRecebe403EmTodosOsEndpoints() throws Exception {
        for (String url : GETS) {
            mvc.perform(get(url)).andExpect(status().isForbidden());
        }
        for (String url : POSTS) {
            mvc.perform(post(url).with(csrf()).param("nome", "X")).andExpect(status().isForbidden());
        }
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminAbrePaginasEFazPostDireto() throws Exception {
        when(service.resumoExclusao(1L)).thenReturn(new ResumoExclusao(1L, "Teste", true, 0, 0, List.of()));

        mvc.perform(get("/admin/categorias")).andExpect(status().isOk());
        mvc.perform(get("/admin/categorias/nova")).andExpect(status().isOk());
        mvc.perform(get("/admin/categorias/1/excluir")).andExpect(status().isOk());
        for (String url : POSTS) {
            mvc.perform(post(url).with(csrf()).param("nome", "X")).andExpect(status().is3xxRedirection());
        }
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void listaTemAcoesComNomeAcessivelENivelPorClasse() throws Exception {
        when(service.listarArvore()).thenReturn(List.of(
                new CategoriaService.CategoriaLinha(1L, "Medicamentos", null, 1, true, 0),
                new CategoriaService.CategoriaLinha(11L, "Analgésicos", null, 1, false, 1)));

        String html = mvc.perform(get("/admin/categorias")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(html).doesNotContain("style=");
        assertThat(html).contains("<td class=\"nivel nivel-1\">Analgésicos</td>");
        // Ações em ícone (lápis e lixeira), com o nome da categoria no title e no texto para leitor de tela.
        assertThat(html).containsPattern("(?s)<a class=\"acao-icone\" href=\"/admin/categorias/11/editar\" title=\"Editar Analgésicos\">"
                + "\\s*<svg[^>]*aria-hidden=\"true\".*?</svg>\\s*<span class=\"visually-hidden\">Editar Analgésicos</span>");
        assertThat(html).containsPattern("(?s)<a class=\"acao-icone acao-icone--perigo\" href=\"/admin/categorias/11/excluir\"\\s+"
                + "title=\"Excluir Analgésicos\">\\s*<svg[^>]*aria-hidden=\"true\".*?</svg>"
                + "\\s*<span class=\"visually-hidden\">Excluir Analgésicos</span>");
        assertThat(html).contains("<span class=\"selo selo--neutro\">Inativa</span>");
        assertThat(html).contains("<li aria-current=\"page\">Categorias</li>");
        // Na lista da seção, o Voltar leva ao Painel.
        assertThat(html).contains("<a class=\"topo-admin__voltar\" href=\"/admin\">")
                .contains("Voltar<span class=\"visually-hidden\"> para o Painel</span>");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void exclusaoUsaBotaoDePerigoEInativarComoSecundario() throws Exception {
        when(service.resumoExclusao(1L)).thenReturn(new ResumoExclusao(1L, "Teste", true, 0, 0, List.of()));

        String html = mvc.perform(get("/admin/categorias/1/excluir")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(html).contains("<button type=\"submit\" class=\"btn-perigo\">Excluir</button>")
                .contains("<button type=\"submit\" class=\"btn-secundario\">Apenas inativar</button>");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void postSemTokenCsrfEhRecusadoMesmoParaAdmin() throws Exception {
        mvc.perform(post("/admin/categorias/1/inativar")).andExpect(status().isForbidden());
    }
}
