package com.farmacia.farmaciaonline.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import com.farmacia.farmaciaonline.config.SecurityConfig;
import com.farmacia.farmaciaonline.gateway.demo.DemoGatewayAdapter;
import com.farmacia.farmaciaonline.gateway.dto.RespostaPagamento;
import com.farmacia.farmaciaonline.model.enums.StatusPagamento;
import com.farmacia.farmaciaonline.repository.CategoriaRepository;
import com.farmacia.farmaciaonline.repository.ProdutoRepository;

@WebMvcTest(DemoPagamentoController.class)
@Import(SecurityConfig.class)
class DemoPagamentoControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private DemoGatewayAdapter demoGatewayAdapter;

    @MockBean
    private CategoriaRepository categoriaRepository;

    @MockBean
    private ProdutoRepository produtoRepository;

    @Test
    @DisplayName("visitante não autenticado não consegue forçar status de pagamento demo")
    void visitanteNaoAcessaEndpointDemo() throws Exception {
        mvc.perform(post("/demo/pagamento/forcar-status")
                .with(csrf())
                .param("transacaoGatewayId", "PIX-123")
                .param("novoStatus", "APROVADO"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @WithMockUser(roles = "CLIENTE")
    @DisplayName("cliente comum recebe 403 Forbidden ao tentar forçar status demo")
    void clienteRecebe403() throws Exception {
        mvc.perform(post("/demo/pagamento/forcar-status")
                .with(csrf())
                .param("transacaoGatewayId", "PIX-123")
                .param("novoStatus", "APROVADO"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("post sem CSRF é recusado mesmo para admin")
    void postSemCsrfRecebe403() throws Exception {
        mvc.perform(post("/demo/pagamento/forcar-status")
                .param("transacaoGatewayId", "PIX-123")
                .param("novoStatus", "APROVADO"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("admin com CSRF consegue forçar status com sucesso")
    void adminConsegueForcarStatus() throws Exception {
        RespostaPagamento resposta = new RespostaPagamento(
                "PIX-123",
                StatusPagamento.APROVADO,
                "Pagamento aprovado (demo)",
                "DEMO-PIX-123",
                null,
                null,
                null,
                new BigDecimal("50.00"));

        when(demoGatewayAdapter.consultar("PIX-123")).thenReturn(resposta);

        mvc.perform(post("/demo/pagamento/forcar-status")
                .with(csrf())
                .param("transacaoGatewayId", "PIX-123")
                .param("novoStatus", "APROVADO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transacaoGatewayId").value("PIX-123"))
                .andExpect(jsonPath("$.status").value("APROVADO"));

        verify(demoGatewayAdapter).forcarStatus("PIX-123", StatusPagamento.APROVADO);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("se transação demo não existe retorna 400 Bad Request com erro")
    void transacaoInexistenteRetornaBadRequest() throws Exception {
        doThrow(new IllegalArgumentException("Transação demo não encontrada: PIX-999"))
                .when(demoGatewayAdapter).forcarStatus(eq("PIX-999"), any());

        mvc.perform(post("/demo/pagamento/forcar-status")
                .with(csrf())
                .param("transacaoGatewayId", "PIX-999")
                .param("novoStatus", "APROVADO"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("Transação demo não encontrada: PIX-999"));
    }
}
