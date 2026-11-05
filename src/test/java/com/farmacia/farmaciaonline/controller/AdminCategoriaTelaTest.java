package com.farmacia.farmaciaonline.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import com.farmacia.farmaciaonline.config.SecurityConfig;
import com.farmacia.farmaciaonline.repository.CategoriaRepository;
import com.farmacia.farmaciaonline.service.CategoriaService;

/** UC13: erros do formulário de categoria aparecem junto ao campo, em português. Sem banco. */
@WebMvcTest(AdminCategoriaController.class)
@Import(SecurityConfig.class)
@WithMockUser(roles = "ADMIN")
class AdminCategoriaTelaTest {

    @MockBean
    private CategoriaService service;

    @MockBean
    private CategoriaRepository categoriaRepository; // usado pelo NavegacaoAdvice (menu)

    @Autowired
    private MockMvc mvc;

    @Test
    @DisplayName("ordem grande demais (o campo number não tem teto): mensagem em português ligada ao campo, nada gravado")
    void ordemQueNaoCabeNoInteiro() throws Exception {
        String html = mvc.perform(post("/admin/categorias").with(csrf())
                        .param("nome", "Analgésicos").param("ordem", "99999999999"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(html).containsPattern("<p class=\"campo__erro\" role=\"alert\" id=\"ordem-erro\">"
                + "Informe um número inteiro válido \\(até 2.147.483.647\\).</p>");
        assertThat(html).containsPattern("<input type=\"number\" id=\"ordem\"[^>]*aria-invalid=\"true\"[^>]*"
                + "aria-describedby=\"ordem-erro\"");
        assertThat(html).doesNotContain("Failed to convert").doesNotContain("java.lang");
        verify(service, never()).salvar(any(), any());
    }

    @Test
    @DisplayName("categoria pai que não é número (envio adulterado): mensagem genérica no campo, nada gravado")
    void paiQueNaoEhNumero() throws Exception {
        String html = mvc.perform(post("/admin/categorias").with(csrf())
                        .param("nome", "Analgésicos").param("paiId", "abc").param("ordem", "1"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(html).contains("id=\"paiId-erro\">Valor inválido. Confira o campo e tente de novo.</p>");
        assertThat(html).doesNotContain("Failed to convert");
        verify(service, never()).salvar(any(), any());
    }

    @Test
    @DisplayName("nome em branco: erro no elemento nome-erro, ligado ao campo")
    void nomeEmBranco() throws Exception {
        String html = mvc.perform(post("/admin/categorias").with(csrf()).param("nome", "").param("ordem", "1"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(html).contains("id=\"nome-erro\">Informe o nome.</p>")
                .containsPattern("<input type=\"text\" id=\"nome\"[^>]*aria-describedby=\"nome-erro\"");
    }
}
