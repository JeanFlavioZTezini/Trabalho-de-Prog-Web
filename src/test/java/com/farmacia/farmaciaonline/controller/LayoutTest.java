package com.farmacia.farmaciaonline.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.StringUtils;

import com.farmacia.farmaciaonline.config.SecurityConfig;

/**
 * Garante que todas as páginas usam o mesmo layout (templates/fragments/layout.html)
 * e que os pontos de acessibilidade do layout não regridem.
 * Não precisa de banco: sobe só a camada web.
 */
@WebMvcTest({HomeController.class, InstitucionalController.class})
@Import(SecurityConfig.class)
class LayoutTest {

    @Autowired
    private MockMvc mvc;

    private String pagina(String url) throws Exception {
        return mvc.perform(get(url))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/", "/institucional/boas-praticas"})
    void paginaUsaOLayoutComLandmarksUnicos(String url) throws Exception {
        String html = pagina(url);

        assertThat(html).contains("lang=\"pt-BR\"");
        assertThat(StringUtils.countOccurrencesOf(html, "<header")).isEqualTo(1);
        assertThat(StringUtils.countOccurrencesOf(html, "<footer")).isEqualTo(1);
        assertThat(StringUtils.countOccurrencesOf(html, "<main id=\"conteudo\"")).isEqualTo(1);
        assertThat(StringUtils.countOccurrencesOf(html, "<h1")).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/", "/institucional/boas-praticas"})
    void primeiroElementoFocavelEOLinkParaPularParaOConteudo(String url) throws Exception {
        String html = pagina(url);

        assertThat(html).contains("class=\"skip-link\"");
        assertThat(html).contains("href=\"#conteudo\"");
        // o skip-link vem antes do cabeçalho, logo é o primeiro Tab
        assertThat(html.indexOf("class=\"skip-link\"")).isLessThan(html.indexOf("<header"));
    }

    @Test
    void cabecalhoTemBuscaAcessivelEMenuDeCategorias() throws Exception {
        String html = pagina("/");

        assertThat(html).contains("role=\"search\"");
        assertThat(html).contains("action=\"/produtos\"");
        assertThat(html).contains("for=\"busca\"");
        assertThat(html).contains("name=\"q\"");
        assertThat(html).contains("aria-label=\"Categorias\"");
        assertThat(html).contains("aria-controls=\"menu-categorias\"");
        assertThat(html).contains("id=\"menu-categorias\"");
    }

    @Test
    void visitanteVeConviteParaEntrarECadastrar() throws Exception {
        String html = pagina("/");

        assertThat(html).contains("Seja bem-vindo!");
        assertThat(html).contains("href=\"/login\"");
        assertThat(html).contains("href=\"/cadastro\"");
    }

    @Test
    @WithMockUser(username = "maria")
    void usuarioLogadoVeOProprioNomeEnaoOConvite() throws Exception {
        String html = pagina("/");

        assertThat(html).contains("maria");
        assertThat(html).doesNotContain("Seja bem-vindo!");
    }

    @Test
    void linksDoRodapeApontamParaSecoesQueExistem() throws Exception {
        String home = pagina("/");
        String boasPraticas = pagina("/institucional/boas-praticas");

        for (String ancora : new String[] {
                "rdc-44-2009", "uso-responsavel", "receitas", "privacidade", "trocas"}) {
            assertThat(home).contains("href=\"/institucional/boas-praticas#" + ancora + "\"");
            assertThat(boasPraticas).contains("id=\"" + ancora + "\"");
        }
    }
}
