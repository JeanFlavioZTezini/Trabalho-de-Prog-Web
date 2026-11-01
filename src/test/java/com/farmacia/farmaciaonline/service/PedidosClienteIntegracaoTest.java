package com.farmacia.farmaciaonline.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.server.ResponseStatusException;

import com.farmacia.farmaciaonline.model.Apresentacao;
import com.farmacia.farmaciaonline.model.EnderecoForm;
import com.farmacia.farmaciaonline.model.Usuario;
import com.farmacia.farmaciaonline.model.enums.MetodoPagamento;
import com.farmacia.farmaciaonline.model.enums.Perfil;
import com.farmacia.farmaciaonline.model.enums.StatusPedido;
import com.farmacia.farmaciaonline.model.enums.StatusUsuario;
import com.farmacia.farmaciaonline.security.UsuarioAutenticado;
import com.farmacia.farmaciaonline.service.PedidosClienteService.DetalhePedido;
import com.farmacia.farmaciaonline.service.PedidosClienteService.PaginaDePedidos;

/**
 * UC06 com banco de verdade: o que cada cliente enxerga, a ordem, as cópias gravadas na compra e a linha do tempo.
 * Cada teste cria os próprios clientes e produtos (a base não usa rollback).
 */
class PedidosClienteIntegracaoTest extends CheckoutTesteBase {

    @Autowired
    private PedidosClienteService pedidosCliente;

    @Autowired
    private MockMvc mvc;

    private static RequestPostProcessor como(Usuario usuario) {
        return user(UsuarioAutenticado.de(usuario));
    }

