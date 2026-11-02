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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.farmacia.farmaciaonline.TestcontainersConfiguration;

/** F-20 sem o perfil demo: não há provedor, então os botões somem e as rotas respondem 404. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("sem-demo")
class LoginSocialSemDemoTest {

    @Autowired
    private MockMvc mvc;

    @Test
    @DisplayName("sem perfil demo: rotas do login social dão 404 e o login não mostra os botões")
    void semProvedor() throws Exception {
        mvc.perform(get("/login/social/google")).andExpect(status().isNotFound());
        mvc.perform(get("/login/social/demo/google/consentimento").param("state", "x"))
                .andExpect(status().isNotFound());
        String login = mvc.perform(get("/login")).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        assertThat(login).doesNotContain("/login/social/");
    }
}
