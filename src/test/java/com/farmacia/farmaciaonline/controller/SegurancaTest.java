package com.farmacia.farmaciaonline.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import com.farmacia.farmaciaonline.config.SecurityConfig;
import com.farmacia.farmaciaonline.repository.CategoriaRepository;
import com.farmacia.farmaciaonline.repository.ProdutoRepository;

/**
 * Regras de acesso e CSRF (F1-09). Sobe só a camada web, sem banco e sem Docker:
 * o login "de verdade" contra o seed é testado no LoginIntegracaoTest.
 */
@WebMvcTest({HomeController.class, InstitucionalController.class, LoginController.class, AdminController.class})
@Import(SecurityConfig.class)
class SegurancaTest {

    @MockBean
    private CategoriaRepository categoriaRepository;

    @MockBean
    private ProdutoRepository produtoRepository;

    @Autowired
    private MockMvc mvc;

    // ---------- /admin ----------

    @Test
    void visitanteEmAdminEhRedirecionadoParaOLogin() throws Exception {
        mvc.perform(get("/admin"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login*"));
    }

    @Test
    @WithMockUser(roles = "CLIENTE")
    void clienteEmAdminRecebe403() throws Exception {
        mvc.perform(get("/admin")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "CLIENTE")
    void clienteTambemNaoAcessaSubrotasDeAdmin() throws Exception {
        mvc.perform(get("/admin/produtos")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminEmAdminRecebe200ComOPainel() throws Exception {
        String html = mvc.perform(get("/admin"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(html).contains("Painel do administrador");
    }

    // ---------- páginas públicas ----------

    @ParameterizedTest
    @ValueSource(strings = {"/", "/login", "/institucional/boas-praticas"})
    void paginasPublicasAbremSemLogin(String url) throws Exception {
        mvc.perform(get(url)).andExpect(status().isOk());
    }

    // ---------- CSRF ----------

    @Test
    void postSemTokenCsrfEhRecusado() throws Exception {
        mvc.perform(post("/login").param("identificador", "cliente@farma.com").param("senha", "Farma@123"))
                .andExpect(status().isForbidden());
    }

    @Test
    void postComTokenCsrfPassaDoFiltroEChegaAoLogin() throws Exception {
        // Aqui não há usuário cadastrado (sem banco), então o login falha por credenciais:
        // o que importa é que NÃO deu 403, ou seja, o token foi aceito.
        mvc.perform(post("/login").with(csrf()).param("identificador", "x").param("senha", "y"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?erro=credenciais"));
    }

    @Test
    @WithMockUser
    void logoutSemTokenCsrfEhRecusado() throws Exception {
        mvc.perform(post("/logout")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void logoutPorPostComTokenVoltaParaOLoginComAviso() throws Exception {
        mvc.perform(post("/logout").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?logout"));
    }

    @Test
    void formularioDeLoginTrazOTokenCsrf() throws Exception {
        String html = mvc.perform(get("/login"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(html).contains("name=\"_csrf\"");
    }

    @Test
    @WithMockUser
    void botaoSairDoCabecalhoTambemTrazOTokenCsrf() throws Exception {
        String html = mvc.perform(get("/"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(html).contains("action=\"/logout\"");
        assertThat(html).contains("name=\"_csrf\"");
    }
}
