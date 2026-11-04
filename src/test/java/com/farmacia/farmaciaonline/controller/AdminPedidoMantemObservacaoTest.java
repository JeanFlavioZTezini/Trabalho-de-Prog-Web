package com.farmacia.farmaciaonline.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.farmacia.farmaciaonline.config.SecurityConfig;
import com.farmacia.farmaciaonline.model.enums.Perfil;
import com.farmacia.farmaciaonline.model.enums.StatusPedido;
import com.farmacia.farmaciaonline.model.enums.TipoEntrega;
import com.farmacia.farmaciaonline.repository.CategoriaRepository;
import com.farmacia.farmaciaonline.security.UsuarioAutenticado;
import com.farmacia.farmaciaonline.service.AdminPedidosService;
import com.farmacia.farmaciaonline.service.ConflitoPedidoException;

/** UC17: troca de status recusada (conflito) volta com a observação e a situação escolhidas. */
@WebMvcTest(AdminPedidoController.class)
@Import(SecurityConfig.class)
class AdminPedidoMantemObservacaoTest {

    @MockBean private AdminPedidosService service;
    @MockBean private CategoriaRepository categoriaRepository; // NavegacaoAdvice monta o menu no cabeçalho.
    @Autowired private MockMvc mvc;

    private static RequestPostProcessor admin() {
        return user(new UsuarioAutenticado(1L, "Admin", Perfil.ADMIN, "admin@farma.com", "x"));
    }

    @Test
    @DisplayName("conflito na troca de status devolve a observação e a situação escolhidas")
    void conflitoMantemObservacao() throws Exception {
        doThrow(new ConflitoPedidoException("O pedido foi alterado por outra pessoa."))
                .when(service).transicionar(anyLong(), anyLong(), any(), anyLong(), any());
        when(service.detalhar(7L)).thenReturn(new AdminPedidosService.Detalhe(7L, 1L, Instant.parse("2026-01-01T12:00:00Z"),
                "01/01/2026 às 09:00", StatusPedido.AGUARDANDO_PAGAMENTO, TipoEntrega.ENTREGA, "Cliente Teste",
                "cliente@exemplo.com", List.of(), List.of(), List.of(), BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, "Rua teste, 1", false, null, null, null, false,
                List.of(StatusPedido.CANCELADO)));

        MvcResult resultado = mvc.perform(post("/admin/pedidos/7/status").with(admin()).with(csrf())
                        .param("versao", "1").param("novoStatus", "CANCELADO")
                        .param("observacao", "Cliente pediu por telefone"))
                .andExpect(redirectedUrl("/admin/pedidos/7"))
                .andExpect(flash().attribute("observacao", "Cliente pediu por telefone"))
                .andReturn();

        String tela = mvc.perform(get("/admin/pedidos/7").with(admin()).flashAttrs(resultado.getFlashMap()))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(tela).contains("O pedido foi alterado por outra pessoa.")
                .containsPattern("<textarea[^>]*id=\"observacao\"[^>]*>Cliente pediu por telefone</textarea>")
                .containsPattern("<option value=\"CANCELADO\" selected=\"selected\">");
    }
}
