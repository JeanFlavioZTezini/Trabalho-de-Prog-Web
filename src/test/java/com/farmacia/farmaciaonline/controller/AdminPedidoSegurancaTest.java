package com.farmacia.farmaciaonline.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.farmacia.farmaciaonline.config.SecurityConfig;
import com.farmacia.farmaciaonline.model.enums.StatusPedido;
import com.farmacia.farmaciaonline.model.enums.TipoEntrega;
import com.farmacia.farmaciaonline.repository.CategoriaRepository;
import com.farmacia.farmaciaonline.security.UsuarioAutenticado;
import com.farmacia.farmaciaonline.model.enums.Perfil;
import com.farmacia.farmaciaonline.service.AdminPedidosService;

/** UC17: lista/detalhe e todas as escritas são exclusivas do ADMIN, com CSRF. */
@WebMvcTest(AdminPedidoController.class)
@Import(SecurityConfig.class)
class AdminPedidoSegurancaTest {

    @MockBean private AdminPedidosService service;
    @MockBean private CategoriaRepository categoriaRepository; // NavegacaoAdvice monta o menu no cabeçalho.
    @Autowired private MockMvc mvc;

    private static RequestPostProcessor admin() {
        return user(new UsuarioAutenticado(1L, "Admin", Perfil.ADMIN, "admin@farma.com", "x"));
    }

    @Test
    void visitanteNaoAcessaListaDetalheNemEscritas() throws Exception {
        mvc.perform(get("/admin/pedidos")).andExpect(redirectedUrlPattern("**/login*"));
        mvc.perform(get("/admin/pedidos/7")).andExpect(redirectedUrlPattern("**/login*"));
        mvc.perform(post("/admin/pedidos/7/status").with(csrf()).param("versao", "1")
                .param("novoStatus", "CANCELADO")).andExpect(redirectedUrlPattern("**/login*"));
        mvc.perform(post("/admin/pedidos/7/conferencia-receita").with(csrf()).param("versao", "1")
                .param("confirmado", "true")).andExpect(redirectedUrlPattern("**/login*"));
        verifyNoInteractions(service);
    }

    @Test
    @WithMockUser(roles = "CLIENTE")
    void clienteRecebe403InclusiveEmPostDireto() throws Exception {
        mvc.perform(get("/admin/pedidos")).andExpect(status().isForbidden());
        mvc.perform(get("/admin/pedidos/7")).andExpect(status().isForbidden());
        mvc.perform(post("/admin/pedidos/7/status").with(csrf()).param("versao", "1")
                .param("novoStatus", "PAGAMENTO_APROVADO")).andExpect(status().isForbidden());
        mvc.perform(post("/admin/pedidos/7/conferencia-receita").with(csrf()).param("versao", "1")
                .param("confirmado", "true")).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void adminRenderizaListaEDetalhe() throws Exception {
        when(service.listar(any(AdminPedidosService.FiltroPedidos.class), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));
        when(service.detalhar(7L)).thenReturn(new AdminPedidosService.Detalhe(7L, 1L, Instant.parse("2026-01-01T12:00:00Z"),
                "01/01/2026 às 09:00", StatusPedido.AGUARDANDO_PAGAMENTO, TipoEntrega.ENTREGA, "Cliente Teste",
                "cliente@exemplo.com", List.of(), List.of(), List.of(), BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, "Rua teste, 1", false, null, null, null, false,
                List.of(StatusPedido.CANCELADO)));

        mvc.perform(get("/admin/pedidos").with(admin())).andExpect(status().isOk());
        mvc.perform(get("/admin/pedidos/7").with(admin())).andExpect(status().isOk());
    }

    @Test
    void adminPrecisaDeCsrfEComandosUsamAdministradorDaSessao() throws Exception {
        mvc.perform(post("/admin/pedidos/7/status").with(admin()).param("versao", "1")
                .param("novoStatus", "CANCELADO")).andExpect(status().isForbidden());

        mvc.perform(post("/admin/pedidos/7/status").with(admin()).with(csrf()).param("versao", "1")
                .param("novoStatus", "CANCELADO")).andExpect(redirectedUrl("/admin/pedidos/7"));
        verify(service).transicionar(7L, 1L, StatusPedido.CANCELADO, 1L, null);
    }
}
