package com.farmacia.farmaciaonline.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.server.ResponseStatusException;

import com.farmacia.farmaciaonline.model.Apresentacao;
import com.farmacia.farmaciaonline.model.CartaoForm;
import com.farmacia.farmaciaonline.model.Usuario;
import com.farmacia.farmaciaonline.model.enums.MetodoPagamento;
import com.farmacia.farmaciaonline.model.enums.StatusPagamento;
import com.farmacia.farmaciaonline.repository.CartaoSalvoRepository;
import com.farmacia.farmaciaonline.repository.EnderecoRepository;
import com.farmacia.farmaciaonline.security.UsuarioAutenticado;

/**
 * F-25: comprar novamente, endereço principal e cartão salvo por token. Usa a base do checkout (pedidos de verdade,
 * sem rollback); cada teste cria os próprios clientes e produtos.
 */
class FacilidadesOpcionaisIntegracaoTest extends CheckoutTesteBase {

    @Autowired
    private RecompraService recompra;

    @Autowired
    private CartaoSalvoService cartoes;

    @Autowired
    private CartaoSalvoRepository cartaoRepository;

    @Autowired
    private EnderecoRepository enderecoRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MockMvc mvc;

    private static RequestPostProcessor como(Usuario usuario) {
        return user(UsuarioAutenticado.de(usuario));
    }

    private Long compraComPix(Usuario cliente, Apresentacao... apresentacoes) {
        for (Apresentacao a : apresentacoes) {
            noCarrinho(cliente, a, 2);
        }
        return comprar(cliente, retirada(MetodoPagamento.PIX), null);
    }

    /** Quantidade de cada apresentação no carrinho do cliente. */
    private Map<Long, Integer> carrinhoDe(Usuario cliente) {
        return transacao.execute(s -> carrinhoRepository.findByUsuarioId(cliente.getId())
                .map(c -> c.getItens().stream().collect(java.util.stream.Collectors.toMap(
                        i -> i.getApresentacao().getId(), i -> i.getQuantidade())))
                .orElse(Map.of()));
    }

    private void desativar(Apresentacao apresentacao) {
        jdbc.update("update apresentacao set ativa = false where id = ?", apresentacao.getId());
    }

    private void estoque(Apresentacao apresentacao, int quantidade) {
        jdbc.update("update apresentacao set estoque = ? where id = ?", quantidade, apresentacao.getId());
    }

    private static CartaoForm salvando(String numero) {
        CartaoForm form = cartao(numero);
        form.setSalvarCartao(true);
        return form;
    }

    private static CartaoForm comSalvo(Long cartaoId) {
        CartaoForm form = new CartaoForm();
        form.setCartaoSalvoId(cartaoId);
        form.setCvv("123");
        return form;
    }

    // ---------- Comprar novamente ----------

    @Test
    @DisplayName("comprar novamente põe os itens do pedido no carrinho com o preço de agora")
    void recompraComPrecoAtual() {
        Usuario cliente = novoCliente();
        Apresentacao a = novaApresentacao("10.00", 20);
        Apresentacao b = novaApresentacao("5.00", 20);
        Long pedido = compraComPix(cliente, a, b);
        mudarPreco(a, "12.00");

        RecompraService.Resultado resultado = recompra.comprarNovamente(cliente.getId(), pedido);

        assertThat(resultado.adicionados()).isEqualTo(2);
        assertThat(carrinhoDe(cliente)).containsEntry(a.getId(), 2).containsEntry(b.getId(), 2);
        BigDecimal precoVisto = transacao.execute(s -> carrinhoRepository.findByUsuarioId(cliente.getId()).orElseThrow()
                .getItens().stream().filter(i -> i.getApresentacao().getId().equals(a.getId())).findFirst()
                .orElseThrow().getPrecoVisto());
        assertThat(precoVisto).isEqualByComparingTo("12.00");
    }

    @Test
    @DisplayName("comprar novamente: item inativo ou sem estoque vira aviso e não impede os outros")
    void recompraComItemIndisponivel() {
        Usuario cliente = novoCliente();
        Apresentacao inativa = novaApresentacao("10.00", 20);
        Apresentacao semEstoque = novaApresentacao("10.00", 20);
        Apresentacao ok = novaApresentacao("10.00", 20);
        Long pedido = compraComPix(cliente, inativa, semEstoque, ok);
        desativar(inativa);
        estoque(semEstoque, 0);

        RecompraService.Resultado resultado = recompra.comprarNovamente(cliente.getId(), pedido);

        assertThat(resultado.adicionados()).isEqualTo(1);
        assertThat(resultado.total()).isEqualTo(3);
        assertThat(resultado.avisos()).hasSize(2)
                .anyMatch(a -> a.contains("não está mais disponível"))
                .anyMatch(a -> a.contains("sem estoque"));
        assertThat(carrinhoDe(cliente)).containsOnlyKeys(ok.getId());
    }

