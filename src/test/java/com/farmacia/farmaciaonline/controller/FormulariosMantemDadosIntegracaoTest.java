package com.farmacia.farmaciaonline.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import com.farmacia.farmaciaonline.TestcontainersConfiguration;

/** Login, retomada da confirmação e recuperação de senha voltam com o "E-mail ou CPF" digitado; a senha, nunca. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class FormulariosMantemDadosIntegracaoTest {

    @Autowired
    private MockMvc mvc;

    private String html(RequestBuilder requisicao) throws Exception {
        return mvc.perform(requisicao).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private static String campo(String html, String id) {
        Matcher achado = Pattern.compile("<input[^>]*id=\"" + id + "\"[^>]*>").matcher(html);
        assertThat(achado.find()).as("campo " + id).isTrue();
        return achado.group();
    }

    @Test
    @DisplayName("login recusado volta com o e-mail digitado, fora da URL e só uma vez; a senha volta vazia")
    void loginRecusadoMantemIdentificador() throws Exception {
        MockHttpSession sessao = new MockHttpSession();
        mvc.perform(post("/login").session(sessao).with(csrf())
                        .param("identificador", "ninguem-login@exemplo.com").param("senha", "Errada123"))
                .andExpect(redirectedUrl("/login?erro=credenciais"));

        String tela = html(get("/login").param("erro", "credenciais").session(sessao));
        assertThat(campo(tela, "identificador")).contains("value=\"ninguem-login@exemplo.com\"");
        assertThat(campo(tela, "senha")).doesNotContainPattern("value=\"[^\"]+\"");
        assertThat(tela).doesNotContain("Errada123");

        String deNovo = html(get("/login").session(sessao));
        assertThat(campo(deNovo, "identificador")).doesNotContainPattern("value=\"[^\"]+\"");
    }

    @Test
    @DisplayName("retomada recusada volta com o e-mail ou CPF digitado e a senha vazia")
    void retomadaRecusadaMantemIdentificador() throws Exception {
        String tela = html(post("/confirmar-conta/retomar").with(csrf())
                .param("identificador", "pendente@farma.com").param("senha", "Errada123"));

        assertThat(tela).contains("Não encontramos uma conta pendente com esses dados.");
        assertThat(campo(tela, "identificador")).contains("value=\"pendente@farma.com\"");
        assertThat(campo(tela, "senha")).doesNotContainPattern("value=\"[^\"]+\"");
    }

    @Test
    @DisplayName("recuperação com 'aguarde' volta com o e-mail ou CPF e o canal escolhidos")
    void recuperacaoAguardeMantemIdentificadorECanal() throws Exception {
        String identificador = "ninguem-recupera@exemplo.com";
        mvc.perform(post("/recuperar-senha").with(csrf()).param("identificador", identificador).param("canal", "SMS"));
        MvcResult aguarde = mvc.perform(post("/recuperar-senha").with(csrf())
                        .param("identificador", identificador).param("canal", "SMS"))
                .andExpect(redirectedUrl("/recuperar-senha"))
                .andReturn();

        String tela = html(get("/recuperar-senha").flashAttrs(aguarde.getFlashMap()));
        assertThat(tela).contains("Aguarde");
        assertThat(campo(tela, "identificador")).contains("value=\"" + identificador + "\"");
        assertThat(campo(tela, "canal-sms")).contains("checked");
        assertThat(campo(tela, "canal-email")).doesNotContain("checked");
    }
}