    private static void assertStatus(Runnable consulta, HttpStatus esperado) {
        assertThatThrownBy(consulta::run).isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(esperado));
    }

    /** Compra por retirada com Pix (fica aguardando pagamento) e devolve o número do pedido. */
    private Long compraComPix(Usuario cliente, Apresentacao apresentacao, int quantidade) {
        noCarrinho(cliente, apresentacao, quantidade);
        return comprar(cliente, retirada(MetodoPagamento.PIX), null);
    }

    @Test
    @DisplayName("lista só os pedidos do cliente, do mais recente para o mais antigo, com o total de unidades")
    void listaOsPedidosDoCliente() {
        Usuario cliente = novoCliente();
        Usuario outro = novoCliente();
        Apresentacao a = novaApresentacao("10.00", 20);
        Apresentacao b = novaApresentacao("5.00", 20);

        Long primeiro = compraComPix(cliente, a, 3);
        noCarrinho(cliente, a, 1);
        noCarrinho(cliente, b, 1);
        Long segundo = comprar(cliente, retirada(MetodoPagamento.PIX), null);
        Long dele = compraComPix(outro, a, 1);

        PaginaDePedidos lista = pedidosCliente.listar(cliente.getId(), 0);

        assertThat(lista.getPedidos()).extracting(p -> p.getId()).containsExactly(segundo, primeiro)
                .doesNotContain(dele);
        assertThat(lista.getTotalPedidos()).isEqualTo(2);
        assertThat(lista.getPedidos().get(0).getUnidades()).isEqualTo(2);   // 1 + 1
        assertThat(lista.getPedidos().get(1).getUnidades()).isEqualTo(3);
        assertThat(lista.getPedidos().get(1).getStatus()).isEqualTo(StatusPedido.AGUARDANDO_PAGAMENTO);
        assertThat(pedidosCliente.listar(outro.getId(), 0).getPedidos()).extracting(p -> p.getId()).containsExactly(dele);
    }

    @Test
    @DisplayName("cliente sem pedidos recebe lista vazia")
    void semPedidos() {
        PaginaDePedidos lista = pedidosCliente.listar(novoCliente().getId(), 0);

        assertThat(lista.isVazia()).isTrue();
        assertThat(lista.getTotalPaginas()).isZero();
    }

    @Test
    @DisplayName("pagina de 10 em 10; página além do fim cai na última e página negativa na primeira")
    void paginacao() {
        Usuario cliente = novoCliente();
        Apresentacao a = novaApresentacao("1.00", 100);
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            ids.add(compraComPix(cliente, a, 1));
        }

        PaginaDePedidos primeira = pedidosCliente.listar(cliente.getId(), 0);
        PaginaDePedidos segunda = pedidosCliente.listar(cliente.getId(), 1);

        assertThat(primeira.getPedidos()).hasSize(10);
        assertThat(primeira.getTotalPaginas()).isEqualTo(2);
        assertThat(primeira.isTemProxima()).isTrue();
        assertThat(primeira.isTemAnterior()).isFalse();
        assertThat(primeira.getPedidos().get(0).getId()).isEqualTo(ids.get(11));   // o mais recente
        assertThat(segunda.getPedidos()).hasSize(2);
        assertThat(segunda.getPedidos().get(1).getId()).isEqualTo(ids.get(0));     // o mais antigo
        assertThat(pedidosCliente.listar(cliente.getId(), 9).getPagina()).isEqualTo(1);
        assertThat(pedidosCliente.listar(cliente.getId(), -3).getPagina()).isZero();
    }

    @Test
    @DisplayName("pedido de outra pessoa responde igual a pedido inexistente (404)")
    void naoEnxergaPedidoAlheio() {
        Usuario dono = novoCliente();
        Usuario intruso = novoCliente();
        Long pedido = compraComPix(dono, novaApresentacao("10.00", 5), 1);

        assertThat(pedidosCliente.detalhar(dono.getId(), pedido).getId()).isEqualTo(pedido);
        assertStatus(() -> pedidosCliente.detalhar(intruso.getId(), pedido), HttpStatus.NOT_FOUND);
        assertStatus(() -> pedidosCliente.detalhar(dono.getId(), pedido + 100_000), HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("administrador e conta pendente não usam a consulta do cliente (403)")
    void soClienteAtivo() {
        Usuario admin = novoUsuario(Perfil.ADMIN, StatusUsuario.ATIVA);
        Usuario pendente = novoUsuario(Perfil.CLIENTE, StatusUsuario.PENDENTE);

        assertStatus(() -> pedidosCliente.listar(admin.getId(), 0), HttpStatus.FORBIDDEN);
        assertStatus(() -> pedidosCliente.detalhar(admin.getId(), 1L), HttpStatus.FORBIDDEN);
        assertStatus(() -> pedidosCliente.listar(pendente.getId(), 0), HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("o detalhe mostra a cópia do endereço da compra, mesmo depois de editar ou excluir o endereço salvo")
    void copiaDoEnderecoSobreviveAEdicao() {
        Usuario cliente = novoCliente();
        Long enderecoId = novoEndereco(cliente, "79002-000", "MS");
        noCarrinho(cliente, novaApresentacao("10.00", 5), 1);
        Long pedido = comprar(cliente, entrega(enderecoId, MetodoPagamento.PIX), null);

        EnderecoForm outro = enderecos.formDe(cliente.getId(), enderecoId);
        outro.setLogradouro("Rua Nova Depois da Compra");
        outro.setNumero("999");
        enderecos.salvar(cliente.getId(), enderecoId, outro);
        assertThat(pedidosCliente.detalhar(cliente.getId(), pedido).getEnderecoEntrega().getLogradouro())
                .isEqualTo("Avenida Teste");

        enderecos.excluir(cliente.getId(), enderecoId);
        DetalhePedido detalhe = pedidosCliente.detalhar(cliente.getId(), pedido);

        assertThat(detalhe.getEnderecoEntrega().getLogradouro()).isEqualTo("Avenida Teste");
        assertThat(detalhe.getEnderecoEntrega().getNumero()).isEqualTo("100");
        assertThat(detalhe.getLocalRetirada()).isNull();
        assertThat(detalhe.isEntrega()).isTrue();
        assertThat(detalhe.getFrete()).isNotNull();
        assertThat(detalhe.getTotal()).isEqualByComparingTo(detalhe.getSubtotal().add(detalhe.getFrete()));
    }

    @Test
    @DisplayName("o detalhe mostra o preço da compra, mesmo que o produto mude de preço depois")
    void precoDaCompraNaoMuda() {
        Usuario cliente = novoCliente();
        Apresentacao a = novaApresentacao("10.00", 5);
        Long pedido = compraComPix(cliente, a, 2);

        mudarPreco(a, "99.00");
        DetalhePedido detalhe = pedidosCliente.detalhar(cliente.getId(), pedido);

        assertThat(detalhe.getItens()).hasSize(1);
        assertThat(detalhe.getItens().get(0).getPrecoUnitario()).isEqualByComparingTo("10.00");
        assertThat(detalhe.getItens().get(0).getSubtotal()).isEqualByComparingTo("20.00");
        assertThat(detalhe.getItens().get(0).getNome()).startsWith("Produto ");
        assertThat(detalhe.getLocalRetirada()).isNotNull();
        assertThat(detalhe.getLocalRetirada().getNome()).isNotBlank();
    }

    @Test
    @DisplayName("pagamento aparece mascarado: cartão só com bandeira e final; nunca número, CVV, código ou id do gateway")
    void pagamentoMascarado() {
        Usuario cliente = novoCliente();
        noCarrinho(cliente, novaApresentacao("10.00", 5), 1);
        Long pedido = comprar(cliente, retirada(MetodoPagamento.CARTAO_CREDITO), cartao(CARTAO_APROVADO));

        DetalhePedido detalhe = pedidosCliente.detalhar(cliente.getId(), pedido);

        assertThat(detalhe.getPagamentos()).hasSize(1);
        var pagamento = detalhe.getPagamentos().get(0);
        assertThat(pagamento.getMetodo()).isEqualTo("Cartão de crédito");
        assertThat(pagamento.getSituacao()).isEqualTo("Aprovado");
        assertThat(pagamento.getCartaoMascarado()).endsWith("final 1111").doesNotContain(CARTAO_APROVADO);
        assertThat(detalhe.getStatus()).isEqualTo(StatusPedido.PAGAMENTO_APROVADO);
    }

    @Test
    @DisplayName("linha do tempo na ordem das mudanças, com o último passo como atual")
    void linhaDoTempo() {
        Usuario cliente = novoCliente();
        Long pedido = compraComPix(cliente, novaApresentacao("10.00", 5), 1);

        DetalhePedido antes = pedidosCliente.detalhar(cliente.getId(), pedido);
        assertThat(antes.getHistorico()).extracting(p -> p.getStatus()).containsExactly(StatusPedido.AGUARDANDO_PAGAMENTO);
        assertThat(antes.getHistorico().get(0).isAtual()).isTrue();

        assertThat(estado.cancelar(pedido, null, "Cancelado pelo teste")).isTrue();
        DetalhePedido depois = pedidosCliente.detalhar(cliente.getId(), pedido);

        assertThat(depois.getStatus()).isEqualTo(StatusPedido.CANCELADO);
        assertThat(depois.getHistorico()).extracting(p -> p.getStatus())
                .containsExactly(StatusPedido.AGUARDANDO_PAGAMENTO, StatusPedido.CANCELADO);
        assertThat(depois.getHistorico()).extracting(p -> p.isAtual()).containsExactly(false, true);
        assertThat(depois.getHistorico().get(1).getQuandoFormatado()).matches("\\d{2}/\\d{2}/\\d{4} às \\d{2}:\\d{2}");
    }

    @Test
    @DisplayName("pedido sem histórico gravado ainda mostra a situação atual")
    void semHistorico() {
        Usuario cliente = novoCliente();
        Long pedido = compraComPix(cliente, novaApresentacao("10.00", 5), 1);
        historicoRepository.deleteAll(historicoRepository.findByPedidoIdOrderByCriadoEmAsc(pedido));

        DetalhePedido detalhe = pedidosCliente.detalhar(cliente.getId(), pedido);

        assertThat(detalhe.getHistorico()).hasSize(1);
        assertThat(detalhe.getHistorico().get(0).getStatus()).isEqualTo(StatusPedido.AGUARDANDO_PAGAMENTO);
        assertThat(detalhe.getHistorico().get(0).isAtual()).isTrue();
    }

    // ---------- Pela web, com a segurança de verdade ----------

    @Test
    @DisplayName("pela URL: o cliente vê o próprio pedido; o de outro dá 404; o administrador e o visitante não entram")
    void acessoPelaUrl() throws Exception {
        Usuario dono = novoCliente();
        Usuario intruso = novoCliente();
        Usuario admin = novoUsuario(Perfil.ADMIN, StatusUsuario.ATIVA);
        Long pedido = compraComPix(dono, novaApresentacao("10.00", 5), 1);

        String html = mvc.perform(get("/pedidos/" + pedido).with(como(dono))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(html).contains("Pedido nº <span>" + pedido + "</span>").contains("Aguardando pagamento");

        mvc.perform(get("/pedidos/" + pedido).with(como(intruso))).andExpect(status().isNotFound());
        mvc.perform(get("/pedidos/" + pedido).with(como(admin))).andExpect(status().isForbidden());
        mvc.perform(get("/pedidos").with(como(admin))).andExpect(status().isForbidden());
        mvc.perform(get("/pedidos/" + pedido)).andExpect(status().is3xxRedirection());

        // o cliente não consegue mudar o status do próprio pedido
        mvc.perform(post("/pedidos/" + pedido).with(como(dono)).with(csrf()).param("status", "ENTREGUE"))
                .andExpect(status().isMethodNotAllowed());
        assertThat(pedidoRepository.findById(pedido).orElseThrow().getStatus()).isEqualTo(StatusPedido.AGUARDANDO_PAGAMENTO);
    }
}
