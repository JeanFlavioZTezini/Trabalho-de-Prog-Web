package com.farmacia.farmaciaonline.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.farmacia.farmaciaonline.config.SecurityConfig;
import com.farmacia.farmaciaonline.repository.CategoriaRepository;

/**
 * Sem o perfil demo, a caixa de mensagens de teste não existe: nenhum código aparece em tela.
 * O @ActiveProfiles troca o perfil demo do application.properties por outro.
 */
@WebMvcTest(CaixaDemoController.class)
@ActiveProfiles("sem-demo")
@Import(SecurityConfig.class)
class CaixaDemoSemPerfilTest {

    @MockBean
    private CategoriaRepository categoriaRepository;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ApplicationContext contexto;

    @Test
    void semOPerfilDemoACaixaNaoExiste() throws Exception {
        assertThat(contexto.getBeansOfType(CaixaDemoController.class)).isEmpty();

        mvc.perform(get("/caixa-demo")).andExpect(status().isNotFound());
    }
}
