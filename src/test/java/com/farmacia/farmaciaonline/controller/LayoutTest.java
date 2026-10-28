package com.farmacia.farmaciaonline.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;

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
import com.farmacia.farmaciaonline.model.Usuario;
import com.farmacia.farmaciaonline.model.enums.Perfil;
import com.farmacia.farmaciaonline.model.enums.StatusUsuario;
import com.farmacia.farmaciaonline.security.UsuarioAutenticado;

/**
 * Garante que todas as páginas usam o mesmo layout (templates/fragments/layout.html)
 * e que os pontos de acessibilidade do layout não regridem.
 * Não precisa de banco: sobe só a camada web.
 */
@WebMvcTest({HomeController.class, InstitucionalController.class, LoginController.class})
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
    @ValueSource(strings = {"/", "/institucional/boas-praticas", "/login"})
    void paginaUsaOLayoutComLandmarksUnicos(String url) throws Exception {
        String html = pagina(url);

        assertThat(html).contains("lang=\"pt-BR\"");
        assertThat(StringUtils.countOccurrencesOf(html, "<header")).isEqualTo(1);
        assertThat(StringUtils.countOccurrencesOf(html, "<footer")).isEqualTo(1);
        assertThat(StringUtils.countOccurrencesOf(html, "<main id=\"conteudo\"")).isEqualTo(1);
        assertThat(StringUtils.countOccurrencesOf(html, "<h1")).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/", "/institucional/boas-praticas", "/login"})
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
    void usuarioLogadoVeSoOPrimeiroNomeEOBotaoSair() throws Exception {
        Usuario usuario = Usuario.builder()
                .id(2L)
                .nome("Maria da Silva")
                .email("cliente@farma.com")
                .dataNascimento(LocalDate.of(1995, 5, 20))
                .senhaHash("hash")
                .perfil(Perfil.CLIENTE)
                .status(StatusUsuario.ATIVA)
                .termosVersao("1.0")
                .termosAceitosEm(Instant.now())
                .build();

        String html = mvc.perform(get("/").with(user(UsuarioAutenticado.de(usuario))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(html).contains("<strong>Maria</strong>");
        assertThat(html).doesNotContain("Maria da Silva");
        assertThat(html).doesNotContain("cliente@farma.com");
        assertThat(html).contains("action=\"/logout\"");
        assertThat(html).contains(">Sair</button>");
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
