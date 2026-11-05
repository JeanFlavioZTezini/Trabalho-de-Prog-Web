package com.farmacia.farmaciaonline.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import com.farmacia.farmaciaonline.TestcontainersConfiguration;
import com.farmacia.farmaciaonline.model.enums.Perfil;
import com.farmacia.farmaciaonline.security.UsuarioAutenticado;

/** O administrador não compra (checkout dá 403): endereços e cartões salvos também são só de cliente. Seed: admin id 1. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class AdminSemAreaDeCompraIntegracaoTest {

    @Autowired
    private org.springframework.test.web.servlet.MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    private static RequestPostProcessor admin() {
        return user(new UsuarioAutenticado(1L, "Admin", Perfil.ADMIN, "admin@farma.com", "x"));
    }

    @Test
    @DisplayName("admin recebe 403 em Meus endereços e Meus cartões, inclusive ao cadastrar endereço")
    void adminNaoTemEnderecosNemCartoes() throws Exception {
        mvc.perform(get("/enderecos").with(admin())).andExpect(status().isForbidden());
        mvc.perform(get("/enderecos/novo").with(admin())).andExpect(status().isForbidden());
        mvc.perform(get("/conta/cartoes").with(admin())).andExpect(status().isForbidden());
        mvc.perform(post("/enderecos").with(admin()).with(csrf())
                        .param("destinatario", "Admin").param("cep", "79002-000").param("logradouro", "Rua A")
                        .param("numero", "1").param("bairro", "Centro").param("cidade", "Campo Grande").param("uf", "MS"))
                .andExpect(status().isForbidden());

        assertThat(jdbc.queryForObject("select count(*) from endereco where usuario_id = 1", Integer.class)).isZero();
    }
}
