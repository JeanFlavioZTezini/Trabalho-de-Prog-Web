package com.farmacia.farmaciaonline.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.farmacia.farmaciaonline.model.EnderecoEntrega;
import com.farmacia.farmaciaonline.model.ItemPedido;
import com.farmacia.farmaciaonline.model.Pedido;
import com.farmacia.farmaciaonline.model.Usuario;
import com.farmacia.farmaciaonline.model.enums.StatusPedido;
import com.farmacia.farmaciaonline.model.enums.TipoEntrega;
import com.farmacia.farmaciaonline.repository.HistoricoStatusPedidoRepository;
import com.farmacia.farmaciaonline.repository.PagamentoRepository;
import com.farmacia.farmaciaonline.repository.PedidoRepository;
import com.farmacia.farmaciaonline.repository.UsuarioRepository;

/** Próximos status oferecidos pelo painel (UC17) para um pedido pronto para retirada. */
class AdminPedidosServiceTest {

    private PedidoRepository pedidos;
    private AdminPedidosService service;

    @BeforeEach
    void preparar() {
        pedidos = mock(PedidoRepository.class);
        PagamentoRepository pagamentos = mock(PagamentoRepository.class);
        HistoricoStatusPedidoRepository historico = mock(HistoricoStatusPedidoRepository.class);
        when(pagamentos.findByPedidoIdOrderByIdDesc(1L)).thenReturn(List.of());
        when(historico.findByPedidoIdOrderByCriadoEmAscIdAsc(1L)).thenReturn(List.of());
        service = new AdminPedidosService(pedidos, pagamentos, historico, mock(UsuarioRepository.class),
                mock(EstadoPedidoService.class));
    }

    private void prontoParaRetirada(boolean exigeReceita, Instant conferidaEm) {
        Pedido pedido = new Pedido();
        pedido.setId(1L);
        pedido.setVersao(0L);
        pedido.setStatus(StatusPedido.PRONTO_PARA_RETIRADA);
        pedido.setTipoEntrega(TipoEntrega.RETIRADA);
        Usuario cliente = new Usuario();
        cliente.setNome("Cliente");
        pedido.setUsuario(cliente);
        ItemPedido item = new ItemPedido();
        item.setExigeReceita(exigeReceita);
        item.setPrecoUnitario(java.math.BigDecimal.TEN);
        item.setQuantidade(1);
        pedido.getItens().add(item);
        pedido.setReceitaConferidaEm(conferidaEm);
        when(pedidos.findById(1L)).thenReturn(Optional.of(pedido));
    }

    @Test
    @DisplayName("retirada sem receita: pode ir direto para Entregue")
    void semReceitaLiberaEntregue() {
        prontoParaRetirada(false, null);

        assertThat(service.detalhar(1L).proximosStatus()).containsExactly(StatusPedido.ENTREGUE);
    }

    @Test
    @DisplayName("retirada com receita sem conferência: nenhuma mudança até conferir")
    void comReceitaSemConferenciaBloqueia() {
        prontoParaRetirada(true, null);

        assertThat(service.detalhar(1L).proximosStatus()).isEmpty();
    }

    @Test
    @DisplayName("retirada com receita já conferida: libera Entregue")
    void comReceitaConferidaLibera() {
        prontoParaRetirada(true, Instant.now());

        assertThat(service.detalhar(1L).proximosStatus()).containsExactly(StatusPedido.ENTREGUE);
    }

    private void emSeparacaoParaEntrega(String complemento) {
        Pedido pedido = new Pedido();
        pedido.setId(1L);
        pedido.setVersao(0L);
        pedido.setStatus(StatusPedido.EM_SEPARACAO);
        pedido.setTipoEntrega(TipoEntrega.ENTREGA);
        Usuario cliente = new Usuario();
        cliente.setNome("Cliente");
        pedido.setUsuario(cliente);
        EnderecoEntrega endereco = new EnderecoEntrega();
        endereco.setDestinatario("Maria Silva");
        endereco.setLogradouro("Rua 14 de Julho");
        endereco.setNumero("100");
        endereco.setComplemento(complemento);
        endereco.setBairro("Centro");
        endereco.setCidade("Campo Grande");
        endereco.setUf("MS");
        endereco.setCep("79002000");
        pedido.setEnderecoEntrega(endereco);
        when(pedidos.findById(1L)).thenReturn(Optional.of(pedido));
    }

    @Test
    @DisplayName("entrega: o local é a cópia do endereço, com complemento quando houver, e o próximo passo é Enviado")
    void entregaMostraOEnderecoCopiado() {
        emSeparacaoParaEntrega("Apto 12");

        AdminPedidosService.Detalhe detalhe = service.detalhar(1L);
        assertThat(detalhe.local()).isEqualTo(
                "Maria Silva — Rua 14 de Julho, 100 (Apto 12) — Centro, Campo Grande/MS — CEP 79002000");
        assertThat(detalhe.proximosStatus()).containsExactly(StatusPedido.ENVIADO, StatusPedido.CANCELADO);
    }

    @Test
    @DisplayName("entrega sem complemento: nada de parênteses vazios")
    void entregaSemComplemento() {
        emSeparacaoParaEntrega(" ");

        assertThat(service.detalhar(1L).local()).isEqualTo(
                "Maria Silva — Rua 14 de Julho, 100 — Centro, Campo Grande/MS — CEP 79002000");
    }
}
