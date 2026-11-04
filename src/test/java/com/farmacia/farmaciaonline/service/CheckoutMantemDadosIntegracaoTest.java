package com.farmacia.farmaciaonline.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.util.StringUtils;

import com.farmacia.farmaciaonline.model.Apresentacao;
import com.farmacia.farmaciaonline.model.Usuario;
import com.farmacia.farmaciaonline.security.UsuarioAutenticado;

/**
 * Erro no cartão não apaga o que foi digitado: validade, nome, "salvar cartão", forma de pagamento e parcelas voltam;
 * número e código de segurança nunca voltam (nem na sessão, nem no HTML).
 */
class CheckoutMantemDadosIntegracaoTest extends CheckoutTesteBase {

    private static final String QUERY = "tipoEntrega=RETIRADA&metodo=CARTAO_CREDITO&lojaId=1&parcelas=3";

    @Autowired
    private MockMvc mvc;

    private static RequestPostProcessor como(Usuario usuario) {
        return user(UsuarioAutenticado.de(usuario));
    }

    private static String campoOculto(String html, String nome) {
        Matcher achado = Pattern.compile("name=\"" + nome + "\" value=\"([^\"]+)\"").matcher(html);
        assertThat(achado.find()).as("campo " + nome).isTrue();
        return achado.group(1);
    }

    /** Segue o redirecionamento levando o flash, como o navegador faz. */
    private String seguir(Usuario cliente, MvcResult resultado) throws Exception {
        String html = mvc.perform(get(resultado.getResponse().getRedirectedUrl()).with(como(cliente))
                        .flashAttrs(resultado.getFlashMap()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(StringUtils.countOccurrencesOf(html, "<h1")).isEqualTo(1);
        return html;
    }

    private MvcResult confirmar(Usuario cliente, String numero, String validade, String cvv) throws Exception {
        String html = mvc.perform(get("/checkout/resumo?" + QUERY).with(como(cliente)))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        MockHttpServletRequestBuilder requisicao = post("/checkout/confirmar").with(como(cliente)).with(csrf())
                .param("conferencia", campoOculto(html, "conferencia"))
                .param("chave", campoOculto(html, "chave"))
                .param("numeroCartao", numero).param("validadeCartao", validade).param("cvv", cvv)
                .param("nomeImpresso", "MARIA TESTE").param("salvarCartao", "true");
        for (String par : QUERY.split("&")) {
            requisicao.param(par.split("=")[0], par.split("=")[1]);
        }
        return mvc.perform(requisicao).andReturn();
    }

    private static String campo(String html, String id) {
        Matcher achado = Pattern.compile("<input[^>]*id=\"" + id + "\"[^>]*>").matcher(html);
        assertThat(achado.find()).as("campo " + id).isTrue();
        return achado.group();
    }

    @Test
    @DisplayName("resumo: formato errado marca o campo e devolve validade, nome e 'salvar cartão'; número e código não")
    void formatoErradoNoResumo() throws Exception {
        Usuario cliente = novoCliente();
        Apresentacao a = novaApresentacao("10.00", 5);
        noCarrinho(cliente, a, 1);

        MvcResult resultado = confirmar(cliente, "4111 1111", "12/40", "987");
        assertThat(resultado.getResponse().getRedirectedUrl()).startsWith("/checkout/resumo")
                .doesNotContain("4111").doesNotContain("987").doesNotContain("MARIA");
        assertThat(resultado.getFlashMap().toString()).doesNotContain("4111").doesNotContain("987");
        String html = seguir(cliente, resultado);

        assertThat(campo(html, "numeroCartao")).contains("aria-invalid=\"true\"")
                .contains("numeroCartao-erro").doesNotContainPattern("value=\"[^\"]+\"");
        assertThat(html).contains("id=\"numeroCartao-erro\"").doesNotContain("id=\"validadeCartao-erro\"")
                .doesNotContain("id=\"cvv-erro\"").doesNotContain("id=\"nomeImpresso-erro\"");
        assertThat(campo(html, "validadeCartao")).contains("value=\"12/40\"");
        assertThat(campo(html, "nomeImpresso")).contains("value=\"MARIA TESTE\"");
        assertThat(campo(html, "cvv")).doesNotContainPattern("value=\"[^\"]+\"");
        assertThat(campo(html, "salvarCartao")).contains("checked");
        assertThat(html).contains("Confira os dados do cartão").doesNotContain("value=\"987\"");
        assertThat(estoqueDe(a)).isEqualTo(5);
    }

    @Test
    @DisplayName("pedido: depois da recusa, forma de pagamento, parcelas, validade e nome continuam escolhidos")
    void recusaMantemEscolhasNaTelaDoPedido() throws Exception {
        Usuario cliente = novoCliente();
        noCarrinho(cliente, novaApresentacao("100.00", 5), 1);

        MvcResult resultado = confirmar(cliente, CARTAO_RECUSADO, "12/40", "987");
        assertThat(resultado.getResponse().getRedirectedUrl()).startsWith("/checkout/pedidos/");
        String html = seguir(cliente, resultado);

        assertThat(html).contains("Pagamento recusado");
        assertThat(campo(html, "validadeCartao")).contains("value=\"12/40\"");
        assertThat(campo(html, "nomeImpresso")).contains("value=\"MARIA TESTE\"");
        assertThat(campo(html, "numeroCartao")).doesNotContainPattern("value=\"[^\"]+\"");
        assertThat(html).doesNotContain(CARTAO_RECUSADO).doesNotContain("value=\"987\"");
        Matcher credito = Pattern.compile("<input[^>]*name=\"metodo\"[^>]*value=\"CARTAO_CREDITO\"[^>]*>").matcher(html);
        assertThat(credito.find()).isTrue();
        assertThat(credito.group()).contains("checked");
        assertThat(html).containsPattern("<option value=\"3\" selected=\"selected\">3x</option>");
    }

    @Test
    @DisplayName("pedido: nova tentativa com formato errado marca o campo e mantém a forma de pagamento escolhida")
    void novaTentativaComFormatoErrado() throws Exception {
        Usuario cliente = novoCliente();
        noCarrinho(cliente, novaApresentacao("10.00", 5), 1);
        String pedidoUrl = confirmar(cliente, CARTAO_RECUSADO, "12/40", "987").getResponse().getRedirectedUrl();
        String tela = mvc.perform(get(pedidoUrl).with(como(cliente)))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        MvcResult resultado = mvc.perform(post(pedidoUrl + "/pagamento").with(como(cliente)).with(csrf())
                        .param("chave", campoOculto(tela, "chave")).param("metodo", "CARTAO_DEBITO")
                        .param("numeroCartao", CARTAO_RECUSADO).param("validadeCartao", "13/40")
                        .param("cvv", "987").param("nomeImpresso", "MARIA TESTE"))
                .andReturn();
        String html = seguir(cliente, resultado);

        assertThat(campo(html, "validadeCartao")).contains("aria-invalid=\"true\"").contains("value=\"13/40\"");
        assertThat(html).contains("id=\"validadeCartao-erro\"").doesNotContain(CARTAO_RECUSADO);
        Matcher debito = Pattern.compile("<input[^>]*name=\"metodo\"[^>]*value=\"CARTAO_DEBITO\"[^>]*>").matcher(html);
        assertThat(debito.find()).isTrue();
        assertThat(debito.group()).contains("checked");
    }
}
