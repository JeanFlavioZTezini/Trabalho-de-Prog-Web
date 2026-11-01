package com.farmacia.farmaciaonline.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.server.ResponseStatusException;

import com.farmacia.farmaciaonline.config.SecurityConfig;
import com.farmacia.farmaciaonline.model.enums.Perfil;
import com.farmacia.farmaciaonline.repository.CategoriaRepository;
import com.farmacia.farmaciaonline.security.UsuarioAutenticado;
import com.farmacia.farmaciaonline.service.ClienteAdminService;
import com.farmacia.farmaciaonline.service.ClienteDetalhe;

/** UC11: só o ADMIN consulta clientes, inclusive por URL direta; a rota é somente leitura. Sem banco. */
@WebMvcTest(AdminClienteController.class)
@Import(SecurityConfig.class)
class AdminClienteSegurancaTest {

    private static final List<String> GETS = List.of("/admin/clientes", "/admin/clientes/7");
    private static final List<String> OUTROS_METODOS = List.of("/admin/clientes", "/admin/clientes/7");

    @MockBean
    private ClienteAdminService service;

    @MockBean
    private CategoriaRepository categoriaRepository; // usado pelo NavegacaoAdvice (menu)

    @Autowired
    private MockMvc mvc;

    private static RequestPostProcessor admin() {
        return user(new UsuarioAutenticado(1L, "Admin", Perfil.ADMIN, "admin@farma.com", "x"));
    }

    private static ClienteDetalhe detalhe() {
        return new ClienteDetalhe(7L, "Maria", "529.982.247-25", "maria@teste.com", true, "(11) 98765-4321", false,
                java.time.LocalDate.of(1990, 1, 1), null, com.farmacia.farmaciaonline.model.enums.StatusUsuario.ATIVA,
                "Ativa", java.time.LocalDateTime.of(2026, 1, 1, 10, 0), List.of());
    }

    @Test
    void visitanteEhMandadoParaOLogin() throws Exception {
        for (String url : GETS) {
            mvc.perform(get(url)).andExpect(redirectedUrlPattern("**/login*"));
        }
        verifyNoInteractions(service);
    }

    @Test
    @WithMockUser(roles = "CLIENTE")
    void clienteRecebe403EmListaEDetalhePorUrlDireta() throws Exception {
        for (String url : GETS) {
            mvc.perform(get(url)).andExpect(status().isForbidden());
        }
        verifyNoInteractions(service);
    }

    @Test
    @WithMockUser(roles = "CLIENTE")
    void clienteRecebe403EmEscritas() throws Exception {
        for (String url : OUTROS_METODOS) {
            mvc.perform(post(url).with(csrf())).andExpect(status().isForbidden());
            mvc.perform(put(url).with(csrf())).andExpect(status().isForbidden());
            mvc.perform(delete(url).with(csrf())).andExpect(status().isForbidden());
        }
        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("nem o ADMIN consegue escrever: não existe POST, PUT nem DELETE")
    void adminNaoTemRotaDeEscrita() throws Exception {
        for (String url : OUTROS_METODOS) {
            mvc.perform(post(url).with(admin()).with(csrf())).andExpect(status().isMethodNotAllowed());
            mvc.perform(put(url).with(admin()).with(csrf())).andExpect(status().isMethodNotAllowed());
            mvc.perform(delete(url).with(admin()).with(csrf())).andExpect(status().isMethodNotAllowed());
        }
        verifyNoInteractions(service);
    }

    @Test
    void adminAbreListaEDetalhe() throws Exception {
        when(service.listar(any(), any(), any(), any(), any())).thenReturn(new PageImpl<>(List.of()));
        when(service.detalhar(eq(1L), any(), eq(7L))).thenReturn(detalhe());

        mvc.perform(get("/admin/clientes").with(admin())).andExpect(status().isOk());
        mvc.perform(get("/admin/clientes/7").with(admin())).andExpect(status().isOk());
        verify(service).detalhar(eq(1L), any(), eq(7L));
    }

    @Test
    @DisplayName("cliente inexistente (404 do serviço) chega como 404")
    void detalheInexistenteEh404() throws Exception {
        when(service.detalhar(any(), any(), eq(99L))).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));

        mvc.perform(get("/admin/clientes/99").with(admin())).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("status desconhecido no filtro vira 'todos'")
    void statusInvalidoViraTodos() throws Exception {
        when(service.listar(any(), any(), any(), any(), any())).thenReturn(new PageImpl<>(List.of()));

        mvc.perform(get("/admin/clientes").param("status", "QUALQUER").with(admin())).andExpect(status().isOk());

        verify(service).listar(eq(1L), any(), any(), eq(null), any());
        verify(service, never()).detalhar(any(), any(), any());
    }
}