    @Test
    @DisplayName("comprar novamente respeita o estoque atual (ajusta e avisa)")
    void recompraAjustaAoEstoque() {
        Usuario cliente = novoCliente();
        Apresentacao a = novaApresentacao("10.00", 20);
        Long pedido = compraComPix(cliente, a);
        estoque(a, 1);

        RecompraService.Resultado resultado = recompra.comprarNovamente(cliente.getId(), pedido);

        assertThat(carrinhoDe(cliente)).containsEntry(a.getId(), 1);
        assertThat(resultado.avisos()).isNotEmpty();
    }

    @Test
    @DisplayName("comprar novamente com pedido de outra pessoa responde 404 e não mexe no carrinho")
    void recompraDePedidoAlheio() {
        Usuario dono = novoCliente();
        Usuario outro = novoCliente();
        Long pedido = compraComPix(dono, novaApresentacao("10.00", 20));

        assertThatThrownBy(() -> recompra.comprarNovamente(outro.getId(), pedido))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        assertThat(carrinhoDe(outro)).isEmpty();
    }

    @Test
    @DisplayName("rota de comprar novamente: leva ao carrinho com mensagem, exige CSRF e login")
    void rotaDeRecompra() throws Exception {
        Usuario cliente = novoCliente();
        Apresentacao a = novaApresentacao("10.00", 20);
        Long pedido = compraComPix(cliente, a);

        mvc.perform(post("/pedidos/{id}/comprar-novamente", pedido).with(como(cliente)).with(csrf()))
                .andExpect(redirectedUrl("/carrinho"))
                .andExpect(flash().attribute("sucesso", org.hamcrest.Matchers.containsString("adicionados ao carrinho")));
        mvc.perform(post("/pedidos/{id}/comprar-novamente", pedido).with(como(cliente)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/pedidos/{id}/comprar-novamente", pedido).with(csrf()))
                .andExpect(redirectedUrlPattern("**/login*"));

        String html = mvc.perform(get("/pedidos/{id}", pedido).with(como(cliente)))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(html).contains("/pedidos/" + pedido + "/comprar-novamente", "aria-describedby=\"comprar-novamente-ajuda\"");
    }

    @Test
    @DisplayName("comprar novamente sem nenhum item disponível mostra erro")
    void rotaDeRecompraSemItens() throws Exception {
        Usuario cliente = novoCliente();
        Apresentacao a = novaApresentacao("10.00", 20);
        Long pedido = compraComPix(cliente, a);
        desativar(a);

        mvc.perform(post("/pedidos/{id}/comprar-novamente", pedido).with(como(cliente)).with(csrf()))
                .andExpect(redirectedUrl("/carrinho"))
                .andExpect(flash().attribute("erro", org.hamcrest.Matchers.containsString("Nenhum item")));
    }

    // ---------- Endereço principal ----------

    private int principais(Usuario cliente) {
        return jdbc.queryForObject("select count(*) from endereco where usuario_id = ? and principal",
                Integer.class, cliente.getId());
    }

    @Test
    @DisplayName("primeiro endereço nasce principal; trocar deixa um só principal")
    void enderecoPrincipalUnico() {
        Usuario cliente = novoCliente();
        Long primeiro = novoEndereco(cliente, "79002000", "MS");
        Long segundo = novoEndereco(cliente, "79800001", "MS");

        assertThat(enderecoRepository.findById(primeiro).orElseThrow().isPrincipal()).isTrue();
        assertThat(enderecoRepository.findById(segundo).orElseThrow().isPrincipal()).isFalse();

        enderecos.definirPrincipal(cliente.getId(), segundo);

        assertThat(principais(cliente)).isEqualTo(1);
        assertThat(enderecos.listar(cliente.getId())).extracting(e -> e.getId()).first().isEqualTo(segundo);
    }

    @Test
    @DisplayName("excluir o principal promove o endereço mais recente")
    void excluirPrincipalPromove() {
        Usuario cliente = novoCliente();
        Long primeiro = novoEndereco(cliente, "79002000", "MS");
        Long segundo = novoEndereco(cliente, "79800001", "MS");

        enderecos.excluir(cliente.getId(), primeiro);

        assertThat(enderecoRepository.findById(segundo).orElseThrow().isPrincipal()).isTrue();
        assertThat(principais(cliente)).isEqualTo(1);
    }

    @Test
    @DisplayName("endereço principal de outra pessoa responde 404 e nada muda")
    void principalAlheio() {
        Usuario dono = novoCliente();
        Usuario outro = novoCliente();
        novoEndereco(dono, "79002000", "MS");
        Long doDono = novoEndereco(dono, "79800001", "MS");

        assertThatThrownBy(() -> enderecos.definirPrincipal(outro.getId(), doDono))
                .isInstanceOf(ResponseStatusException.class);
        assertThat(enderecoRepository.findById(doDono).orElseThrow().isPrincipal()).isFalse();
    }

    @Test
    @DisplayName("checkout abre com o principal marcado e o pedido antigo guarda a cópia do endereço")
    void checkoutUsaPrincipalEPedidoNaoMuda() throws Exception {
        Usuario cliente = novoCliente();
        Long antigo = novoEndereco(cliente, "79002000", "MS");
        Long novo = novoEndereco(cliente, "79800001", "MS");
        noCarrinho(cliente, novaApresentacao("10.00", 20), 1);
        Long pedido = comprar(cliente, entrega(antigo, MetodoPagamento.PIX), null);
        enderecos.definirPrincipal(cliente.getId(), novo);
        noCarrinho(cliente, novaApresentacao("10.00", 20), 1);

        String html = mvc.perform(get("/checkout").with(como(cliente)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(html).containsPattern("name=\"enderecoId\" value=\"" + novo + "\"\\s+checked");
        String cepDoPedido = transacao.execute(s -> pedidoRepository.findById(pedido).orElseThrow()
                .getEnderecoEntrega().getCep());
        assertThat(cepDoPedido).isEqualTo("79002000");

        String lista = mvc.perform(get("/enderecos").with(como(cliente)))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(lista).contains("selo--principal", "/enderecos/" + antigo + "/principal");
    }

    @Test
    @DisplayName("rota de endereço principal exige CSRF")
    void rotaPrincipal() throws Exception {
        Usuario cliente = novoCliente();
        novoEndereco(cliente, "79002000", "MS");
        Long segundo = novoEndereco(cliente, "79800001", "MS");

        mvc.perform(post("/enderecos/{id}/principal", segundo).with(como(cliente))).andExpect(status().isForbidden());
        mvc.perform(post("/enderecos/{id}/principal", segundo).with(como(cliente)).with(csrf()))
                .andExpect(redirectedUrl("/enderecos"));
        assertThat(enderecoRepository.findById(segundo).orElseThrow().isPrincipal()).isTrue();
    }

    // ---------- Cartão salvo por token ----------

    private String linhasDoCartao(Usuario cliente) {
        return String.join("|", jdbc.queryForList(
                "select concat_ws(';', token_gateway, bandeira, ultimos4, validade_mes, validade_ano, nome_impresso) "
                        + "from cartao_salvo where usuario_id = ?", String.class, cliente.getId()));
    }

    @Test
    @DisplayName("pagamento aprovado com 'salvar cartão' guarda só token, bandeira, final e validade")
    void salvaCartaoAprovado() {
        Usuario cliente = novoCliente();
        noCarrinho(cliente, novaApresentacao("10.00", 20), 1);

        Long pedido = comprar(cliente, retirada(MetodoPagamento.CARTAO_CREDITO), salvando(CARTAO_APROVADO));

        List<com.farmacia.farmaciaonline.model.CartaoSalvo> salvos = cartaoRepository.findByUsuarioId(cliente.getId());
        assertThat(salvos).hasSize(1);
        assertThat(salvos.get(0).getTokenGateway()).startsWith("tok_demo_");
        assertThat(salvos.get(0).getUltimos4()).isEqualTo("1111");
        assertThat(salvos.get(0).getBandeira()).isEqualTo("VISA");
        assertThat(linhasDoCartao(cliente)).doesNotContain(CARTAO_APROVADO).doesNotContain(";123;");
        Long vinculado = jdbc.queryForObject("select cartao_salvo_id from pagamento where pedido_id = ?", Long.class,
                pedido);
        assertThat(vinculado).isEqualTo(salvos.get(0).getId());
    }

    @Test
    @DisplayName("pagamento recusado não salva o cartão; o mesmo cartão não é salvo duas vezes")
    void naoSalvaRecusadoNemDuplicado() {
        Usuario cliente = novoCliente();
        noCarrinho(cliente, novaApresentacao("10.00", 20), 1);
        comprar(cliente, retirada(MetodoPagamento.CARTAO_CREDITO), salvando(CARTAO_RECUSADO));
        assertThat(cartaoRepository.findByUsuarioId(cliente.getId())).isEmpty();

        for (int i = 0; i < 2; i++) {
            noCarrinho(cliente, novaApresentacao("10.00", 20), 1);
            comprar(cliente, retirada(MetodoPagamento.CARTAO_CREDITO), salvando(CARTAO_APROVADO));
        }
        assertThat(cartaoRepository.findByUsuarioId(cliente.getId())).hasSize(1);
    }

    @Test
    @DisplayName("pagar com o cartão salvo (pedindo o CVV de novo) aprova e registra o cartão no pagamento")
    void pagaComCartaoSalvo() {
        Usuario cliente = novoCliente();
        noCarrinho(cliente, novaApresentacao("10.00", 20), 1);
        comprar(cliente, retirada(MetodoPagamento.CARTAO_CREDITO), salvando(CARTAO_APROVADO));
        Long cartaoId = cartaoRepository.findByUsuarioId(cliente.getId()).get(0).getId();

        noCarrinho(cliente, novaApresentacao("10.00", 20), 1);
        Long pedido = comprar(cliente, retirada(MetodoPagamento.CARTAO_CREDITO), comSalvo(cartaoId));

        var pagamentosDoPedido = pagamentoRepository.findByPedidoIdOrderByIdDesc(pedido);
        assertThat(pagamentosDoPedido).hasSize(1);
        assertThat(pagamentosDoPedido.get(0).getStatus()).isEqualTo(StatusPagamento.APROVADO);
        assertThat(pagamentosDoPedido.get(0).getCartaoUltimos4()).isEqualTo("1111");
        assertThat(jdbc.queryForObject("select cartao_salvo_id from pagamento where pedido_id = ?", Long.class, pedido))
                .isEqualTo(cartaoId);
    }

    @Test
    @DisplayName("cartão salvo de outra pessoa: 404 e o gateway não é chamado")
    void cartaoAlheioNaoCobra() {
        Usuario dono = novoCliente();
        noCarrinho(dono, novaApresentacao("10.00", 20), 1);
        comprar(dono, retirada(MetodoPagamento.CARTAO_CREDITO), salvando(CARTAO_APROVADO));
        Long cartaoDoDono = cartaoRepository.findByUsuarioId(dono.getId()).get(0).getId();

        Usuario outro = novoCliente();
        noCarrinho(outro, novaApresentacao("10.00", 20), 1);
        clearInvocations(gateway);

        assertThatThrownBy(() -> comprar(outro, retirada(MetodoPagamento.CARTAO_CREDITO), comSalvo(cartaoDoDono)))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        verify(gateway, never()).processar(any());
        assertThat(jdbc.queryForObject("select count(*) from pedido where usuario_id = ?", Integer.class,
                outro.getId())).isZero();
    }

    @Test
    @DisplayName("com cartão salvo, o formulário só exige o código de segurança")
    void formularioComCartaoSalvo() {
        CartaoForm form = new CartaoForm();
        form.setCartaoSalvoId(1L);
        assertThat(form.errosDeFormato()).containsExactly("Informe o código de segurança do cartão, com 3 ou 4 números.");
        form.setCvv("123");
        assertThat(form.errosDeFormato()).isEmpty();
    }

    @Test
    @DisplayName("Meus cartões: lista mascarado, remove o próprio e 404 para o de outra pessoa")
    void meusCartoes() throws Exception {
        Usuario cliente = novoCliente();
        Usuario outro = novoCliente();
        noCarrinho(cliente, novaApresentacao("10.00", 20), 1);
        comprar(cliente, retirada(MetodoPagamento.CARTAO_CREDITO), salvando(CARTAO_APROVADO));
        Long cartaoId = cartaoRepository.findByUsuarioId(cliente.getId()).get(0).getId();

        String html = mvc.perform(get("/conta/cartoes").with(como(cliente)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(html).contains("VISA final 1111").doesNotContain(CARTAO_APROVADO).doesNotContain("tok_demo_");
        assertThat(html.split("<h1").length - 1).isEqualTo(1);

        mvc.perform(post("/conta/cartoes/{id}/remover", cartaoId).with(como(outro)).with(csrf()))
                .andExpect(status().isNotFound());
        assertThat(cartaoRepository.findById(cartaoId)).isPresent();

        mvc.perform(post("/conta/cartoes/{id}/remover", cartaoId).with(como(cliente)).with(csrf()))
                .andExpect(redirectedUrl("/conta/cartoes"));
        assertThat(cartaoRepository.findById(cartaoId)).isEmpty();
    }

    @Test
    @DisplayName("resumo do checkout oferece o cartão salvo e a opção de salvar")
    void resumoOfereceCartaoSalvo() throws Exception {
        Usuario cliente = novoCliente();
        noCarrinho(cliente, novaApresentacao("10.00", 20), 1);
        comprar(cliente, retirada(MetodoPagamento.CARTAO_CREDITO), salvando(CARTAO_APROVADO));
        noCarrinho(cliente, novaApresentacao("10.00", 20), 1);

        String html = mvc.perform(get("/checkout/resumo").with(como(cliente)).param("tipoEntrega", "RETIRADA")
                        .param("lojaId", "1").param("metodo", "CARTAO_CREDITO"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(html).contains("name=\"cartaoSalvoId\"", "VISA final 1111", "<label for=\"salvarCartao\"");
        assertThat(cartoes.listar(cliente.getId())).hasSize(1);
    }
}
