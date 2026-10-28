package com.farmacia.farmaciaonline.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.StringUtils;

import com.farmacia.farmaciaonline.config.SecurityConfig;

/** Conteúdo e acessibilidade da página /login (F1-09). Sem banco e sem Docker. */
@WebMvcTest(LoginController.class)
@Import(SecurityConfig.class)
class LoginPageTest {

    @Autowired
    private MockMvc mvc;

    private String pagina(String url) throws Exception {
        return mvc.perform(get(url))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    void camposTemRotuloLigadoAoCampo() throws Exception {
        String html = pagina("/login");

        assertThat(html).contains("<label for=\"identificador\"");
        assertThat(html).contains("id=\"identificador\"");
        assertThat(html).contains("name=\"identificador\"");
        assertThat(html).contains("<label for=\"senha\"");
        assertThat(html).contains("id=\"senha\"");
        assertThat(html).contains("type=\"password\"");
        assertThat(StringUtils.countOccurrencesOf(html, "<h1")).isEqualTo(1);
    }

    @Test
    void formularioPostaParaLogin() throws Exception {
        String html = pagina("/login");

        assertThat(html).contains("action=\"/login\"");
        assertThat(html).contains("method=\"post\"");
    }

    @Test
    void semErroNemLogoutNaoMostraAvisos() throws Exception {
        String html = pagina("/login");

        assertThat(html).doesNotContain("role=\"alert\"");
        assertThat(html).doesNotContain("role=\"status\"");
    }

    @Test
    void erroDeCredenciaisAparecemComRoleAlert() throws Exception {
        String html = pagina("/login?erro=credenciais");

        assertThat(html).contains("role=\"alert\"");
        assertThat(html).contains("E-mail, CPF ou senha incorretos");
    }

    @Test
    void contaPendenteTemMensagemPropria() throws Exception {
        String html = pagina("/login?erro=pendente");

        assertThat(html).contains("role=\"alert\"");
        assertThat(html).contains("ainda não foi confirmada");
    }

    @Test
    void contaBloqueadaTemMensagemPropria() throws Exception {
        String html = pagina("/login?erro=bloqueada");

        assertThat(html).contains("temporariamente bloqueada");
    }

    @Test
    void avisoDeLogoutUsaRoleStatus() throws Exception {
        String html = pagina("/login?logout");

        assertThat(html).contains("role=\"status\"");
        assertThat(html).contains("Você saiu da sua conta");
        assertThat(html).doesNotContain("role=\"alert\"");
    }

    @Test
    void textoDaUrlNaoEhRepetidoNaPagina() throws Exception {
        String html = pagina("/login?erro=%3Cscript%3Ealert(1)%3C/script%3E");

        assertThat(html).doesNotContain("<script>alert(1)");
        assertThat(html).contains("E-mail, CPF ou senha incorretos");
    }
}
