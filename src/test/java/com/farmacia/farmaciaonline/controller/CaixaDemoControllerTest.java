package com.farmacia.farmaciaonline.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.farmacia.farmaciaonline.config.SecurityConfig;
import com.farmacia.farmaciaonline.mensagem.demo.DemoEnvioAdapter;
import com.farmacia.farmaciaonline.model.enums.CanalCodigo;
import com.farmacia.farmaciaonline.repository.CategoriaRepository;

/**
 * Caixa de mensagens de teste com o perfil demo (ligado no application.properties). Sem banco e sem Docker.
 * A caixa fica na memória entre testes: cada teste usa textos próprios e confere só eles.
 */
@WebMvcTest(CaixaDemoController.class)
@Import({SecurityConfig.class, DemoEnvioAdapter.class})
class CaixaDemoControllerTest {

    @MockBean
    private CategoriaRepository categoriaRepository;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private DemoEnvioAdapter caixa;

    /** Envia como se fosse durante uma requisição da sessão dada (é assim que o adaptador descobre a caixa). */
    private void enviarNaSessao(MockHttpSession sessao, CanalCodigo canal, String destino, String assunto, String texto) {
        MockHttpServletRequest requisicao = new MockHttpServletRequest();
        requisicao.setSession(sessao);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(requisicao));
        try {
            caixa.enviar(canal, destino, assunto, texto);
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }

    private String html(MockHttpServletRequestBuilder requisicao) throws Exception {
        return mvc.perform(requisicao).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("a sessão vê as próprias mensagens, da mais recente para a mais antiga, com um único h1")
    void sessaoVeAsPropriasMensagens() throws Exception {
        MockHttpSession sessao = new MockHttpSession();
        enviarNaSessao(sessao, CanalCodigo.EMAIL, "maria@exemplo.com", "Assunto antigo", "Seu código do Farma++ é 111111.");
        enviarNaSessao(sessao, CanalCodigo.SMS, "67999998888", "Assunto novo", "Seu código do Farma++ é 222222.");

        String html = html(get("/caixa-demo").session(sessao));

        assertThat(html).contains("<h1>Caixa de mensagens de teste</h1>").contains("nenhum e-mail ou SMS sai de verdade")
                .contains("Você vê só as mensagens geradas nesta sessão");
        assertThat(StringUtils.countOccurrencesOf(html, "<h1")).isEqualTo(1);
        assertThat(html).contains("SMS para 67999998888").contains("E-mail para maria@exemplo.com").contains("222222");
        assertThat(html.indexOf("Assunto novo")).isLessThan(html.indexOf("Assunto antigo"));
    }

    @Test
    @DisplayName("uma sessão não vê o código gerado em outra (era assim que se tomava a conta de alguém)")
    void sessaoNaoVeCodigoDeOutra() throws Exception {
        MockHttpSession vitima = new MockHttpSession();
        MockHttpSession atacante = new MockHttpSession();
        enviarNaSessao(vitima, CanalCodigo.EMAIL, "vitima@exemplo.com", "Recuperação", "Seu código do Farma++ é 314159.");
        enviarNaSessao(atacante, CanalCodigo.EMAIL, "atacante@exemplo.com", "Cadastro", "Seu código do Farma++ é 271828.");

        String html = html(get("/caixa-demo").session(atacante));

        assertThat(html).contains("271828").doesNotContain("314159").doesNotContain("vitima@exemplo.com");
    }

    @Test
    @DisplayName("requisição sem sessão (ou mensagem gerada fora de requisição) não mostra nada a visitante")
    void semSessaoNaoVeNada() throws Exception {
        caixa.enviar(CanalCodigo.EMAIL, "job@exemplo.com", "Pedido expirado", "Seu pedido foi cancelado: 999001.");

        String html = html(get("/caixa-demo"));

        assertThat(html).contains("Nenhuma mensagem ainda").doesNotContain("999001");
    }

    @Test
    @DisplayName("o administrador vê as mensagens de todas as sessões e as geradas fora de requisição")
    void administradorVeTodas() throws Exception {
        enviarNaSessao(new MockHttpSession(), CanalCodigo.EMAIL, "a@exemplo.com", "A", "Seu código do Farma++ é 404001.");
        caixa.enviar(CanalCodigo.EMAIL, "job@exemplo.com", "Pedido expirado", "Seu pedido foi cancelado: 404002.");

        String html = html(get("/caixa-demo").with(user("admin").roles("ADMIN")));

        assertThat(html).contains("404001").contains("404002");
    }
}
