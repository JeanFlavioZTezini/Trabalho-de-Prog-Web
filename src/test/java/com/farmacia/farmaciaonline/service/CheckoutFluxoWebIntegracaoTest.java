package com.farmacia.farmaciaonline.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.util.StringUtils;

import com.farmacia.farmaciaonline.model.Apresentacao;
import com.farmacia.farmaciaonline.model.Pedido;
import com.farmacia.farmaciaonline.model.Usuario;
import com.farmacia.farmaciaonline.model.enums.MetodoPagamento;
import com.farmacia.farmaciaonline.model.enums.Perfil;
import com.farmacia.farmaciaonline.model.enums.StatusPedido;
import com.farmacia.farmaciaonline.model.enums.StatusUsuario;
import com.farmacia.farmaciaonline.security.UsuarioAutenticado;

/** O percurso pelas telas, com banco de verdade: rotas, autorização, o que aparece e o que nunca aparece. */
class CheckoutFluxoWebIntegracaoTest extends CheckoutTesteBase {

    @Autowired
    private MockMvc mvc;

    private static RequestPostProcessor como(Usuario usuario) {
        return user(UsuarioAutenticado.de(usuario));
    }

    private String pagina(Usuario usuario, String url) throws Exception {
        String html = mvc.perform(get(url).with(como(usuario)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(StringUtils.countOccurrencesOf(html, "<h1")).isEqualTo(1);
        // A moeda em pt-BR usa espaço não separável ("R$ 10,00"): troca para as comparações ficarem legíveis.
        return html.replace(' ', ' ');
    }

    private static String campoOculto(String html, String nome) {
        Matcher achado = Pattern.compile("name=\"" + nome + "\" value=\"([^\"]+)\"").matcher(html);
        assertThat(achado.find()).as("campo " + nome).isTrue();
        return achado.group(1);
    }

    /** Abre o resumo como o navegador e envia o formulário que ele trouxe. */
    private MockHttpServletResponse confirmar(Usuario cliente, String query, String... camposExtras) throws Exception {
        String html = pagina(cliente, "/checkout/resumo?" + query);
        var requisicao = post("/checkout/confirmar").with(como(cliente)).with(csrf())
                .param("conferencia", campoOculto(html, "conferencia"))
                .param("chave", campoOculto(html, "chave"));
        for (String par : query.split("&")) {
            requisicao.param(par.split("=")[0], par.split("=")[1]);
        }
        for (int i = 0; i < camposExtras.length; i += 2) {
            requisicao.param(camposExtras[i], camposExtras[i + 1]);
        }
        return mvc.perform(requisicao).andReturn().getResponse();
    }

    private static Long idDoRedirecionamento(MockHttpServletResponse resposta) {
        assertThat(resposta.getRedirectedUrl()).startsWith("/checkout/pedidos/");
        return Long.valueOf(resposta.getRedirectedUrl().substring("/checkout/pedidos/".length()));
    }

    @Test
    @DisplayName("visitante é mandado para o login, e POST sem CSRF é recusado")
    void acesso() throws Exception {
        mvc.perform(get("/checkout")).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
        mvc.perform(get("/checkout/pedidos/1")).andExpect(status().is3xxRedirection());
        mvc.perform(post("/checkout/confirmar").with(como(novoCliente()))).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("administrador recebe 403 no checkout")
    void adminNaoCompra() throws Exception {
        Usuario admin = novoUsuario(Perfil.ADMIN, StatusUsuario.ATIVA);

        mvc.perform(get("/checkout").with(como(admin))).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("carrinho vazio mostra aviso, e o resumo volta para o checkout")
    void carrinhoVazio() throws Exception {
        Usuario cliente = novoCliente();

        assertThat(pagina(cliente, "/checkout")).contains("Seu carrinho está vazio");
        mvc.perform(get("/checkout/resumo").with(como(cliente))).andExpect(redirectedUrl("/checkout"));
    }

    @Test
    @DisplayName("volta do cadastro de endereço: /checkout?enderecoId deixa o endereço marcado; o de outra pessoa dá 404")
    void retornoDosEnderecos() throws Exception {
        Usuario cliente = novoCliente();
        Usuario outro = novoCliente();
        noCarrinho(cliente, novaApresentacao("10.00", 5), 1);
        Long meu = novoEndereco(cliente, "79002-000", "MS");
        Long alheio = novoEndereco(outro, "79002-000", "MS");

        String html = pagina(cliente, "/checkout?enderecoId=" + meu);
        assertThat(html).contains("Avenida Teste").contains("/enderecos?retorno=checkout");

        mvc.perform(get("/checkout?enderecoId=" + alheio).with(como(cliente))).andExpect(status().isNotFound());
        mvc.perform(get("/checkout/resumo?tipoEntrega=ENTREGA&metodo=PIX&enderecoId=" + alheio).with(como(cliente)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("compra por entrega com Pix: resumo, confirmação, tela do pedido e aprovação pelo botão demo")
    void percursoPix() throws Exception {
        Usuario cliente = novoCliente();
        Apresentacao a = novaApresentacao("10.50", 5);
        noCarrinho(cliente, a, 2);
        Long enderecoId = novoEndereco(cliente, "79002-000", "MS");
        String query = "tipoEntrega=ENTREGA&metodo=PIX&enderecoId=" + enderecoId;

        assertThat(pagina(cliente, "/checkout/resumo?" + query))
                .contains("R$ 21,00").contains("R$ 9,90").contains("R$ 30,90").contains("até <span>2</span> dias");

        Long id = idDoRedirecionamento(confirmar(cliente, query));

        String tela = pagina(cliente, "/checkout/pedidos/" + id);
        assertThat(tela).contains("Aguardando pagamento").contains("DEMO-PIX").contains("Simular pagamento aprovado")
                .contains("Avenida Teste").contains("Enviamos o resumo");

        mvc.perform(post("/checkout/pedidos/" + id + "/demo/aprovar").with(como(cliente)).with(csrf()))
                .andExpect(redirectedUrl("/checkout/pedidos/" + id));

        assertThat(pagina(cliente, "/checkout/pedidos/" + id)).contains("Pagamento aprovado").doesNotContain("Simular");
        assertThat(estoqueDe(a)).isEqualTo(3);
    }

    @Test
    @DisplayName("o total enviado pelo navegador é ignorado")
    void totalDoNavegadorIgnorado() throws Exception {
        Usuario cliente = novoCliente();
        noCarrinho(cliente, novaApresentacao("10.00", 5), 2);

        Long id = idDoRedirecionamento(confirmar(cliente, "tipoEntrega=RETIRADA&metodo=PIX&lojaId=1",
                "total", "0.01", "subtotal", "0.01", "frete", "0"));

        assertThat(pedidoRepository.findById(id).orElseThrow().getTotal()).isEqualByComparingTo("20.00");
    }

    @Test
    @DisplayName("cartão: número e código nunca voltam para a URL nem para a tela, e formato errado não cria pedido")
    void dadosDoCartaoNaoVazam() throws Exception {
        Usuario cliente = novoCliente();
        Apresentacao a = novaApresentacao("10.00", 5);
        noCarrinho(cliente, a, 1);
        String query = "tipoEntrega=RETIRADA&metodo=CARTAO_CREDITO&lojaId=1";

        MockHttpServletResponse invalido = confirmar(cliente, query, "numeroCartao", "4111111111111111",
                "validadeCartao", "99/99", "cvv", "987", "nomeImpresso", "CLIENTE TESTE");
        assertThat(invalido.getRedirectedUrl()).startsWith("/checkout/resumo").doesNotContain("4111").doesNotContain("987");
        assertThat(estoqueDe(a)).isEqualTo(5);
        assertThat(itensNoCarrinho(cliente)).isEqualTo(1);

        MockHttpServletResponse recusado = confirmar(cliente, query, "numeroCartao", CARTAO_RECUSADO,
                "validadeCartao", "12/40", "cvv", "987", "nomeImpresso", "CLIENTE TESTE");
        Long id = idDoRedirecionamento(recusado);

        String tela = pagina(cliente, "/checkout/pedidos/" + id);
        assertThat(tela).contains("Pagamento recusado").contains("continuam reservados")
                .doesNotContain(CARTAO_RECUSADO).doesNotContain("987");

        mvc.perform(post("/checkout/pedidos/" + id + "/pagamento").with(como(cliente)).with(csrf())
                        .param("chave", campoOculto(tela, "chave"))
                        .param("metodo", "CARTAO_DEBITO")
                        .param("numeroCartao", CARTAO_APROVADO).param("validadeCartao", "12/40")
                        .param("cvv", "987").param("nomeImpresso", "CLIENTE TESTE"))
                .andExpect(redirectedUrl("/checkout/pedidos/" + id));

        Pedido pedido = pedidoRepository.findById(id).orElseThrow();
        assertThat(pedido.getStatus()).isEqualTo(StatusPedido.PAGAMENTO_APROVADO);
        assertThat(pagina(cliente, "/checkout/pedidos/" + id)).contains("Pagamento aprovado").doesNotContain(CARTAO_APROVADO);
    }

    @Test
    @DisplayName("preço alterado: sem botão de confirmar até aceitar; ao aceitar, volta ao resumo com o preço novo")
    void precoAlteradoNaTela() throws Exception {
        Usuario cliente = novoCliente();
        Apresentacao a = novaApresentacao("10.00", 5);
        noCarrinho(cliente, a, 1);
        mudarPreco(a, "15.00");
        String query = "tipoEntrega=RETIRADA&metodo=PIX&lojaId=1";

        String comAviso = pagina(cliente, "/checkout/resumo?" + query);
        assertThat(comAviso).contains("O preço de um item mudou").contains("Aceitar os novos preços")
                .contains("R$ 15,00").contains("R$ 10,00").doesNotContain("Confirmar pedido");

        mvc.perform(post("/checkout/precos").with(como(cliente)).with(csrf())
                        .param("tipoEntrega", "RETIRADA").param("metodo", "PIX").param("lojaId", "1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/checkout/resumo?*"));

        assertThat(pagina(cliente, "/checkout/resumo?" + query)).contains("Confirmar pedido").doesNotContain("Aceitar os novos preços");
    }

    @Test
    @DisplayName("resumo aberto e preço muda antes de confirmar: volta ao resumo sem criar pedido")
    void precoMudaComResumoAberto() throws Exception {
        Usuario cliente = novoCliente();
        Apresentacao a = novaApresentacao("10.00", 5);
        noCarrinho(cliente, a, 1);
        String html = pagina(cliente, "/checkout/resumo?tipoEntrega=RETIRADA&metodo=PIX&lojaId=1");

        mudarPreco(a, "15.00");

        mvc.perform(post("/checkout/confirmar").with(como(cliente)).with(csrf())
                        .param("tipoEntrega", "RETIRADA").param("metodo", "PIX").param("lojaId", "1")
                        .param("conferencia", campoOculto(html, "conferencia"))
                        .param("chave", campoOculto(html, "chave")))
                .andExpect(redirectedUrlPattern("/checkout/resumo?*"));

        assertThat(estoqueDe(a)).isEqualTo(5);
        assertThat(itensNoCarrinho(cliente)).isEqualTo(1);
    }

    @Test
    @DisplayName("controlado: a tela não oferece entrega, avisa da receita e o POST de entrega é recusado")
    void controladoNaTela() throws Exception {
        Usuario cliente = novoCliente();
        Apresentacao controlado = novaApresentacao("18.50", 5, true);
        noCarrinho(cliente, controlado, 1);
        Long enderecoId = novoEndereco(cliente, "79002-000", "MS");

        assertThat(pagina(cliente, "/checkout")).contains("retirado em loja").doesNotContain("value=\"ENTREGA\"");
        String query = "tipoEntrega=ENTREGA&metodo=PIX&enderecoId=" + enderecoId;
        assertThat(pagina(cliente, "/checkout/resumo?" + query)).contains("só pode ser retirado em loja")
                .doesNotContain("Confirmar pedido");

        mvc.perform(post("/checkout/confirmar").with(como(cliente)).with(csrf())
                        .param("tipoEntrega", "ENTREGA").param("metodo", "PIX")
                        .param("enderecoId", String.valueOf(enderecoId))
                        .param("conferencia", "qualquer").param("chave", UUID.randomUUID().toString()))
                .andExpect(redirectedUrlPattern("/checkout/resumo?*"));
        assertThat(estoqueDe(controlado)).isEqualTo(5);

        Long id = idDoRedirecionamento(confirmar(cliente, "tipoEntrega=RETIRADA&metodo=PIX&lojaId=1"));
        assertThat(pagina(cliente, "/checkout/pedidos/" + id)).contains("apresente a receita na loja").contains("Farma++ Centro");
    }

    @Test
    @DisplayName("pedido de outra pessoa responde 404 em todas as rotas")
    void pedidoAlheio() throws Exception {
        Usuario dono = novoCliente();
        Usuario outro = novoCliente();
        noCarrinho(dono, novaApresentacao("10.00", 5), 1);
        Long id = comprar(dono, retirada(MetodoPagamento.PIX), null);

        mvc.perform(get("/checkout/pedidos/" + id).with(como(outro))).andExpect(status().isNotFound());
        for (String acao : new String[] {"/atualizar", "/resumo", "/demo/aprovar", "/demo/expirar"}) {
            mvc.perform(post("/checkout/pedidos/" + id + acao).with(como(outro)).with(csrf()))
                    .andExpect(status().isNotFound());
        }
        mvc.perform(post("/checkout/pedidos/" + id + "/pagamento").with(como(outro)).with(csrf())
                        .param("chave", UUID.randomUUID().toString()).param("metodo", "PIX"))
                .andExpect(status().isNotFound());

        assertThat(pedidoRepository.findById(id).orElseThrow().getStatus()).isEqualTo(StatusPedido.AGUARDANDO_PAGAMENTO);
    }

    @Test
    @DisplayName("falha no envio do resumo: a tela orienta e oferece reenviar")
    void reenvioDoResumo() throws Exception {
        Usuario cliente = novoCliente();
        noCarrinho(cliente, novaApresentacao("10.00", 5), 1);
        doThrow(new IllegalStateException("fora do ar")).when(envio).enviar(any(), anyString(), anyString(), anyString());

        Long id = idDoRedirecionamento(confirmar(cliente, "tipoEntrega=RETIRADA&metodo=PIX&lojaId=1"));

        assertThat(pagina(cliente, "/checkout/pedidos/" + id)).contains("não conseguimos enviar o resumo").contains("Reenviar resumo");

        doCallRealMethod().when(envio).enviar(any(), anyString(), anyString(), anyString());
        mvc.perform(post("/checkout/pedidos/" + id + "/resumo").with(como(cliente)).with(csrf()))
                .andExpect(redirectedUrl("/checkout/pedidos/" + id));

        assertThat(pagina(cliente, "/checkout/pedidos/" + id)).doesNotContain("Reenviar resumo").contains("Enviamos o resumo");
    }

    @Test
    @DisplayName("expiração pelo botão demo: a tela mostra o cancelamento e o estoque volta")
    void expiracaoDemo() throws Exception {
        Usuario cliente = novoCliente();
        Apresentacao a = novaApresentacao("10.00", 5);
        noCarrinho(cliente, a, 1);
        Long id = idDoRedirecionamento(confirmar(cliente, "tipoEntrega=RETIRADA&metodo=BOLETO&lojaId=1"));

        mvc.perform(post("/checkout/pedidos/" + id + "/demo/expirar").with(como(cliente)).with(csrf()))
                .andExpect(redirectedUrl("/checkout/pedidos/" + id));

        assertThat(pagina(cliente, "/checkout/pedidos/" + id)).contains("foi cancelado").doesNotContain("Pagar este pedido");
        assertThat(estoqueDe(a)).isEqualTo(5);
    }

    @Test
    @DisplayName("o endpoint administrativo de forçar status também atualiza o pedido")
    void forcarStatusDoAdmin() throws Exception {
        Usuario cliente = novoCliente();
        Usuario admin = novoUsuario(Perfil.ADMIN, StatusUsuario.ATIVA);
        noCarrinho(cliente, novaApresentacao("10.00", 5), 1);
        Long id = comprar(cliente, retirada(MetodoPagamento.PIX), null);
        String transacao = pagamentoRepository.findByPedidoId(id).get(0).getTransacaoGatewayId();

        mvc.perform(post("/demo/pagamento/forcar-status").with(como(admin)).with(csrf())
                        .param("transacaoGatewayId", transacao).param("novoStatus", "APROVADO"))
                .andExpect(status().isOk());

        assertThat(pedidoRepository.findById(id).orElseThrow().getStatus()).isEqualTo(StatusPedido.PAGAMENTO_APROVADO);
    }
}
