package com.farmacia.farmaciaonline.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.farmacia.farmaciaonline.TestcontainersConfiguration;

/**
 * O card compartilhado (fragments/produto-card.html, F-01) leva o formulário do carrinho (F-10) na home e no catálogo.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class CardCarrinhoIntegracaoTest {

    @Autowired
    private MockMvc mvc;

    private String pagina(String url) throws Exception {
        return mvc.perform(get(url))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("a home mostra o botão Adicionar ao carrinho, dentro do card, enviando por POST com o token CSRF")
    void homeTemBotaoNoCard() throws Exception {
        String html = pagina("/");

        assertThat(html).contains("class=\"card-carrinho\"");
        assertThat(html).contains("action=\"/carrinho/itens\"");
        assertThat(html).contains("name=\"apresentacaoId\"");
        assertThat(html).contains("name=\"_csrf\"");
        // o formulário fica dentro do article.card, não solto na grade
        assertThat(html).containsPattern("(?s)<article class=\"card\">.*?class=\"card-carrinho\".*?</article>");
    }

    @Test
    @DisplayName("o catálogo (/produtos) usa o mesmo card e também tem o botão")
    void catalogoTemBotaoNoCard() throws Exception {
        assertThat(pagina("/produtos")).contains("class=\"card-carrinho\"");
    }
}
