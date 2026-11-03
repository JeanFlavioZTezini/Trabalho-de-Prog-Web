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
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.util.StringUtils;

import com.farmacia.farmaciaonline.config.SecurityConfig;
import com.farmacia.farmaciaonline.mensagem.demo.DemoEnvioAdapter;
import com.farmacia.farmaciaonline.model.enums.CanalCodigo;
import com.farmacia.farmaciaonline.model.enums.Perfil;
import com.farmacia.farmaciaonline.repository.CategoriaRepository;
import com.farmacia.farmaciaonline.security.UsuarioAutenticado;

/**
 * Caixa de mensagens de teste com o perfil demo (ligado no application.properties). Sem banco e sem Docker.
 * A caixa fica na memória entre testes: cada teste usa contas e textos próprios e confere só eles.
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

    private static UsuarioAutenticado cliente(long id) {
        return new UsuarioAutenticado(id, "Cliente " + id, Perfil.CLIENTE, "cliente" + id + "@exemplo.com", "hash");
    }

    private String html(MockHttpServletRequestBuilder requisicao) throws Exception {
        return mvc.perform(requisicao).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("a conta logada vê as próprias mensagens, da mais recente para a mais antiga, com um único h1")
    void contaVeAsPropriasMensagens() throws Exception {
        caixa.enviar(CanalCodigo.EMAIL, "maria@exemplo.com", "Assunto antigo", "Seu código é 111111.", 9001L);
        caixa.enviar(CanalCodigo.SMS, "67999998888", "Assunto novo", "Seu código é 222222.", 9001L);

        String html = html(get("/caixa-demo").with(user(cliente(9001))));

        assertThat(html).contains("<h1>Caixa de mensagens de teste</h1>")
                .contains("nenhum e-mail ou SMS sai de verdade")
                .contains("Você vê as mensagens enviadas para os contatos da sua conta");
        assertThat(StringUtils.countOccurrencesOf(html, "<h1")).isEqualTo(1);
        assertThat(html).contains("SMS para 67999998888").contains("E-mail para maria@exemplo.com").contains("222222");
        assertThat(html.indexOf("Assunto novo")).isLessThan(html.indexOf("Assunto antigo"));
    }

    @Test
    @DisplayName("a conta logada não vê mensagem de outra conta (nem para o mesmo celular) nem mensagem sem conta")
    void contaNaoVeMensagemDeOutra() throws Exception {
        caixa.enviar(CanalCodigo.SMS, "67911112222", "Recuperação", "Seu código do Farma++ é 314159.", 9002L);
        caixa.enviar(CanalCodigo.EMAIL, "job@exemplo.com", "Sem conta", "Mensagem sem dono: 314160.");
        caixa.enviar(CanalCodigo.SMS, "67911112222", "Cadastro", "Seu código do Farma++ é 271828.", 9003L);

        String html = html(get("/caixa-demo").with(user(cliente(9003))));

        assertThat(html).contains("271828").doesNotContain("314159").doesNotContain("314160");
    }

    @Test
    @DisplayName("visitante com a conta em confirmação na sessão vê as mensagens dessa conta")
    void contaEmConfirmacaoVeAsMensagens() throws Exception {
        caixa.enviar(CanalCodigo.EMAIL, "novo@exemplo.com", "Cadastro", "Seu código do Farma++ é 505001.", 9004L);
        caixa.enviar(CanalCodigo.EMAIL, "outro@exemplo.com", "Cadastro", "Seu código do Farma++ é 505002.", 9005L);
        MockHttpSession sessao = new MockHttpSession();
        sessao.setAttribute(CadastroController.SESSAO_CONTA, 9004L);

        String html = html(get("/caixa-demo").session(sessao));

        assertThat(html).contains("505001").doesNotContain("505002");
    }

    @Test
    @DisplayName("visitante sem conta em confirmação vê a caixa vazia")
    void visitanteSemContaNaoVeNada() throws Exception {
        caixa.enviar(CanalCodigo.EMAIL, "alguem@exemplo.com", "Recuperação", "Seu código do Farma++ é 999001.", 9006L);

        assertThat(html(get("/caixa-demo"))).contains("Nenhuma mensagem ainda").doesNotContain("999001");
        assertThat(html(get("/caixa-demo").session(new MockHttpSession()))).contains("Nenhuma mensagem ainda");
    }

    @Test
    @DisplayName("o administrador vê as mensagens de todas as contas e as sem conta")
    void administradorVeTodas() throws Exception {
        caixa.enviar(CanalCodigo.EMAIL, "a@exemplo.com", "A", "Seu código do Farma++ é 404001.", 9007L);
        caixa.enviar(CanalCodigo.EMAIL, "job@exemplo.com", "Pedido expirado", "Seu pedido foi cancelado: 404002.");

        String html = html(get("/caixa-demo").with(user("admin").roles("ADMIN")));

        assertThat(html).contains("404001").contains("404002");
    }
}
