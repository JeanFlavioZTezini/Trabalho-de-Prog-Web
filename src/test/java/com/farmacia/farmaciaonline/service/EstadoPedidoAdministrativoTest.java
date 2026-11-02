package com.farmacia.farmaciaonline.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.farmacia.farmaciaonline.model.HistoricoStatusPedido;
import com.farmacia.farmaciaonline.model.Apresentacao;
import com.farmacia.farmaciaonline.model.ItemPedido;
import com.farmacia.farmaciaonline.model.Pedido;
import com.farmacia.farmaciaonline.model.Usuario;
import com.farmacia.farmaciaonline.model.enums.StatusPagamento;
import com.farmacia.farmaciaonline.model.enums.StatusPedido;
import com.farmacia.farmaciaonline.model.enums.TipoEntrega;
import com.farmacia.farmaciaonline.repository.ApresentacaoRepository;
import com.farmacia.farmaciaonline.repository.HistoricoStatusPedidoRepository;
import com.farmacia.farmaciaonline.repository.PagamentoRepository;
import com.farmacia.farmaciaonline.repository.PedidoRepository;
import com.farmacia.farmaciaonline.repository.ProdutoRepository;

import jakarta.persistence.EntityManager;

@ExtendWith(MockitoExtension.class)
class EstadoPedidoAdministrativoTest {

    @Mock private PedidoRepository pedidoRepository;
    @Mock private PagamentoRepository pagamentoRepository;
    @Mock private HistoricoStatusPedidoRepository historicoRepository;
    @Mock private ApresentacaoRepository apresentacaoRepository;
    @Mock private ProdutoRepository produtoRepository;
    @Mock private EntityManager entityManager;

    private EstadoPedidoService service;

    @BeforeEach
    void preparar() {
        service = new EstadoPedidoService(pedidoRepository, pagamentoRepository, historicoRepository,
                apresentacaoRepository, produtoRepository);
        ReflectionTestUtils.setField(service, "em", entityManager);
    }

    @Test
    void naoPermiteAprovarPagamentoPeloPainel() {
        Pedido pedido = pedido(StatusPedido.AGUARDANDO_PAGAMENTO, TipoEntrega.ENTREGA);
        prepararPedido(pedido);

        assertThatThrownBy(() -> service.transicionarAdministrativamente(42L, 4L,
                StatusPedido.PAGAMENTO_APROVADO, new Usuario(), null))
                .isInstanceOf(AcaoPedidoInvalidaException.class);

        assertThat(pedido.getStatus()).isEqualTo(StatusPedido.AGUARDANDO_PAGAMENTO);
        verifyNoInteractions(historicoRepository);
    }

    @Test
    void rejeitaFormularioComVersaoAntigaSemAlterarPedido() {
        Pedido pedido = pedido(StatusPedido.PAGAMENTO_APROVADO, TipoEntrega.ENTREGA);
        prepararPedido(pedido);

        assertThatThrownBy(() -> service.transicionarAdministrativamente(42L, 3L,
                StatusPedido.EM_SEPARACAO, new Usuario(), null))
                .isInstanceOf(ConflitoPedidoException.class)
                .hasMessageContaining("Recarregue");

        assertThat(pedido.getStatus()).isEqualTo(StatusPedido.PAGAMENTO_APROVADO);
        verifyNoInteractions(historicoRepository);
    }

    @Test
    void cancelarPedidoPagoRegistraEstornoPendenteEHistoricoComOrigem() {
        Pedido pedido = pedido(StatusPedido.PAGAMENTO_APROVADO, TipoEntrega.ENTREGA);
        prepararPedido(pedido);
        when(pagamentoRepository.findByPedidoIdAndStatus(42L, StatusPagamento.PENDENTE))
                .thenReturn(Optional.empty());
        Usuario admin = new Usuario();
        admin.setId(7L);
        admin.setNome("Admin teste");

        service.transicionarAdministrativamente(42L, 4L, StatusPedido.CANCELADO, admin, "Cancelado a pedido.");

        assertThat(pedido.getStatus()).isEqualTo(StatusPedido.CANCELADO);
        assertThat(pedido.isEstornoPendente()).isTrue();
        ArgumentCaptor<HistoricoStatusPedido> captor = ArgumentCaptor.forClass(HistoricoStatusPedido.class);
        verify(historicoRepository).save(captor.capture());
        assertThat(captor.getValue().getStatusAnterior()).isEqualTo(StatusPedido.PAGAMENTO_APROVADO);
        assertThat(captor.getValue().getStatus()).isEqualTo(StatusPedido.CANCELADO);
        assertThat(captor.getValue().getAlteradoPor()).isSameAs(admin);
        assertThat(captor.getValue().getObservacao()).contains("estorno pendente");
    }

    @Test
    void bloqueiaEntregaDeProdutoComReceitaSemConferenciaPresencial() {
        Pedido pedido = pedido(StatusPedido.PRONTO_PARA_RETIRADA, TipoEntrega.RETIRADA);
        ItemPedido item = new ItemPedido();
        item.setExigeReceita(true);
        pedido.getItens().add(item);
        prepararPedido(pedido);

        assertThatThrownBy(() -> service.transicionarAdministrativamente(42L, 4L,
                StatusPedido.ENTREGUE, new Usuario(), null))
                .isInstanceOf(AcaoPedidoInvalidaException.class)
                .hasMessageContaining("conferência presencial");

        assertThat(pedido.getStatus()).isEqualTo(StatusPedido.PRONTO_PARA_RETIRADA);
        verifyNoInteractions(historicoRepository);
    }

    @Test
    void pedidoCanceladoNaoLiberaReservaDuasVezes() {
        Pedido pedido = pedido(StatusPedido.AGUARDANDO_PAGAMENTO, TipoEntrega.ENTREGA);
        ItemPedido item = new ItemPedido();
        Apresentacao apresentacao = new Apresentacao();
        apresentacao.setId(15L);
        item.setApresentacao(apresentacao);
        item.setQuantidade(2);
        pedido.getItens().add(item);
        prepararPedido(pedido);
        when(pagamentoRepository.findByPedidoIdAndStatus(42L, StatusPagamento.PENDENTE))
                .thenReturn(Optional.empty());

        service.transicionarAdministrativamente(42L, 4L, StatusPedido.CANCELADO, new Usuario(), null);
        assertThatThrownBy(() -> service.transicionarAdministrativamente(42L, 4L,
                StatusPedido.CANCELADO, new Usuario(), null))
                .isInstanceOf(AcaoPedidoInvalidaException.class);

        verify(apresentacaoRepository, times(1)).devolverEstoque(15L, 2);
        verify(historicoRepository, times(1)).save(any(HistoricoStatusPedido.class));
    }

    private void prepararPedido(Pedido pedido) {
        when(pedidoRepository.findById(42L)).thenReturn(Optional.of(pedido));
        when(pedidoRepository.travarLinha(42L)).thenReturn(Optional.of(42L));
    }

    private static Pedido pedido(StatusPedido status, TipoEntrega tipo) {
        Pedido pedido = new Pedido();
        pedido.setId(42L);
        pedido.setVersao(4L);
        pedido.setStatus(status);
        pedido.setTipoEntrega(tipo);
        return pedido;
    }
}
