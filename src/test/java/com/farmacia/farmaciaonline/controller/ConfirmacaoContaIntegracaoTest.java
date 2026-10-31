package com.farmacia.farmaciaonline.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
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
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import com.farmacia.farmaciaonline.TestcontainersConfiguration;
import com.farmacia.farmaciaonline.mensagem.demo.DemoEnvioAdapter;
import com.farmacia.farmaciaonline.model.Usuario;
import com.farmacia.farmaciaonline.model.enums.FinalidadeCodigo;
import com.farmacia.farmaciaonline.model.enums.StatusUsuario;
import com.farmacia.farmaciaonline.repository.CodigoVerificacaoRepository;
import com.farmacia.farmaciaonline.repository.UsuarioRepository;

/**
 * Confirmação da conta de ponta a ponta (cadastro, código, ativação, login e retomada), com o seed do V2 e o perfil
 * demo (ligado no application.properties). Cada teste é desfeito no fim (@Transactional).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class ConfirmacaoContaIntegracaoTest {

    private static final String EMAIL = "carla.nova@exemplo.com";
    private static final String CELULAR = "67999997777";
    private static final Pattern SEIS_DIGITOS = Pattern.compile("\\b(\\d{6})\\b");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UsuarioRepository usuarios;

    @Autowired
    private CodigoVerificacaoRepository codigos;

    @Autowired
    private DemoEnvioAdapter caixa;

    private MockHttpSession cadastrar() throws Exception {
        return (MockHttpSession) mvc.perform(post("/cadastro").with(csrf())
                        .param("nome", "Carla Nova")
                        .param("cpf", "390.533.447-05")
                        .param("dataNascimento", "1992-08-10")
                        .param("genero", "FEMININO")
                        .param("celular", "(67) 99999-7777")
                        .param("email", EMAIL)
                        .param("senha", "Senha123")
                        .param("confirmarSenha", "Senha123")
                        .param("aceiteTermos", "true"))
                .andExpect(redirectedUrl("/confirmar-conta"))
                .andReturn().getRequest().getSession();
    }

    private void pedirCodigo(MockHttpSession sessao, String canal) throws Exception {
        mvc.perform(post("/confirmar-conta/enviar").with(csrf()).session(sessao).param("canal", canal))
                .andExpect(redirectedUrl("/confirmar-conta"))
                .andExpect(flash().attributeExists("sucesso"));
    }

    private String codigoEnviadoPara(String destino) {
        String texto = caixa.mensagens().stream().filter(m -> m.destino().equals(destino)).findFirst().orElseThrow().texto();
        Matcher encontrado = SEIS_DIGITOS.matcher(texto);
        assertThat(encontrado.find()).isTrue();
        return encontrado.group(1);
    }

    private String html(MockHttpServletRequestBuilder requisicao) throws Exception {
        return mvc.perform(requisicao).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private Long idDaContaPendenteDoSeed() {
        return usuarios.findByEmailIgnoreCase("pendente@farma.com").orElseThrow().getId();
    }

    @Test
    @DisplayName("confirmar pelo e-mail ativa a conta, confirma só o e-mail e a pessoa consegue entrar")
    void confirmarPeloEmail() throws Exception {
        MockHttpSession sessao = cadastrar();
        String pagina = html(get("/confirmar-conta").session(sessao));
        assertThat(pagina).contains("<h1>Confirme sua conta</h1>").contains("c***@exemplo.com").contains("(67) 9****-7777");

        pedirCodigo(sessao, "EMAIL");
        String codigo = codigoEnviadoPara(EMAIL);
        assertThat(html(get("/confirmar-conta").session(sessao))).doesNotContain(codigo);

        mvc.perform(post("/confirmar-conta").with(csrf()).session(sessao).param("codigo", codigo))
                .andExpect(redirectedUrl("/login?confirmada"))
                .andExpect(request().sessionAttributeDoesNotExist(CadastroController.SESSAO_CONTA));

        Usuario conta = usuarios.findByEmailIgnoreCase(EMAIL).orElseThrow();
        assertThat(conta.getStatus()).isEqualTo(StatusUsuario.ATIVA);
        assertThat(conta.getEmailConfirmadoEm()).isNotNull();
        assertThat(conta.getCelularConfirmadoEm()).isNull();
        assertThat(html(get("/login").param("confirmada", ""))).contains("Conta confirmada");
        mvc.perform(formLogin("/login").user("identificador", EMAIL).password("senha", "Senha123"))
                .andExpect(authenticated());
    }

    @Test
    @DisplayName("confirmar por SMS confirma só o celular")
    void confirmarPorSms() throws Exception {
        MockHttpSession sessao = cadastrar();
        pedirCodigo(sessao, "SMS");

        mvc.perform(post("/confirmar-conta").with(csrf()).session(sessao).param("codigo", codigoEnviadoPara(CELULAR)))
                .andExpect(redirectedUrl("/login?confirmada"));

        Usuario conta = usuarios.findByEmailIgnoreCase(EMAIL).orElseThrow();
        assertThat(conta.getStatus()).isEqualTo(StatusUsuario.ATIVA);
        assertThat(conta.getCelularConfirmadoEm()).isNotNull();
        assertThat(conta.getEmailConfirmadoEm()).isNull();
    }

    @Test
    @DisplayName("código errado avisa quantas tentativas faltam, grava a tentativa e a conta segue pendente")
    void codigoErrado() throws Exception {
        MockHttpSession sessao = cadastrar();
        pedirCodigo(sessao, "EMAIL");
        String errado = codigoEnviadoPara(EMAIL).equals("000000") ? "111111" : "000000";

        mvc.perform(post("/confirmar-conta").with(csrf()).session(sessao).param("codigo", errado))
                .andExpect(redirectedUrl("/confirmar-conta"))
                .andExpect(flash().attribute("erro", containsString("4 tentativas")));

        Usuario conta = usuarios.findByEmailIgnoreCase(EMAIL).orElseThrow();
        assertThat(conta.getStatus()).isEqualTo(StatusUsuario.PENDENTE);
        assertThat(codigos.findFirstByUsuarioIdAndFinalidadeOrderByCriadoEmDesc(conta.getId(), FinalidadeCodigo.CADASTRO)
                .orElseThrow().getTentativas()).isEqualTo(1);
    }

    @Test
    @DisplayName("pedir outro código antes de 60 segundos pede para aguardar")
    void reenvioCedoDemais() throws Exception {
        MockHttpSession sessao = cadastrar();
        pedirCodigo(sessao, "EMAIL");

        mvc.perform(post("/confirmar-conta/enviar").with(csrf()).session(sessao).param("canal", "SMS"))
                .andExpect(redirectedUrl("/confirmar-conta"))
                .andExpect(flash().attribute("erro", containsString("Aguarde")));
    }

    @Test
    @DisplayName("sem a conta na sessão não dá para pedir código nem confirmar")
    void semSessaoNaoConfirma() throws Exception {
        mvc.perform(post("/confirmar-conta/enviar").with(csrf()).param("canal", "EMAIL"))
                .andExpect(redirectedUrl("/cadastro"));
        mvc.perform(post("/confirmar-conta").with(csrf()).param("codigo", "123456"))
                .andExpect(redirectedUrl("/cadastro"));
    }

    @Test
    @DisplayName("login de conta pendente oferece retomar a confirmação")
    void loginPendenteOfereceRetomada() throws Exception {
        assertThat(html(get("/login").param("erro", "pendente")))
                .contains("ainda não foi confirmada").contains("href=\"/confirmar-conta/retomar\"");
        assertThat(html(get("/login"))).doesNotContain("href=\"/confirmar-conta/retomar\"");
    }

    @Test
    @DisplayName("retomada com e-mail ou CPF e a senha certa abre a confirmação da conta pendente")
    void retomadaComSenhaCerta() throws Exception {
        Long id = idDaContaPendenteDoSeed();
        for (String identificador : new String[] {"pendente@farma.com", "PENDENTE@Farma.com", "111.444.777-35"}) {
            mvc.perform(post("/confirmar-conta/retomar").with(csrf())
                            .param("identificador", identificador).param("senha", "Farma@123"))
                    .andExpect(redirectedUrl("/confirmar-conta"))
                    .andExpect(request().sessionAttribute(CadastroController.SESSAO_CONTA, id));
        }
    }

    @Test
    @DisplayName("retomada com senha errada ou conta inexistente dá a mesma mensagem e não abre nada")
    void retomadaRecusada() throws Exception {
        String senhaErrada = mvc.perform(post("/confirmar-conta/retomar").with(csrf())
                        .param("identificador", "pendente@farma.com").param("senha", "Errada123"))
                .andExpect(status().isOk())
                .andExpect(request().sessionAttributeDoesNotExist(CadastroController.SESSAO_CONTA))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String inexistente = html(post("/confirmar-conta/retomar").with(csrf())
                .param("identificador", "ninguem@exemplo.com").param("senha", "Farma@123"));

        assertThat(senhaErrada).contains("Não encontramos uma conta pendente com esses dados.");
        assertThat(inexistente).contains("Não encontramos uma conta pendente com esses dados.");
    }

    @Test
    @DisplayName("retomada de conta que já está ativa manda para o login")
    void retomadaDeContaAtiva() throws Exception {
        mvc.perform(post("/confirmar-conta/retomar").with(csrf())
                        .param("identificador", "cliente@farma.com").param("senha", "Farma@123"))
                .andExpect(redirectedUrl("/login?confirmada"))
                .andExpect(request().sessionAttributeDoesNotExist(CadastroController.SESSAO_CONTA));
    }

    @Test
    @DisplayName("no perfil demo a página de confirmação aponta a caixa de mensagens, que mostra o código")
    void caixaDemoMostraOCodigo() throws Exception {
        MockHttpSession sessao = cadastrar();
        assertThat(html(get("/confirmar-conta").session(sessao))).contains("href=\"/caixa-demo\"");
        pedirCodigo(sessao, "EMAIL");

        assertThat(html(get("/caixa-demo"))).contains(EMAIL).contains(codigoEnviadoPara(EMAIL));
    }
}
