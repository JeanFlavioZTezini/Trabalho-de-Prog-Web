package com.farmacia.farmaciaonline.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import com.farmacia.farmaciaonline.TestcontainersConfiguration;
import com.farmacia.farmaciaonline.mensagem.demo.DemoEnvioAdapter;
import com.farmacia.farmaciaonline.model.IdentidadeSocial;
import com.farmacia.farmaciaonline.model.Usuario;
import com.farmacia.farmaciaonline.model.enums.Perfil;
import com.farmacia.farmaciaonline.model.enums.ProvedorSocial;
import com.farmacia.farmaciaonline.model.enums.StatusUsuario;
import com.farmacia.farmaciaonline.repository.IdentidadeSocialRepository;
import com.farmacia.farmaciaonline.repository.UsuarioRepository;
import com.farmacia.farmaciaonline.security.UsuarioAutenticado;

/**
 * F-20 de ponta a ponta com o provedor simulado (perfil demo, ligado no application.properties): botão, tela do
 * provedor, retorno com state, primeiro acesso, vínculo com CPF existente e login. Cada teste é desfeito no fim.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class LoginSocialIntegracaoTest {

    private static final String CPF_SEED = "52998224725";          // cliente@farma.com
    private static final String CPF_NOVO = "12345678909";
    private static final Pattern STATE = Pattern.compile("state=([^&]+)");
    private static final Pattern SEIS_DIGITOS = Pattern.compile("(\\d{6})");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UsuarioRepository usuarios;

    @Autowired
    private IdentidadeSocialRepository identidades;

    @Autowired
    private SessionRegistry sessionRegistry;

    @Autowired
    private DemoEnvioAdapter caixa;

    @Autowired
    private JdbcTemplate jdbc;

    /** Faz o caminho botão → provedor simulado → retorno e devolve a resposta do retorno. */
    private MvcResult viaProvedor(MockHttpSession sessao, String acao, String idExterno, String email, boolean verificado)
            throws Exception {
        String consentimento = mvc.perform(get("/login/social/google").session(sessao))
                .andReturn().getResponse().getRedirectedUrl();
        assertThat(consentimento).startsWith("/login/social/demo/google/consentimento");
        Matcher m = STATE.matcher(consentimento);
        assertThat(m.find()).isTrue();
        String retorno = mvc.perform(post("/login/social/demo/google/consentimento").session(sessao).with(csrf())
                        .param("state", m.group(1)).param("acao", acao).param("idExterno", idExterno)
                        .param("nome", "Pessoa Social").param("email", email == null ? "" : email)
                        .param("emailVerificado", String.valueOf(verificado)))
                .andReturn().getResponse().getRedirectedUrl();
        return mvc.perform(get(retorno).session(sessao)).andReturn();
    }

    private MvcResult completar(MockHttpSession sessao, String cpf, String nascimento, String email, String... extras)
            throws Exception {
        var requisicao = post("/login/social/completar").session(sessao).with(csrf())
                .param("nome", "Pessoa Social").param("cpf", cpf).param("dataNascimento", nascimento)
                .param("genero", "FEMININO").param("celular", "67999998888").param("aceiteTermos", "true");
        if (email != null) {
            requisicao.param("email", email);
        }
        for (int i = 0; i < extras.length; i += 2) {
            requisicao.param(extras[i], extras[i + 1]);
        }
        return mvc.perform(requisicao).andReturn();
    }

    private static boolean logado(MockHttpSession sessao) {
        return sessao.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY) != null;
    }

    private static String html(MvcResult resultado) throws Exception {
        return resultado.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("primeiro acesso com e-mail verificado cria conta ativa sem senha, vincula e entra; na volta entra direto")
    void primeiroAcessoEVolta() throws Exception {
        MockHttpSession sessao = new MockHttpSession();
        MvcResult retorno = viaProvedor(sessao, "permitir", "g-ana", "ana.social@teste.com", true);
        assertThat(retorno.getResponse().getRedirectedUrl()).isEqualTo("/login/social/completar");

        assertThat(html(mvc.perform(get("/login/social/completar").session(sessao)).andReturn()))
                .contains("<label for=\"cpf\"", "<label for=\"dataNascimento\"", "ana.social@teste.com")
                .doesNotContain("id=\"email\"");
        MvcResult fim = completar(sessao, CPF_NOVO, "1995-05-20", null);

        assertThat(fim.getResponse().getRedirectedUrl()).isEqualTo("/");
        assertThat(logado(sessao)).isTrue();
        Usuario conta = usuarios.findByCpf(CPF_NOVO).orElseThrow();
        assertThat(conta.getStatus()).isEqualTo(StatusUsuario.ATIVA);
        assertThat(conta.getPerfil()).isEqualTo(Perfil.CLIENTE);
        assertThat(conta.getSenhaHash()).isNull();
        assertThat(conta.getEmailConfirmadoEm()).isNotNull();
        assertThat(identidades.findByProvedorAndProvedorUsuarioId(ProvedorSocial.GOOGLE, "g-ana")).isPresent();
        // Registrada como no form login: é assim que a RevogacaoDeSessoes encontra a sessão (procura pelo id).
        assertThat(sessionRegistry.getAllPrincipals()).anyMatch(p -> p instanceof UsuarioAutenticado u
                && u.getId().equals(conta.getId()));

        MockHttpSession outraVisita = new MockHttpSession();
        MvcResult volta = viaProvedor(outraVisita, "permitir", "g-ana", "ana.social@teste.com", true);
        assertThat(volta.getResponse().getRedirectedUrl()).isEqualTo("/");
        assertThat(logado(outraVisita)).isTrue();
    }

    @Test
    @DisplayName("menor de 18 anos é recusado no primeiro acesso, com erro ligado ao campo")
    void menorDeIdade() throws Exception {
        MockHttpSession sessao = new MockHttpSession();
        viaProvedor(sessao, "permitir", "g-menor", "menor@teste.com", true);

        MvcResult fim = completar(sessao, CPF_NOVO, java.time.LocalDate.now().minusYears(17).toString(), null);

        assertThat(html(fim)).contains("id=\"dataNascimento-erro\"", "18 anos");
        assertThat(usuarios.findByCpf(CPF_NOVO)).isEmpty();
        assertThat(logado(sessao)).isFalse();
    }

    @Test
    @DisplayName("e-mail não verificado (ou ausente) cria conta pendente e leva à confirmação por código")
    void emailNaoVerificadoOuAusente() throws Exception {
        MockHttpSession sessao = new MockHttpSession();
        viaProvedor(sessao, "permitir", "g-sem-email", null, false);
        assertThat(html(mvc.perform(get("/login/social/completar").session(sessao)).andReturn()))
                .contains("<label for=\"email\"");

        MvcResult fim = completar(sessao, CPF_NOVO, "1990-01-01", "sem.email@teste.com");

        assertThat(fim.getResponse().getRedirectedUrl()).isEqualTo("/confirmar-conta");
        assertThat(logado(sessao)).isFalse();
        Usuario conta = usuarios.findByCpf(CPF_NOVO).orElseThrow();
        assertThat(conta.getStatus()).isEqualTo(StatusUsuario.PENDENTE);
        assertThat(conta.getEmailConfirmadoEm()).isNull();
        assertThat(sessao.getAttribute(CadastroController.SESSAO_CONTA)).isEqualTo(conta.getId());
    }

    @Test
    @DisplayName("CPF de conta existente exige comprovação; e-mail igual não basta; senha errada recusa, certa vincula")
    void cpfExistenteComSenha() throws Exception {
        MockHttpSession sessao = new MockHttpSession();
        viaProvedor(sessao, "permitir", "g-cliente", "cliente@farma.com", true);

        MvcResult fim = completar(sessao, CPF_SEED, "1990-01-01", null);
        assertThat(fim.getResponse().getRedirectedUrl()).isEqualTo("/login/social/vincular");
        assertThat(logado(sessao)).isFalse();
        assertThat(html(mvc.perform(get("/login/social/vincular").session(sessao)).andReturn()))
                .contains("c***@farma.com").doesNotContain("cliente@farma.com");

        mvc.perform(post("/login/social/vincular/senha").session(sessao).with(csrf()).param("senha", "errada"))
                .andExpect(redirectedUrl("/login/social/vincular"))
                .andExpect(flash().attribute("erro", org.hamcrest.Matchers.containsString("Senha incorreta")));
        assertThat(identidades.findByProvedorAndProvedorUsuarioId(ProvedorSocial.GOOGLE, "g-cliente")).isEmpty();
        assertThat(usuarios.findByCpf(CPF_SEED).orElseThrow().getTentativasLoginFalhas()).isEqualTo(1);

        mvc.perform(post("/login/social/vincular/senha").session(sessao).with(csrf()).param("senha", "Farma@123"))
                .andExpect(redirectedUrl("/"));
        assertThat(logado(sessao)).isTrue();
        IdentidadeSocial vinculo = identidades.findByProvedorAndProvedorUsuarioId(ProvedorSocial.GOOGLE, "g-cliente")
                .orElseThrow();
        assertThat(vinculo.getUsuario().getCpf()).isEqualTo(CPF_SEED);
    }

    @Test
    @DisplayName("CPF existente também vincula com código mandado ao e-mail da conta (não ao do provedor)")
    void cpfExistenteComCodigo() throws Exception {
        MockHttpSession sessao = new MockHttpSession();
        viaProvedor(sessao, "permitir", "g-codigo", "outro.email@teste.com", true);
        completar(sessao, CPF_SEED, "1990-01-01", null);

        mvc.perform(post("/login/social/vincular/codigo/enviar").session(sessao).with(csrf()))
                .andExpect(flash().attributeExists("sucesso"));
        assertThat(caixa.mensagens().stream().noneMatch(m -> m.destino().equals("outro.email@teste.com"))).isTrue();
        String texto = caixa.mensagens().stream().filter(m -> m.destino().equals("cliente@farma.com")).findFirst()
                .orElseThrow().texto();
        Matcher codigo = SEIS_DIGITOS.matcher(texto);
        assertThat(codigo.find()).isTrue();

        mvc.perform(post("/login/social/vincular/codigo").session(sessao).with(csrf()).param("codigo", codigo.group(1)))
                .andExpect(redirectedUrl("/"));
        assertThat(logado(sessao)).isTrue();
    }

    @Test
    @DisplayName("vínculo com conta de administrador não entra")
    void adminNaoEntra() throws Exception {
        Usuario admin = usuarios.findByEmailIgnoreCase("admin@farma.com").orElseThrow();
        identidades.saveAndFlush(IdentidadeSocial.builder().usuario(admin).provedor(ProvedorSocial.GOOGLE)
                .provedorUsuarioId("g-admin").build());
        MockHttpSession sessao = new MockHttpSession();

        MvcResult retorno = viaProvedor(sessao, "permitir", "g-admin", "admin@farma.com", true);

        assertThat(retorno.getResponse().getRedirectedUrl()).isEqualTo("/login");
        assertThat(retorno.getFlashMap().get("erroSocial")).asString().contains("administrador");
        assertThat(logado(sessao)).isFalse();
    }

    @Test
    @DisplayName("cancelar, provedor fora do ar e state inválido voltam ao login sem entrar")
    void cancelarIndisponivelEState() throws Exception {
        MockHttpSession sessao = new MockHttpSession();
        MvcResult cancelado = viaProvedor(sessao, "cancelar", "g-x", null, false);
        assertThat(cancelado.getFlashMap().get("avisoSocial")).asString().contains("cancelou");

        MvcResult fora = viaProvedor(sessao, "indisponivel", "g-x", null, false);
        assertThat(fora.getFlashMap().get("erroSocial")).asString().contains("não respondeu");

        MvcResult forjado = mvc.perform(get("/login/social/google/retorno").session(new MockHttpSession())
                .param("state", "inventado").param("code", "qualquer")).andReturn();
        assertThat(forjado.getResponse().getRedirectedUrl()).isEqualTo("/login");
        assertThat(forjado.getFlashMap().get("erroSocial")).asString().contains("não é válida");
        assertThat(logado(sessao)).isFalse();
    }

    @Test
    @DisplayName("campos proibidos no primeiro acesso são ignorados (conta nasce CLIENTE)")
    void camposProibidos() throws Exception {
        MockHttpSession sessao = new MockHttpSession();
        viaProvedor(sessao, "permitir", "g-esperto", "esperto@teste.com", false);

        completar(sessao, CPF_NOVO, "1990-01-01", null, "perfil", "ADMIN", "status", "ATIVA", "senhaHash", "x",
                "id", "1");

        Usuario conta = usuarios.findByCpf(CPF_NOVO).orElseThrow();
        assertThat(conta.getPerfil()).isEqualTo(Perfil.CLIENTE);
        assertThat(conta.getStatus()).isEqualTo(StatusUsuario.PENDENTE);
        assertThat(conta.getSenhaHash()).isNull();
    }

    @Test
    @DisplayName("e-mail do provedor já usado por outra conta (CPF diferente) é recusado como conflito")
    void conflitoDeEmail() throws Exception {
        MockHttpSession sessao = new MockHttpSession();
        viaProvedor(sessao, "permitir", "g-conflito", "cliente@farma.com", true);

        MvcResult fim = completar(sessao, CPF_NOVO, "1990-01-01", null);

        assertThat(html(fim)).contains("id=\"email-erro\"", "já está em outra conta");
        assertThat(usuarios.findByCpf(CPF_NOVO)).isEmpty();
    }

    @Test
    @DisplayName("logado em Minha conta, vincula direto; identidade de outra conta é recusada")
    void vincularLogado() throws Exception {
        Usuario cliente = usuarios.findByEmailIgnoreCase("cliente@farma.com").orElseThrow();
        MockHttpSession sessao = new MockHttpSession();
        String consentimento = mvc.perform(get("/login/social/facebook").session(sessao)
                .with(user(UsuarioAutenticado.de(cliente)))).andReturn().getResponse().getRedirectedUrl();
        Matcher m = STATE.matcher(consentimento);
        assertThat(m.find()).isTrue();
        String retorno = mvc.perform(post("/login/social/demo/facebook/consentimento").session(sessao).with(csrf())
                        .param("state", m.group(1)).param("acao", "permitir").param("idExterno", "f-cliente"))
                .andReturn().getResponse().getRedirectedUrl();

        mvc.perform(get(retorno).session(sessao).with(user(UsuarioAutenticado.de(cliente))))
                .andExpect(redirectedUrl("/conta"))
                .andExpect(flash().attribute("sucesso", org.hamcrest.Matchers.containsString("vinculada")));
        assertThat(identidades.findByUsuarioIdOrderByCriadoEmAsc(cliente.getId())).hasSize(1);
    }

    @Test
    @DisplayName("tela de login mostra os provedores simulados; retorno vai ao carrinho quando veio de lá")
    void botoesERetornoAoCarrinho() throws Exception {
        String login = mvc.perform(get("/login")).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        assertThat(login).contains("href=\"/login/social/google\"", "Google (simulado)");
        assertThat(login.split("<h1").length - 1).isEqualTo(1);

        MockHttpSession sessao = new MockHttpSession();
        sessao.setAttribute(CadastroController.SESSAO_RETORNO, "carrinho");
        viaProvedor(sessao, "permitir", "g-carrinho", "carrinho.social@teste.com", true);
        MvcResult fim = completar(sessao, CPF_NOVO, "1990-01-01", null);
        assertThat(fim.getResponse().getRedirectedUrl()).isEqualTo("/carrinho");
    }

    @Test
    @DisplayName("provedor desconhecido na URL responde 404")
    void provedorDesconhecido() throws Exception {
        mvc.perform(get("/login/social/twitter")).andExpect(status().isNotFound());
    }

    @Test
    @org.junit.jupiter.api.DisplayName("a tela de consentimento demo abre com \"e-mail verificado\" desmarcado")
    void consentimentoAbreComEmailVerificadoDesmarcado() throws Exception {
        MockHttpSession sessao = new MockHttpSession();
        String consentimento = mvc.perform(get("/login/social/google").session(sessao))
                .andReturn().getResponse().getRedirectedUrl();

        String html = mvc.perform(get(consentimento).session(sessao)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

        assertThat(html).containsPattern("<input type=\"checkbox\" id=\"emailVerificado\" name=\"emailVerificado\" value=\"true\"\\s+"
                + "aria-describedby=\"emailVerificado-ajuda\">");
        assertThat(html).doesNotContainPattern("id=\"emailVerificado\"[^>]*checked");
    }
}
