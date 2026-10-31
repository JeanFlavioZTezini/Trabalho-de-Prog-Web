package com.farmacia.farmaciaonline.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.farmacia.farmaciaonline.config.SecurityConfig;
import com.farmacia.farmaciaonline.gateway.demo.DemoGatewayAdapter;
import com.farmacia.farmaciaonline.repository.CategoriaRepository;
import com.farmacia.farmaciaonline.service.PagamentoPedidoService;

/** Sem o perfil demo, os botões que simulam o pagamento não existem. */
@WebMvcTest(CheckoutDemoController.class)
@ActiveProfiles("sem-demo")
@Import(SecurityConfig.class)
class CheckoutDemoSemPerfilTest {

    @MockBean
    private CategoriaRepository categoriaRepository;

    @MockBean
    private DemoGatewayAdapter gateway;

    @MockBean
    private PagamentoPedidoService pagamentos;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ApplicationContext contexto;

    @Test
    @WithMockUser(roles = "CLIENTE")
    void semOPerfilDemoOsBotoesNaoExistem() throws Exception {
        assertThat(contexto.getBeansOfType(CheckoutDemoController.class)).isEmpty();

        mvc.perform(post("/checkout/pedidos/1/demo/aprovar").with(csrf())).andExpect(status().isNotFound());
    }
}
