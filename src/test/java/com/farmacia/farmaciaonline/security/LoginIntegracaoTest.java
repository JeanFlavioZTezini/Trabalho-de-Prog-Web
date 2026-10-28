package com.farmacia.farmaciaonline.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.FormLoginRequestBuilder;
import org.springframework.test.web.servlet.MockMvc;

import com.farmacia.farmaciaonline.TestcontainersConfiguration;

/**
 * Login de verdade contra o seed (V2__seed.sql), num PostgreSQL descartável: confere os critérios
 * de aceite da F1-09. Precisa do Docker ligado, como os outros testes com banco.
 *
 * O {@code formLogin()} do spring-security-test já manda o token CSRF junto.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class LoginIntegracaoTest {

    private static final String SENHA_DO_SEED = "Farma@123";

    @Autowired
    private MockMvc mvc;

    private static FormLoginRequestBuilder login(String identificador, String senha) {
        // os nomes dos campos do nosso formulário: "identificador" (e-mail ou CPF) e "senha"
        return formLogin("/login")
                .user("identificador", identificador)
                .password("senha", senha);
    }

    @Test
    @DisplayName("admin@farma.com entra e vai para /admin")
    void adminEntraEVaiParaOPainel() throws Exception {
        mvc.perform(login("admin@farma.com", SENHA_DO_SEED))
                .andExpect(redirectedUrl("/admin"))
                .andExpect(authenticated().withRoles("ADMIN"));
    }

    @Test
    @DisplayName("cliente@farma.com entra e vai para a página inicial")
    void clienteEntraPorEmail() throws Exception {
        mvc.perform(login("cliente@farma.com", SENHA_DO_SEED))
                .andExpect(redirectedUrl("/"))
                .andExpect(authenticated().withRoles("CLIENTE"));
    }

    @ParameterizedTest(name = "cliente entra pelo CPF digitado como \"{0}\"")
    @ValueSource(strings = {"52998224725", "529.982.247-25", " 529.982.247-25 "})
    void clienteEntraPorCpfComOuSemMascara(String cpf) throws Exception {
        mvc.perform(login(cpf, SENHA_DO_SEED))
                .andExpect(redirectedUrl("/"))
                .andExpect(authenticated().withRoles("CLIENTE"));
    }

    @Test
    @DisplayName("E-mail em maiúsculas também entra")
    void emailEmMaiusculas() throws Exception {
        mvc.perform(login("CLIENTE@FARMA.COM", SENHA_DO_SEED))
                .andExpect(redirectedUrl("/"))
                .andExpect(authenticated().withRoles("CLIENTE"));
    }

    @Test
    @DisplayName("pendente@farma.com não consegue entrar")
    void contaPendenteNaoEntra() throws Exception {
        mvc.perform(login("pendente@farma.com", SENHA_DO_SEED))
                .andExpect(redirectedUrl("/login?erro=pendente"))
                .andExpect(unauthenticated());
    }

    @Test
    @DisplayName("A conta pendente também não entra pelo CPF")
    void contaPendenteNaoEntraPeloCpf() throws Exception {
        mvc.perform(login("111.444.777-35", SENHA_DO_SEED))
                .andExpect(redirectedUrl("/login?erro=pendente"))
                .andExpect(unauthenticated());
    }

    @Test
    void senhaErradaNaoEntra() throws Exception {
        mvc.perform(login("cliente@farma.com", "senha-errada"))
                .andExpect(redirectedUrl("/login?erro=credenciais"))
                .andExpect(unauthenticated());
    }

    @Test
    void usuarioInexistenteNaoEntraENaoSeDiferenciaDeSenhaErrada() throws Exception {
        mvc.perform(login("ninguem@farma.com", SENHA_DO_SEED))
                .andExpect(redirectedUrl("/login?erro=credenciais"))
                .andExpect(unauthenticated());
    }

    @Test
    @DisplayName("O cliente logado recebe 403 em /admin")
    void clienteLogadoRecebe403EmAdmin() throws Exception {
        MockHttpSession sessao = (MockHttpSession) mvc.perform(login("cliente@farma.com", SENHA_DO_SEED))
                .andReturn().getRequest().getSession(false);

        mvc.perform(get("/admin").session(sessao)).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("O admin logado abre /admin")
    void adminLogadoAbreAdmin() throws Exception {
        MockHttpSession sessao = (MockHttpSession) mvc.perform(login("admin@farma.com", SENHA_DO_SEED))
                .andReturn().getRequest().getSession(false);

        mvc.perform(get("/admin").session(sessao)).andExpect(status().isOk());
    }
}
