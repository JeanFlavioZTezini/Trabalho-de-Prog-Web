package com.farmacia.farmaciaonline.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.StringUtils;

import com.farmacia.farmaciaonline.config.SecurityConfig;
import com.farmacia.farmaciaonline.mensagem.demo.DemoEnvioAdapter;
import com.farmacia.farmaciaonline.model.enums.CanalCodigo;
import com.farmacia.farmaciaonline.repository.CategoriaRepository;

/**
 * Caixa de mensagens de teste com o perfil demo (ligado no application.properties). Sem banco e sem Docker.
 */
@WebMvcTest(CaixaDemoController.class)
@Import({SecurityConfig.class, DemoEnvioAdapter.class})
class CaixaDemoControllerTest {

    @MockBean
    private CategoriaRepository categoriaRepository;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private DemoEnvioAdapter caixa;

    @Test
    void visitanteVeAsMensagensDaMaisRecenteParaAMaisAntiga() throws Exception {
        caixa.enviar(CanalCodigo.EMAIL, "maria@exemplo.com", "Assunto antigo", "Seu código do Farma++ é 111111.");
        caixa.enviar(CanalCodigo.SMS, "67999998888", "Assunto novo", "Seu código do Farma++ é 222222.");

        String html = mvc.perform(get("/caixa-demo")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(html).contains("<h1>Caixa de mensagens de teste</h1>").contains("nenhum e-mail ou SMS sai de verdade");
        assertThat(StringUtils.countOccurrencesOf(html, "<h1")).isEqualTo(1);
        assertThat(html).contains("SMS para 67999998888").contains("E-mail para maria@exemplo.com").contains("222222");
        assertThat(html.indexOf("Assunto novo")).isLessThan(html.indexOf("Assunto antigo"));
    }
}
