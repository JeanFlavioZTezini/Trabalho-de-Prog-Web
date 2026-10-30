package com.farmacia.farmaciaonline.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Controller;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import com.farmacia.farmaciaonline.config.SecurityConfig;
import com.farmacia.farmaciaonline.repository.CategoriaRepository;

/**
 * Confere o conteúdo das páginas de erro (templates/error): saem no layout do site, têm um único
 * título e não mostram detalhes técnicos. Sem banco e sem Docker.
 *
 * O @WebMvcTest não monta o resolvedor de páginas de erro do Spring Boot (o /error cai na página
 * branca padrão), então um controller de teste devolve cada template como uma view comum.
 * Que o Boot escolhe o arquivo pelo status (error/404.html para 404 etc.) se confere abrindo uma
 * página que não existe na aplicação rodando.
 */
@WebMvcTest(PaginasDeErroTest.PaginasDeErroDeTeste.class)
@Import({SecurityConfig.class, PaginasDeErroTest.PaginasDeErroDeTeste.class})
class PaginasDeErroTest {

    /** Rota pública (/institucional/**) que só existe nos testes. */
    @Controller
    static class PaginasDeErroDeTeste {

        @GetMapping("/institucional/teste-erro/{pagina}")
        String pagina(@PathVariable("pagina") String pagina) {
            return "error/" + pagina;
        }
    }

    @MockBean
    private CategoriaRepository categoriaRepository;

    @Autowired
    private MockMvc mvc;

    @ParameterizedTest(name = "error/{0}.html mostra \"{1}\"")
    @CsvSource({
            "403, Acesso negado",
            "404, Página não encontrada",
            "5xx, Algo deu errado"
    })
    void paginaDeErroUsaOLayoutETemUmUnicoTitulo(String pagina, String titulo) throws Exception {
        String html = mvc.perform(get("/institucional/teste-erro/" + pagina))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(html).contains("<h1>" + titulo + "</h1>");
        assertThat(html).contains("<main id=\"conteudo\"");
        assertThat(html).contains("class=\"skip-link\"");
        assertThat(html).contains("href=\"/\"");
        assertThat(StringUtils.countOccurrencesOf(html, "<h1")).isEqualTo(1);
        assertThat(html).doesNotContain("Exception").doesNotContain("trace");
    }
}
