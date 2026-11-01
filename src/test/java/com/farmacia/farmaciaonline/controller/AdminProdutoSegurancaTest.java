package com.farmacia.farmaciaonline.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.farmacia.farmaciaonline.config.SecurityConfig;
import com.farmacia.farmaciaonline.model.ProdutoForm;
import com.farmacia.farmaciaonline.model.enums.Perfil;
import com.farmacia.farmaciaonline.repository.CategoriaRepository;
import com.farmacia.farmaciaonline.security.UsuarioAutenticado;
import com.farmacia.farmaciaonline.service.CategoriaService;
import com.farmacia.farmaciaonline.service.ProdutoService;

/** UC12: todos os endpoints de produto são só do ADMIN, inclusive POST direto. Sem banco. */
@WebMvcTest(AdminProdutoController.class)
@Import(SecurityConfig.class)
class AdminProdutoSegurancaTest {

    private static final List<String> GETS = List.of("/admin/produtos", "/admin/produtos/novo",
            "/admin/produtos/1/editar");

    private static final List<String> POSTS = List.of("/admin/produtos", "/admin/produtos/1",
            "/admin/produtos/1/excluir");

    @MockBean
    private ProdutoService service;

    @MockBean
    private CategoriaService categoriaService;

    @MockBean
    private CategoriaRepository categoriaRepository; // usado pelo NavegacaoAdvice (menu)

    @Autowired
    private MockMvc mvc;

    private static RequestPostProcessor admin() {
        return user(new UsuarioAutenticado(1L, "Admin", Perfil.ADMIN, "admin@farma.com", "x"));
    }

    @Test
    void visitanteEhMandadoParaOLoginEmTodosOsEndpoints() throws Exception {
        for (String url : GETS) {
            mvc.perform(get(url)).andExpect(redirectedUrlPattern("**/login*"));
        }
        for (String url : POSTS) {
            mvc.perform(post(url).with(csrf()).param("nome", "X")).andExpect(redirectedUrlPattern("**/login*"));
        }
        verifyNoInteractions(service);
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
        verifyNoInteractions(service);
    }

    @Test
    void adminAbreAsPaginas() throws Exception {
        when(service.buscar(any(), any(), any())).thenReturn(new PageImpl<>(List.of()));
        when(service.formDe(1L)).thenReturn(new ProdutoForm());

        for (String url : GETS) {
            mvc.perform(get(url).with(admin())).andExpect(status().isOk());
        }
    }

    @Test
    @DisplayName("salvar e excluir registram o administrador da sessão, não um id vindo do formulário")
    void postGravaOAdminDaSessao() throws Exception {
        mvc.perform(post("/admin/produtos").with(admin()).with(csrf()).param("nome", "Produto").param("marca", "Marca")
                .param("tarja", "SEM_TARJA").param("atualizadoPor", "99"))
                .andExpect(redirectedUrl("/admin/produtos"));
        verify(service).salvar(eq(null), any(ProdutoForm.class), eq(1L));

        mvc.perform(post("/admin/produtos/7/excluir").with(admin()).with(csrf()))
                .andExpect(redirectedUrl("/admin/produtos"));
        verify(service).excluir(7L, 1L);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void postSemTokenCsrfEhRecusadoMesmoParaAdmin() throws Exception {
        mvc.perform(post("/admin/produtos/1/excluir")).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
}
