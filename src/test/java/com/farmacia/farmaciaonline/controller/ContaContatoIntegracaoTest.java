package com.farmacia.farmaciaonline.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import com.farmacia.farmaciaonline.TestcontainersConfiguration;
import com.farmacia.farmaciaonline.mensagem.MensagemEnviada;
import com.farmacia.farmaciaonline.mensagem.demo.DemoEnvioAdapter;
import com.farmacia.farmaciaonline.model.Usuario;
import com.farmacia.farmaciaonline.repository.UsuarioRepository;

/** Fluxo completo da troca de e-mail e de celular com login, banco real e a caixa de mensagens de demonstração. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class ContaContatoIntegracaoTest {

    private static final String EMAIL = "cliente@farma.com";
    private static final String SENHA = "Farma@123";
    private static final Pattern SEIS_DIGITOS = Pattern.compile("\\b(\\d{6})\\b");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UsuarioRepository usuarios;

    @Autowired
    private DemoEnvioAdapter caixa;

    private MockHttpSession entrar(String identificador, String senha) throws Exception {
        MvcResult resultado = mvc.perform(post("/login").with(csrf())
                .param("identificador", identificador).param("senha", senha)).andReturn();
        return (MockHttpSession) resultado.getRequest().getSession(false);
    }

    private String codigoPara(String destino) {
        MensagemEnviada mensagem = caixa.mensagens().stream().filter(m -> m.destino().equals(destino)).findFirst()
                .orElseThrow();
        Matcher m = SEIS_DIGITOS.matcher(mensagem.texto());
        assertThat(m.find()).isTrue();
        assertThat(mensagem.texto()).doesNotContain(SENHA);
        return m.group(1);
    }

    @Test
    @DisplayName("troca o e-mail: o código vai ao novo endereço, o antigo para de entrar e o novo entra")
    void trocaDeEmailComLogin() throws Exception {
        MockHttpSession sessao = entrar(EMAIL, SENHA);
        assertThat(sessao).isNotNull();
        String novo = "troca.web.f21@teste.com";

        mvc.perform(post("/conta/contato/email/enviar").session(sessao).with(csrf())
                        .param("novoContato", novo).param("senhaAtual", SENHA))
                .andExpect(redirectedUrl("/conta/contato/email"));
        assertThat(usuarios.findByEmailIgnoreCase(EMAIL)).isPresent();

        mvc.perform(post("/conta/contato/email/confirmar").session(sessao).with(csrf())
                        .param("codigo", codigoPara(novo)))
                .andExpect(redirectedUrl("/conta"));

        Usuario trocado = usuarios.findByEmailIgnoreCase(novo).orElseThrow();
        assertThat(trocado.getEmailConfirmadoEm()).isNotNull();
        assertThat(usuarios.findByEmailIgnoreCase(EMAIL)).isEmpty();

        MockHttpSession comNovo = entrar(novo, SENHA);
        assertThat(comNovo).isNotNull();
        mvc.perform(get("/conta").session(comNovo)).andExpect(status().isOk());
        mvc.perform(post("/login").with(csrf()).param("identificador", EMAIL).param("senha", SENHA))
                .andExpect(redirectedUrlPattern("/login*"));
    }

    @Test
    @DisplayName("troca o celular: o SMS vai ao novo número e a conta passa a mostrá-lo")
    void trocaDeCelular() throws Exception {
        MockHttpSession sessao = entrar(EMAIL, SENHA);

        mvc.perform(post("/conta/contato/celular/enviar").session(sessao).with(csrf())
                        .param("novoContato", "(67) 98888-1122").param("senhaAtual", SENHA))
                .andExpect(redirectedUrl("/conta/contato/celular"));
        mvc.perform(post("/conta/contato/celular/confirmar").session(sessao).with(csrf())
                        .param("codigo", codigoPara("67988881122")))
                .andExpect(redirectedUrl("/conta"));

        assertThat(usuarios.findByEmailIgnoreCase(EMAIL).orElseThrow().getCelular()).isEqualTo("67988881122");
    }

    @Test
    @DisplayName("código errado não troca o e-mail e senha errada nem envia código")
    void recusas() throws Exception {
        MockHttpSession sessao = entrar(EMAIL, SENHA);
        int antes = caixa.mensagens().size();

        mvc.perform(post("/conta/contato/email/enviar").session(sessao).with(csrf())
                        .param("novoContato", "recusa.f21@teste.com").param("senhaAtual", "errada123"))
                .andExpect(status().isOk());
        assertThat(caixa.mensagens().size()).isEqualTo(antes);

        mvc.perform(post("/conta/contato/email/enviar").session(sessao).with(csrf())
                .param("novoContato", "recusa.f21@teste.com").param("senhaAtual", SENHA));
        String certo = codigoPara("recusa.f21@teste.com");
        String errado = certo.equals("000000") ? "111111" : "000000";
        mvc.perform(post("/conta/contato/email/confirmar").session(sessao).with(csrf()).param("codigo", errado))
                .andExpect(redirectedUrl("/conta/contato/email"));

        assertThat(usuarios.findByEmailIgnoreCase(EMAIL)).isPresent();
        assertThat(usuarios.findByEmailIgnoreCase("recusa.f21@teste.com")).isEmpty();
    }
}
