package com.farmacia.farmaciaonline.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
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
import com.farmacia.farmaciaonline.service.LimiteDeSolicitacoes;

/**
 * Recuperação de senha de ponta a ponta (pedido, código, nova senha, login e sessões antigas), com o seed do V2.
 * Cada teste é desfeito no fim (@Transactional).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class RecuperacaoSenhaIntegracaoTest {

    private static final Pattern SEIS_DIGITOS = Pattern.compile("\\b(\\d{6})\\b");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private DemoEnvioAdapter caixa;

    @Autowired
    private LimiteDeSolicitacoes limite;

    @BeforeEach
    void esquecerPedidosAnteriores() {
        limite.limpar();
    }

    private MockHttpSession pedirCodigo(String identificador) throws Exception {
        return (MockHttpSession) mvc.perform(post("/recuperar-senha").with(csrf())
                        .param("identificador", identificador).param("canal", "EMAIL"))
                .andExpect(redirectedUrl("/recuperar-senha/codigo"))
                .andReturn().getRequest().getSession();
    }

    private String codigoEnviadoPara(String destino) {
        String texto = caixa.mensagens().stream().filter(m -> m.destino().equals(destino)).findFirst().orElseThrow().texto();
        Matcher encontrado = SEIS_DIGITOS.matcher(texto);
        assertThat(encontrado.find()).isTrue();
        return encontrado.group(1);
    }

    private void validarCodigo(MockHttpSession sessao, String codigo) throws Exception {
        mvc.perform(post("/recuperar-senha/codigo").with(csrf()).session(sessao).param("codigo", codigo))
                .andExpect(redirectedUrl("/recuperar-senha/nova-senha"));
    }

    private void trocarSenha(String email, String novaSenha) throws Exception {
        MockHttpSession sessao = pedirCodigo(email);
        validarCodigo(sessao, codigoEnviadoPara(email));
        mvc.perform(post("/recuperar-senha/nova-senha").with(csrf()).session(sessao)
                        .param("senha", novaSenha).param("confirmarSenha", novaSenha))
                .andExpect(redirectedUrl("/login?senhaAlterada"));
    }

    private String html(MockHttpServletRequestBuilder requisicao) throws Exception {
        return mvc.perform(requisicao).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("recuperação completa: a conta entra com a senha nova e não com a antiga")
    void recuperacaoCompleta() throws Exception {
        MockHttpSession sessao = pedirCodigo("cliente@farma.com");
        assertThat(html(get("/recuperar-senha/codigo").session(sessao))).contains("<h1>Digite o código</h1>");
        validarCodigo(sessao, codigoEnviadoPara("cliente@farma.com"));
        assertThat(html(get("/recuperar-senha/nova-senha").session(sessao))).contains("<h1>Crie a nova senha</h1>");

        mvc.perform(post("/recuperar-senha/nova-senha").with(csrf()).session(sessao)
                        .param("senha", "NovaSenha1").param("confirmarSenha", "NovaSenha1"))
                .andExpect(redirectedUrl("/login?senhaAlterada"));

        assertThat(html(get("/login").param("senhaAlterada", ""))).contains("Senha alterada.");
        mvc.perform(formLogin("/login").user("identificador", "cliente@farma.com").password("senha", "NovaSenha1"))
                .andExpect(authenticated());
        mvc.perform(formLogin("/login").user("identificador", "cliente@farma.com").password("senha", "Farma@123"))
                .andExpect(unauthenticated());
    }

    @Test
    @DisplayName("o código já usado não serve para uma segunda troca")
    void codigoUsadoNaoServeDeNovo() throws Exception {
        MockHttpSession primeira = pedirCodigo("cliente@farma.com");
        String codigo = codigoEnviadoPara("cliente@farma.com");
        validarCodigo(primeira, codigo);
        mvc.perform(post("/recuperar-senha/nova-senha").with(csrf()).session(primeira)
                        .param("senha", "NovaSenha1").param("confirmarSenha", "NovaSenha1"))
                .andExpect(redirectedUrl("/login?senhaAlterada"));

        limite.limpar(); // só para poder pedir de novo logo em seguida
        MockHttpSession segunda = pedirCodigo("cliente@farma.com");
        mvc.perform(post("/recuperar-senha/codigo").with(csrf()).session(segunda).param("codigo", codigo))
                .andExpect(redirectedUrl("/recuperar-senha/codigo"))
                .andExpect(flash().attribute("erro", containsString("Código incorreto ou expirado")));
        mvc.perform(get("/recuperar-senha/nova-senha").session(segunda)).andExpect(redirectedUrl("/recuperar-senha"));
    }

    @Test
    @DisplayName("a resposta ao pedido é a mesma para conta existente, inexistente e pendente")
    void respostaGenerica() throws Exception {
        String[] mensagens = new String[3];
        String[] identificadores = {"cliente@farma.com", "ninguem@exemplo.com", "pendente@farma.com"};
        for (int i = 0; i < identificadores.length; i++) {
            limite.limpar();
            mensagens[i] = (String) mvc.perform(post("/recuperar-senha").with(csrf())
                            .param("identificador", identificadores[i]).param("canal", "EMAIL"))
                    .andExpect(redirectedUrl("/recuperar-senha/codigo"))
                    .andReturn().getFlashMap().get("sucesso");
        }

        assertThat(mensagens[0]).contains("Se houver uma conta com esses dados");
        assertThat(mensagens[1]).isEqualTo(mensagens[0]);
        assertThat(mensagens[2]).isEqualTo(mensagens[0]);
    }

    @Test
    @DisplayName("pedir de novo logo em seguida pede para aguardar, mesmo para conta inexistente")
    void pedidoRepetidoAguardaAteParaContaInexistente() throws Exception {
        pedirCodigo("ninguem@exemplo.com");

        mvc.perform(post("/recuperar-senha").with(csrf()).param("identificador", "ninguem@exemplo.com").param("canal", "EMAIL"))
                .andExpect(redirectedUrl("/recuperar-senha"))
                .andExpect(flash().attribute("erro", containsString("Aguarde")));
    }

    @Test
    @DisplayName("código errado dá a mesma mensagem com conta existente ou inexistente")
    void codigoErradoMensagemIgual() throws Exception {
        MockHttpSession existente = pedirCodigo("cliente@farma.com");
        String errado = codigoEnviadoPara("cliente@farma.com").equals("000000") ? "111111" : "000000";
        String comConta = (String) mvc.perform(post("/recuperar-senha/codigo").with(csrf()).session(existente)
                .param("codigo", errado)).andReturn().getFlashMap().get("erro");

        MockHttpSession inexistente = pedirCodigo("ninguem@exemplo.com");
        String semConta = (String) mvc.perform(post("/recuperar-senha/codigo").with(csrf()).session(inexistente)
                .param("codigo", "123456")).andReturn().getFlashMap().get("erro");

        assertThat(comConta).contains("Código incorreto ou expirado").isEqualTo(semConta);
    }

    @Test
    @DisplayName("uma sessão aberta antes da troca não acessa mais rota protegida")
    void sessaoAnteriorPerdeOAcesso() throws Exception {
        MockHttpSession outroNavegador = (MockHttpSession) mvc.perform(formLogin("/login")
                        .user("identificador", "cliente@farma.com").password("senha", "Farma@123"))
                .andExpect(authenticated())
                .andReturn().getRequest().getSession();
        mvc.perform(get("/enderecos").session(outroNavegador)).andExpect(status().isOk());

        trocarSenha("cliente@farma.com", "NovaSenha1");

        mvc.perform(get("/enderecos").session(outroNavegador)).andExpect(redirectedUrl("/login?expirada"));
        assertThat(html(get("/login").param("expirada", ""))).contains("Sua sessão foi encerrada");
    }

    @Test
    @DisplayName("sem pedir e validar o código não se chega à nova senha")
    void semCodigoNaoChegaANovaSenha() throws Exception {
        mvc.perform(get("/recuperar-senha/codigo")).andExpect(redirectedUrl("/recuperar-senha"));
        mvc.perform(get("/recuperar-senha/nova-senha")).andExpect(redirectedUrl("/recuperar-senha"));
        mvc.perform(post("/recuperar-senha/nova-senha").with(csrf()).param("senha", "NovaSenha1")
                .param("confirmarSenha", "NovaSenha1")).andExpect(redirectedUrl("/recuperar-senha"));

        MockHttpSession semValidar = pedirCodigo("cliente@farma.com");
        mvc.perform(get("/recuperar-senha/nova-senha").session(semValidar)).andExpect(redirectedUrl("/recuperar-senha"));
    }

    @Test
    @DisplayName("nova senha fraca ou diferente da confirmação volta ao formulário e não troca nada")
    void senhaFracaNaoTroca() throws Exception {
        MockHttpSession sessao = pedirCodigo("cliente@farma.com");
        validarCodigo(sessao, codigoEnviadoPara("cliente@farma.com"));

        assertThat(html(post("/recuperar-senha/nova-senha").with(csrf()).session(sessao)
                .param("senha", "fraca").param("confirmarSenha", "fraca")))
                .contains("A senha deve ter 8 caracteres ou mais").contains("id=\"senha-erro\"");
        assertThat(html(post("/recuperar-senha/nova-senha").with(csrf()).session(sessao)
                .param("senha", "NovaSenha1").param("confirmarSenha", "NovaSenha2")))
                .contains("As senhas não conferem.");

        mvc.perform(formLogin("/login").user("identificador", "cliente@farma.com").password("senha", "Farma@123"))
                .andExpect(authenticated());
    }

    @Test
    @DisplayName("a nova senha não pode ser igual à atual")
    void novaSenhaIgualAAtualEhRecusada() throws Exception {
        MockHttpSession sessao = pedirCodigo("cliente@farma.com");
        validarCodigo(sessao, codigoEnviadoPara("cliente@farma.com"));

        assertThat(html(post("/recuperar-senha/nova-senha").with(csrf()).session(sessao)
                .param("senha", "Farma@123").param("confirmarSenha", "Farma@123")))
                .contains("id=\"senha-erro\"").contains("A nova senha precisa ser diferente da atual.");

        mvc.perform(post("/recuperar-senha/nova-senha").with(csrf()).session(sessao)
                        .param("senha", "NovaSenha1").param("confirmarSenha", "NovaSenha1"))
                .andExpect(redirectedUrl("/login?senhaAlterada"));
    }

    @Test
    @DisplayName("o administrador recupera a senha e entra no painel")
    void administradorRecupera() throws Exception {
        trocarSenha("admin@farma.com", "NovaSenha1");

        mvc.perform(formLogin("/login").user("identificador", "admin@farma.com").password("senha", "NovaSenha1"))
                .andExpect(authenticated())
                .andExpect(redirectedUrl("/admin"));
    }

    @Test
    @DisplayName("a tela de login tem o atalho para recuperar a senha")
    void loginTemOAtalho() throws Exception {
        assertThat(html(get("/login"))).contains("href=\"/recuperar-senha\"").contains("Esqueci minha senha");
    }

    @Test
    @DisplayName("a tela de pedido não diz que o código já foi enviado")
    void telaDePedidoNaoDizQueJaEnviou() throws Exception {
        assertThat(html(get("/recuperar-senha"))).contains("Ele será enviado").doesNotContain("Mandamos um código");
    }
}
