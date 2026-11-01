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
import com.farmacia.farmaciaonline.repository.CategoriaRepository;

/** Sem o perfil demo, nem o administrador consegue forçar o status de um pagamento. */
@WebMvcTest(DemoPagamentoController.class)
@ActiveProfiles("sem-demo")
@Import(SecurityConfig.class)
class DemoPagamentoSemPerfilTest {

    @MockBean
    private CategoriaRepository categoriaRepository;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ApplicationContext contexto;

    @Test
    @WithMockUser(roles = "ADMIN")
    void semOPerfilDemoARotaNaoExiste() throws Exception {
        assertThat(contexto.getBeansOfType(DemoPagamentoController.class)).isEmpty();

        mvc.perform(post("/demo/pagamento/forcar-status").with(csrf())
                        .param("transacaoGatewayId", "PIX-123")
                        .param("novoStatus", "APROVADO"))
                .andExpect(status().isNotFound());
    }
}
