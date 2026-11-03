package com.farmacia.farmaciaonline.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import com.farmacia.farmaciaonline.TestcontainersConfiguration;
import com.farmacia.farmaciaonline.model.Usuario;
import com.farmacia.farmaciaonline.repository.UsuarioRepository;

/**
 * Regressões da F-18 (proteção do login, sessão e logout) contra o seed e o PostgreSQL real. Não é @Transactional
 * (o handler grava em outra transação), então o cliente do seed é limpo antes e depois de cada teste.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class LoginProtecaoIntegracaoTest {

    private static final String SENHA_DO_SEED = "Farma@123";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UsuarioRepository usuarios;

    @Value("${server.servlet.session.timeout}")
    private Duration tempoDaSessao;

    @BeforeEach
    @AfterEach
    void limparFalhasDoCliente() {
        Usuario cliente = usuarios.findByEmailIgnoreCase("cliente@farma.com").orElseThrow();
        cliente.setTentativasLoginFalhas(0);
        cliente.setBloqueadoAte(null);
        usuarios.saveAndFlush(cliente);
    }

    private Usuario cliente() {
        return usuarios.findByEmailIgnoreCase("cliente@farma.com").orElseThrow();
    }

    private MockHttpSession entrar(String identificador) throws Exception {
        return (MockHttpSession) mvc.perform(formLogin("/login").user("identificador", identificador)
                .password("senha", SENHA_DO_SEED)).andReturn().getRequest().getSession(false);
    }

    private void senhaErrada(String identificador) throws Exception {
        mvc.perform(formLogin("/login").user("identificador", identificador).password("senha", "errada"))
                .andExpect(redirectedUrl("/login?erro=credenciais"));
    }

    // ---------- contagem de falhas ----------

    @Autowired
    private LimiteDeTentativasEmMemoria tentativasEmMemoria;

    /** As 6 respostas seguidas a uma senha errada: o motivo de cada redirecionamento. */
    private java.util.List<String> seisSenhasErradas(String identificador) throws Exception {
        java.util.List<String> motivos = new java.util.ArrayList<>();
        for (int i = 0; i < 6; i++) {
            motivos.add(mvc.perform(formLogin("/login").user("identificador", identificador).password("senha", "errada"))
                    .andReturn().getResponse().getRedirectedUrl());
        }
        return motivos;
    }

    @Test
    @DisplayName("o bloqueio não revela se a conta existe: conta real e e-mail inexistente dão a mesma sequência")
    void bloqueioIgualParaContaRealEInexistente() throws Exception {
        try {
            java.util.List<String> real = seisSenhasErradas("cliente@farma.com");
            java.util.List<String> inexistente = seisSenhasErradas("ninguem-bloqueio@teste.com");

            assertThat(real).containsExactly("/login?erro=credenciais", "/login?erro=credenciais",
                    "/login?erro=credenciais", "/login?erro=credenciais", "/login?erro=credenciais",
                    "/login?erro=bloqueada");
            assertThat(inexistente).isEqualTo(real);
        } finally {
            tentativasEmMemoria.limparTudo();
        }
    }

    @Test
    @DisplayName("quatro senhas incorretas ainda não bloqueiam; a quinta falha conta pelo CPF também")
    void quatroFalhasNaoBloqueiam() throws Exception {
        for (int i = 0; i < 3; i++) {
            senhaErrada("cliente@farma.com");
        }
        senhaErrada("529.982.247-25");

        assertThat(cliente().getTentativasLoginFalhas()).isEqualTo(4);
        assertThat(cliente().getBloqueadoAte()).isNull();
        mvc.perform(formLogin("/login").user("identificador", "cliente@farma.com").password("senha", SENHA_DO_SEED))
                .andExpect(redirectedUrl("/"));
    }

    @Test
    @DisplayName("bloqueio vencido não impede o login e uma nova senha errada recomeça a contagem")
    void bloqueioVencidoRecomecaAContagem() throws Exception {
        Usuario c = cliente();
        c.setTentativasLoginFalhas(5);
        c.setBloqueadoAte(Instant.now().minusSeconds(60));
        usuarios.saveAndFlush(c);

        senhaErrada("cliente@farma.com");

        assertThat(cliente().getTentativasLoginFalhas()).isEqualTo(1);
        assertThat(cliente().getBloqueadoAte()).isNull();
    }

    @Test
    @DisplayName("conta pendente e identificador inexistente não acumulam falhas")
    void pendenteEInexistenteNaoContam() throws Exception {
        for (int i = 0; i < 6; i++) {
            mvc.perform(formLogin("/login").user("identificador", "pendente@farma.com").password("senha", "errada"));
            mvc.perform(formLogin("/login").user("identificador", "ninguem@exemplo.com").password("senha", "errada"));
        }

        Usuario pendente = usuarios.findByEmailIgnoreCase("pendente@farma.com").orElseThrow();
        assertThat(pendente.getTentativasLoginFalhas()).isZero();
        assertThat(pendente.getBloqueadoAte()).isNull();
    }

    @Test
    @DisplayName("campos extras de perfil e status no formulário de login são ignorados")
    void camposDePerfilNoLoginSaoIgnorados() throws Exception {
        MockHttpSession sessao = (MockHttpSession) mvc.perform(post("/login").with(csrf())
                .param("identificador", "cliente@farma.com").param("senha", SENHA_DO_SEED)
                .param("perfil", "ADMIN").param("role", "ROLE_ADMIN").param("status", "ATIVA"))
                .andExpect(redirectedUrl("/")).andReturn().getRequest().getSession(false);

        mvc.perform(get("/admin").session(sessao)).andExpect(status().isForbidden());
        assertThat(cliente().getPerfil().name()).isEqualTo("CLIENTE");
    }

    // ---------- sessão e logout ----------

    @Test
    @DisplayName("a sessão expira por inatividade em 30 minutos")
    void sessaoExpiraEm30Minutos() {
        assertThat(tempoDaSessao).isEqualTo(Duration.ofMinutes(30));
    }

    @Test
    @DisplayName("logout numa aba não derruba a outra sessão da conta; a sessão encerrada volta ao login")
    void logoutEncerraSoASessaoQueSaiu() throws Exception {
        MockHttpSession abaA = entrar("cliente@farma.com");
        MockHttpSession abaB = entrar("cliente@farma.com");
        mvc.perform(get("/conta").session(abaA)).andExpect(status().isOk());

        mvc.perform(post("/logout").with(csrf()).session(abaA)).andExpect(redirectedUrl("/login?logout"));

        mvc.perform(get("/conta").session(abaA)).andExpect(redirectedUrlPattern("**/login"));
        mvc.perform(get("/conta").session(abaB)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("página protegida não pode ser guardada em cache (voltar não restaura depois do logout)")
    void paginaProtegidaNaoVaiParaOCache() throws Exception {
        MockHttpSession sessao = entrar("cliente@farma.com");

        String cache = mvc.perform(get("/conta").session(sessao)).andExpect(status().isOk())
                .andReturn().getResponse().getHeader("Cache-Control");

        assertThat(cache).contains("no-store");
    }

    // ---------- rotas ----------

    @ParameterizedTest
    @ValueSource(strings = {"/conta", "/conta/contato/email", "/conta/contato/celular", "/enderecos", "/checkout",
            "/admin", "/admin/clientes", "/admin/pedidos"})
    @DisplayName("rotas autenticadas mandam quem não entrou para o login")
    void rotasAutenticadasExigemLogin(String rota) throws Exception {
        mvc.perform(get(rota)).andExpect(redirectedUrlPattern("**/login"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/login", "/cadastro", "/recuperar-senha"})
    @DisplayName("telas de entrada, cadastro e recuperação são públicas")
    void rotasPublicas(String rota) throws Exception {
        mvc.perform(get(rota)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST sem CSRF é recusado no login e no logout")
    void postsSemCsrfSaoRecusados() throws Exception {
        mvc.perform(post("/login").param("identificador", "cliente@farma.com").param("senha", SENHA_DO_SEED))
                .andExpect(status().isForbidden());
        mvc.perform(post("/logout")).andExpect(status().isForbidden());
    }

    // ---------- retorno depois do cadastro ----------

    @Test
    @DisplayName("quem se cadastrou a partir do checkout volta ao /checkout depois do login")
    void loginVoltaAoCheckoutDepoisDoCadastro() throws Exception {
        mvc.perform(post("/login").with(csrf()).sessionAttr("retornoCadastro", "checkout")
                .param("identificador", "cliente@farma.com").param("senha", SENHA_DO_SEED))
                .andExpect(redirectedUrl("/checkout"));
    }

    @Test
    @DisplayName("o link de cadastro do cabeçalho leva o retorno ao carrinho só quando a pessoa está no carrinho")
    void cabecalhoCadastroLevaRetornoDoCarrinho() throws Exception {
        String noCarrinho = mvc.perform(get("/carrinho")).andReturn().getResponse().getContentAsString();
        String noLogin = mvc.perform(get("/login")).andReturn().getResponse().getContentAsString();

        assertThat(noCarrinho).contains("/cadastro?retorno=carrinho").contains("/login?retorno=carrinho");
        assertThat(noLogin).contains("href=\"/cadastro\"").doesNotContain("retorno=carrinho");
    }

    @Test
    @DisplayName("entrar pelo cabeçalho do carrinho devolve ao carrinho; retorno desconhecido é ignorado")
    void loginVoltaAoCarrinhoDepoisDeEntrarPeloCabecalho() throws Exception {
        MockHttpSession sessao = (MockHttpSession) mvc.perform(get("/login").param("retorno", "carrinho"))
                .andReturn().getRequest().getSession(false);
        mvc.perform(post("/login").with(csrf()).session(sessao)
                .param("identificador", "cliente@farma.com").param("senha", SENHA_DO_SEED))
                .andExpect(redirectedUrl("/carrinho"));

        MockHttpSession outra = (MockHttpSession) mvc.perform(get("/login").param("retorno", "https://x.example"))
                .andReturn().getRequest().getSession(false);
        assertThat(outra == null || outra.getAttribute("retornoCadastro") == null).isTrue();
    }
}
