package com.farmacia.farmaciaonline.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.farmacia.farmaciaonline.gateway.dto.RespostaPagamento;
import com.farmacia.farmaciaonline.model.Apresentacao;
import com.farmacia.farmaciaonline.model.CheckoutForm;
import com.farmacia.farmaciaonline.model.HistoricoStatusPedido;
import com.farmacia.farmaciaonline.model.Pagamento;
import com.farmacia.farmaciaonline.model.Pedido;
import com.farmacia.farmaciaonline.model.Usuario;
import com.farmacia.farmaciaonline.model.enums.CanalCodigo;
import com.farmacia.farmaciaonline.model.enums.MetodoPagamento;
import com.farmacia.farmaciaonline.model.enums.StatusPagamento;
import com.farmacia.farmaciaonline.model.enums.StatusPedido;

/** Política de pagamento e reserva: aprovação, recusa, evento repetido, expiração, cancelamento e envio do resumo. */
class PagamentoPedidoIntegracaoTest extends CheckoutTesteBase {

    private Pedido pedido(Long id) {
        return pedidoRepository.findById(id).orElseThrow();
    }

    private Pagamento unicoPagamento(Long pedidoId) {
        List<Pagamento> pagamentosDoPedido = pagamentoRepository.findByPedidoId(pedidoId);
        assertThat(pagamentosDoPedido).hasSize(1);
        return pagamentosDoPedido.get(0);
    }

    private void vencerReserva(Long pedidoId) {
        Pedido pedido = pedido(pedidoId);
        pedido.setExpiraEm(Instant.now().minusSeconds(60));
        pedidoRepository.saveAndFlush(pedido);
    }

    /** Última mudança registrada: é a que o teste acabou de provocar. */
    private HistoricoStatusPedido ultimoHistorico(Long pedidoId) {
        List<HistoricoStatusPedido> linhaDoTempo = historicoRepository.findByPedidoIdOrderByCriadoEmAsc(pedidoId);
        return linhaDoTempo.get(linhaDoTempo.size() - 1);
    }

    private long historicosCom(Long pedidoId, StatusPedido status) {
        return historicoRepository.findByPedidoIdOrderByCriadoEmAsc(pedidoId).stream()
                .map(HistoricoStatusPedido::getStatus).filter(status::equals).count();
    }

    private void simularEvento(String transacao, StatusPagamento status) {
        gateway.forcarStatus(transacao, status);
        pagamentos.processarEvento(transacao);
    }

    // ---------- Aprovação ----------

    @Test
    @DisplayName("cartão aprovado: pedido aprovado, reserva consumida, só dados mascarados gravados")
    void cartaoAprovado() {
        Usuario cliente = novoCliente();
        Apresentacao a = novaApresentacao("10.00", 5);
        noCarrinho(cliente, a, 2);

        Long id = comprar(cliente, retirada(MetodoPagamento.CARTAO_CREDITO), cartao(CARTAO_APROVADO));

        assertThat(pedido(id).getStatus()).isEqualTo(StatusPedido.PAGAMENTO_APROVADO);
        assertThat(pedido(id).getExpiraEm()).isNull();
        assertThat(estoqueDe(a)).isEqualTo(3);
        assertThat(vendidosDe(a)).isEqualTo(2);
        assertThat(historicosCom(id, StatusPedido.PAGAMENTO_APROVADO)).isEqualTo(1);
        assertThat(ultimoHistorico(id).getStatusAnterior()).isEqualTo(StatusPedido.AGUARDANDO_PAGAMENTO);

        Pagamento pagamento = unicoPagamento(id);
        assertThat(pagamento.getStatus()).isEqualTo(StatusPagamento.APROVADO);
        assertThat(pagamento.getCartaoUltimos4()).isEqualTo("1111");
        assertThat(pagamento.getCartaoBandeira()).isEqualTo("VISA");
        assertThat(pagamento.getTransacaoGatewayId()).isNotBlank();
    }

    @Test
    @DisplayName("o gateway e o envio do resumo são chamados sem transação aberta, com o pedido já gravado")
    void gatewayEEnvioForaDaTransacao() {
        Usuario cliente = novoCliente();
        noCarrinho(cliente, novaApresentacao("10.00", 5), 1);
        AtomicBoolean gatewayEmTransacao = new AtomicBoolean(true);
        AtomicBoolean envioEmTransacao = new AtomicBoolean(true);
        doAnswer(chamada -> {
            gatewayEmTransacao.set(TransactionSynchronizationManager.isActualTransactionActive());
            return chamada.callRealMethod();
        }).when(gateway).processar(any());
        doAnswer(chamada -> {
            envioEmTransacao.set(TransactionSynchronizationManager.isActualTransactionActive());
            return chamada.callRealMethod();
        }).when(envio).enviar(any(), anyString(), anyString(), anyString());

        comprar(cliente, retirada(MetodoPagamento.CARTAO_CREDITO), cartao(CARTAO_APROVADO));

        assertThat(gatewayEmTransacao).isFalse();
        assertThat(envioEmTransacao).isFalse();
    }

    @Test
    @DisplayName("o banco recusa uma segunda tentativa aprovada no mesmo pedido")
    void bancoImpedeDuplaAprovacao() {
        Usuario cliente = novoCliente();
        noCarrinho(cliente, novaApresentacao("10.00", 5), 1);
        Long id = comprar(cliente, retirada(MetodoPagamento.CARTAO_CREDITO), cartao(CARTAO_APROVADO));

        Pagamento segundo = new Pagamento();
        segundo.setPedido(pedido(id));
        segundo.setMetodo(MetodoPagamento.PIX);
        segundo.setValor(pedido(id).getTotal());
        segundo.setStatus(StatusPagamento.APROVADO);

        assertThatThrownBy(() -> pagamentoRepository.saveAndFlush(segundo))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("pedido já aprovado não aceita nova tentativa: nada é cobrado")
    void pedidoAprovadoNaoCobraDeNovo() {
        Usuario cliente = novoCliente();
        noCarrinho(cliente, novaApresentacao("10.00", 5), 1);
        Long id = comprar(cliente, retirada(MetodoPagamento.CARTAO_CREDITO), cartao(CARTAO_APROVADO));

        pagamentos.pagar(cliente.getId(), id, UUID.randomUUID(), MetodoPagamento.CARTAO_CREDITO, 1, cartao(CARTAO_APROVADO));

        verify(gateway, times(1)).processar(any());
        assertThat(pagamentoRepository.findByPedidoId(id)).hasSize(1);
    }

    @Test
    @DisplayName("aprovação com valor diferente do pedido não aprova")
    void valorDivergente() {
        Usuario cliente = novoCliente();
        noCarrinho(cliente, novaApresentacao("10.00", 5), 1);
        Long id = comprar(cliente, retirada(MetodoPagamento.PIX), null);
        Pagamento pendente = unicoPagamento(id);

        estado.aplicarRetorno(pendente.getId(), new RespostaPagamento(pendente.getTransacaoGatewayId(),
                StatusPagamento.APROVADO, "ok", null, null, null, null, new BigDecimal("0.01")));

        assertThat(pedido(id).getStatus()).isEqualTo(StatusPedido.AGUARDANDO_PAGAMENTO);
        assertThat(unicoPagamento(id).getStatus()).isEqualTo(StatusPagamento.RECUSADO);
    }

    // ---------- Recusa e nova tentativa ----------

    @Test
    @DisplayName("recusa: pedido e reserva continuam, e a nova tentativa fica no mesmo pedido")
    void recusaPermiteNovaTentativa() {
        Usuario cliente = novoCliente();
        Apresentacao a = novaApresentacao("10.00", 5);
        noCarrinho(cliente, a, 2);

        Long id = comprar(cliente, retirada(MetodoPagamento.CARTAO_CREDITO), cartao(CARTAO_RECUSADO));

        assertThat(pedido(id).getStatus()).isEqualTo(StatusPedido.AGUARDANDO_PAGAMENTO);
        assertThat(pedido(id).getExpiraEm()).isAfter(Instant.now());
        assertThat(estoqueDe(a)).isEqualTo(3);
        assertThat(vendidosDe(a)).isZero();
        assertThat(unicoPagamento(id).getStatus()).isEqualTo(StatusPagamento.RECUSADO);
        assertThat(unicoPagamento(id).getMensagem()).contains("recusado");

        PagamentoPedidoService.TelaPedido tela = pagamentos.telaPedido(cliente.getId(), id);
        assertThat(tela.podePagar()).isTrue();
        assertThat(tela.getRecusado()).isNotNull();

        pagamentos.pagar(cliente.getId(), id, tela.chave(), MetodoPagamento.CARTAO_DEBITO, 1, cartao(CARTAO_APROVADO));

        assertThat(pedido(id).getStatus()).isEqualTo(StatusPedido.PAGAMENTO_APROVADO);
        assertThat(pedidoRepository.findByUsuarioIdOrderByCriadoEmDesc(cliente.getId(), Pageable.unpaged()).getTotalElements())
                .isEqualTo(1);
        assertThat(pagamentoRepository.findByPedidoIdOrderByIdDesc(id)).extracting(Pagamento::getStatus)
                .containsExactly(StatusPagamento.APROVADO, StatusPagamento.RECUSADO);
        assertThat(estoqueDe(a)).isEqualTo(3);
        assertThat(vendidosDe(a)).isEqualTo(2);
    }

    @Test
    @DisplayName("nova tentativa repetida com a mesma chave não cria outro pagamento")
    void novaTentativaRepetida() {
        Usuario cliente = novoCliente();
        noCarrinho(cliente, novaApresentacao("10.00", 5), 1);
        Long id = comprar(cliente, retirada(MetodoPagamento.CARTAO_CREDITO), cartao(CARTAO_RECUSADO));
        UUID chave = UUID.randomUUID();

        pagamentos.pagar(cliente.getId(), id, chave, MetodoPagamento.CARTAO_CREDITO, 1, cartao(CARTAO_RECUSADO));
        pagamentos.pagar(cliente.getId(), id, chave, MetodoPagamento.CARTAO_CREDITO, 1, cartao(CARTAO_RECUSADO));

        assertThat(pagamentoRepository.findByPedidoId(id)).hasSize(2);
    }

    @Test
    @DisplayName("cartão inválido para o gateway vira recusa com o motivo, sem número nenhum na mensagem")
    void cartaoInvalidoViraRecusa() {
        Usuario cliente = novoCliente();
        Apresentacao a = novaApresentacao("10.00", 5);
        noCarrinho(cliente, a, 1);

        // Passa no formato do formulário, mas não no dígito verificador.
        Long id = comprar(cliente, retirada(MetodoPagamento.CARTAO_CREDITO), cartao("4111111111111112"));

        assertThat(unicoPagamento(id).getStatus()).isEqualTo(StatusPagamento.RECUSADO);
        assertThat(unicoPagamento(id).getMensagem()).contains("inválido").doesNotContain("4111");
        assertThat(estoqueDe(a)).isEqualTo(4);
    }

    @Test
    @DisplayName("gateway fora do ar: o pedido fica registrado e repetir com a mesma chave completa sem duplicar")
    void gatewayForaDoAr() {
        Usuario cliente = novoCliente();
        Apresentacao a = novaApresentacao("10.00", 5);
        noCarrinho(cliente, a, 1);
        doThrow(new IllegalStateException("fora do ar")).when(gateway).processar(any());

        Long id = comprar(cliente, retirada(MetodoPagamento.PIX), null);

        assertThat(pedido(id).getStatus()).isEqualTo(StatusPedido.AGUARDANDO_PAGAMENTO);
        assertThat(estoqueDe(a)).isEqualTo(4);
        assertThat(itensNoCarrinho(cliente)).isZero();
        PagamentoPedidoService.TelaPedido tela = pagamentos.telaPedido(cliente.getId(), id);
        assertThat(tela.semResposta()).isTrue();
        assertThat(tela.podePagar()).isTrue();
        assertThat(tela.metodos()).containsExactly(MetodoPagamento.PIX);

        doCallRealMethod().when(gateway).processar(any());
        pagamentos.pagar(cliente.getId(), id, tela.chave(), MetodoPagamento.PIX, 1, null);

        Pagamento pagamento = unicoPagamento(id);
        assertThat(pagamento.getStatus()).isEqualTo(StatusPagamento.PENDENTE);
        assertThat(pagamento.getCodigoPagamento()).startsWith("DEMO-PIX");
    }

    // ---------- Pix/boleto e eventos ----------

    @Test
    @DisplayName("Pix: fica pendente com código e prazo do gateway, segurando o estoque")
    void pixPendente() {
        Usuario cliente = novoCliente();
        Apresentacao a = novaApresentacao("10.00", 5);
        noCarrinho(cliente, a, 1);

        Long id = comprar(cliente, retirada(MetodoPagamento.PIX), null);

        Pagamento pagamento = unicoPagamento(id);
        assertThat(pagamento.getStatus()).isEqualTo(StatusPagamento.PENDENTE);
        assertThat(pagamento.getCodigoPagamento()).startsWith("DEMO-PIX");
        assertThat(pedido(id).getExpiraEm()).isEqualTo(pagamento.getExpiraEm());
        assertThat(estoqueDe(a)).isEqualTo(4);
        assertThat(vendidosDe(a)).isZero();
    }

    @Test
    @DisplayName("evento repetido: a aprovação conta uma vez, e evento atrasado de expiração não desfaz")
    void eventoRepetido() {
        Usuario cliente = novoCliente();
        Apresentacao a = novaApresentacao("10.00", 5);
        noCarrinho(cliente, a, 2);
        Long id = comprar(cliente, retirada(MetodoPagamento.PIX), null);
        String transacao = unicoPagamento(id).getTransacaoGatewayId();

        simularEvento(transacao, StatusPagamento.APROVADO);
        simularEvento(transacao, StatusPagamento.APROVADO);
        simularEvento(transacao, StatusPagamento.EXPIRADO);

        assertThat(pedido(id).getStatus()).isEqualTo(StatusPedido.PAGAMENTO_APROVADO);
        assertThat(unicoPagamento(id).getStatus()).isEqualTo(StatusPagamento.APROVADO);
        assertThat(estoqueDe(a)).isEqualTo(3);
        assertThat(vendidosDe(a)).isEqualTo(2);
        assertThat(historicosCom(id, StatusPedido.PAGAMENTO_APROVADO)).isEqualTo(1);
    }

    @Test
    @DisplayName("eventos de aprovação simultâneos contam uma vez")
    void eventosSimultaneos() throws Exception {
        Usuario cliente = novoCliente();
        Apresentacao a = novaApresentacao("10.00", 5);
        noCarrinho(cliente, a, 1);
        Long id = comprar(cliente, retirada(MetodoPagamento.PIX), null);
        String transacao = unicoPagamento(id).getTransacaoGatewayId();
        gateway.forcarStatus(transacao, StatusPagamento.APROVADO);

        ExecutorService threads = Executors.newFixedThreadPool(4);
        CountDownLatch largada = new CountDownLatch(1);
        List<Future<?>> eventos = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            eventos.add(threads.submit(() -> {
                largada.await();
                pagamentos.processarEvento(transacao);
                return null;
            }));
        }
        largada.countDown();
        for (Future<?> evento : eventos) {
            evento.get();
        }
        threads.shutdown();

        assertThat(vendidosDe(a)).isEqualTo(1);
        assertThat(historicosCom(id, StatusPedido.PAGAMENTO_APROVADO)).isEqualTo(1);
    }

    @Test
    @DisplayName("evento de transação desconhecida é ignorado")
    void eventoDesconhecido() {
        pagamentos.processarEvento("NAO-EXISTE");
    }

    @Test
    @DisplayName("evento de expiração do gateway cancela o pedido e devolve o estoque uma vez")
    void eventoDeExpiracao() {
        Usuario cliente = novoCliente();
        Apresentacao a = novaApresentacao("10.00", 5);
        noCarrinho(cliente, a, 2);
        Long id = comprar(cliente, retirada(MetodoPagamento.BOLETO), null);
        String transacao = unicoPagamento(id).getTransacaoGatewayId();

        simularEvento(transacao, StatusPagamento.EXPIRADO);
        simularEvento(transacao, StatusPagamento.EXPIRADO);

        assertThat(pedido(id).getStatus()).isEqualTo(StatusPedido.CANCELADO);
        assertThat(unicoPagamento(id).getStatus()).isEqualTo(StatusPagamento.EXPIRADO);
        assertThat(estoqueDe(a)).isEqualTo(5);
        assertThat(historicosCom(id, StatusPedido.CANCELADO)).isEqualTo(1);
        assertThat(ultimoHistorico(id).getStatusAnterior()).isEqualTo(StatusPedido.AGUARDANDO_PAGAMENTO);
    }

    // ---------- Expiração e cancelamento ----------

    @Test
    @DisplayName("reserva vencida: expira, libera o estoque uma vez, e rodar de novo não libera mais")
    void expiracaoLiberaUmaVez() {
        Usuario cliente = novoCliente();
        Apresentacao a = novaApresentacao("10.00", 5);
        noCarrinho(cliente, a, 2);
        Long id = comprar(cliente, retirada(MetodoPagamento.PIX), null);
        vencerReserva(id);

        assertThat(pagamentos.expirarVencidos()).isGreaterThanOrEqualTo(1);
        pagamentos.expirarVencidos();

        assertThat(pedido(id).getStatus()).isEqualTo(StatusPedido.CANCELADO);
        assertThat(pedido(id).getExpiraEm()).isNull();
        assertThat(unicoPagamento(id).getStatus()).isEqualTo(StatusPagamento.EXPIRADO);
        assertThat(estoqueDe(a)).isEqualTo(5);
        assertThat(historicosCom(id, StatusPedido.CANCELADO)).isEqualTo(1);
        assertThat(ultimoHistorico(id).getStatusAnterior()).isEqualTo(StatusPedido.AGUARDANDO_PAGAMENTO);
    }

    @Test
    @DisplayName("reserva dentro do prazo não expira")
    void reservaNoPrazo() {
        Usuario cliente = novoCliente();
        Apresentacao a = novaApresentacao("10.00", 5);
        noCarrinho(cliente, a, 1);
        Long id = comprar(cliente, retirada(MetodoPagamento.PIX), null);

        pagamentos.expirarVencidos();

        assertThat(estado.expirar(id)).isFalse();
        assertThat(pedido(id).getStatus()).isEqualTo(StatusPedido.AGUARDANDO_PAGAMENTO);
        assertThat(estoqueDe(a)).isEqualTo(4);
    }

    @Test
    @DisplayName("Pix pago no último minuto vira aprovação, não expiração")
    void pagoAntesDeExpirar() {
        Usuario cliente = novoCliente();
        Apresentacao a = novaApresentacao("10.00", 5);
        noCarrinho(cliente, a, 1);
        Long id = comprar(cliente, retirada(MetodoPagamento.PIX), null);
        gateway.forcarStatus(unicoPagamento(id).getTransacaoGatewayId(), StatusPagamento.APROVADO);
        vencerReserva(id);

        pagamentos.expirarVencidos();

        assertThat(pedido(id).getStatus()).isEqualTo(StatusPedido.PAGAMENTO_APROVADO);
        assertThat(estoqueDe(a)).isEqualTo(4);
    }

    @Test
    @DisplayName("cartão recusado e abandonado também libera o estoque ao vencer a reserva")
    void recusadoAbandonadoExpira() {
        Usuario cliente = novoCliente();
        Apresentacao a = novaApresentacao("10.00", 5);
        noCarrinho(cliente, a, 1);
        Long id = comprar(cliente, retirada(MetodoPagamento.CARTAO_CREDITO), cartao(CARTAO_RECUSADO));
        vencerReserva(id);

        pagamentos.expirarVencidos();

        assertThat(pedido(id).getStatus()).isEqualTo(StatusPedido.CANCELADO);
        assertThat(estoqueDe(a)).isEqualTo(5);
    }

    @Test
    @DisplayName("abrir a tela de um pedido vencido já mostra o pedido expirado, sem esperar o job")
    void telaExpiraPedidoVencido() {
        Usuario cliente = novoCliente();
        Apresentacao a = novaApresentacao("10.00", 5);
        noCarrinho(cliente, a, 1);
        Long id = comprar(cliente, retirada(MetodoPagamento.PIX), null);
        vencerReserva(id);

        PagamentoPedidoService.TelaPedido tela = pagamentos.telaPedido(cliente.getId(), id);

        assertThat(tela.pedido().getStatus()).isEqualTo(StatusPedido.CANCELADO);
        assertThat(tela.podePagar()).isFalse();
        assertThat(estoqueDe(a)).isEqualTo(5);
    }

    @Test
    @DisplayName("pedido com reserva vencida não aceita nova tentativa")
    void reservaVencidaNaoPaga() {
        Usuario cliente = novoCliente();
        noCarrinho(cliente, novaApresentacao("10.00", 5), 1);
        Long id = comprar(cliente, retirada(MetodoPagamento.CARTAO_CREDITO), cartao(CARTAO_RECUSADO));
        vencerReserva(id);

        pagamentos.pagar(cliente.getId(), id, UUID.randomUUID(), MetodoPagamento.CARTAO_CREDITO, 1, cartao(CARTAO_APROVADO));

        assertThat(pagamentoRepository.findByPedidoId(id)).hasSize(1);
        assertThat(pedido(id).getStatus()).isEqualTo(StatusPedido.AGUARDANDO_PAGAMENTO);
    }

    @Test
    @DisplayName("cancelar devolve o estoque uma vez; repetido não devolve de novo")
    void cancelarLiberaUmaVez() {
        Usuario cliente = novoCliente();
        Apresentacao a = novaApresentacao("10.00", 5);
        noCarrinho(cliente, a, 2);
        Long id = comprar(cliente, retirada(MetodoPagamento.PIX), null);

        assertThat(estado.cancelar(id, null, "Cancelado")).isTrue();
        assertThat(estado.cancelar(id, null, "Cancelado")).isFalse();

        assertThat(pedido(id).getStatus()).isEqualTo(StatusPedido.CANCELADO);
        assertThat(estoqueDe(a)).isEqualTo(5);
        assertThat(historicosCom(id, StatusPedido.CANCELADO)).isEqualTo(1);
    }

    @Test
    @DisplayName("evento de aprovação depois do cancelamento não reativa o pedido")
    void aprovacaoDepoisDoCancelamento() {
        Usuario cliente = novoCliente();
        Apresentacao a = novaApresentacao("10.00", 5);
        noCarrinho(cliente, a, 1);
        Long id = comprar(cliente, retirada(MetodoPagamento.PIX), null);
        String transacao = unicoPagamento(id).getTransacaoGatewayId();
        estado.cancelar(id, null, "Cancelado");

        simularEvento(transacao, StatusPagamento.APROVADO);

        assertThat(pedido(id).getStatus()).isEqualTo(StatusPedido.CANCELADO);
        assertThat(estoqueDe(a)).isEqualTo(5);
        assertThat(vendidosDe(a)).isZero();
    }

    @Test
    @DisplayName("cancelar pedido pago devolve o estoque e tira da contagem de vendidos")
    void cancelarPedidoPago() {
        Usuario cliente = novoCliente();
        Apresentacao a = novaApresentacao("10.00", 5);
        noCarrinho(cliente, a, 2);
        Long id = comprar(cliente, retirada(MetodoPagamento.CARTAO_CREDITO), cartao(CARTAO_APROVADO));

        assertThat(estado.cancelar(id, usuarioRepository.findById(1L).orElseThrow(), "Pedido do cliente")).isTrue();

        assertThat(estoqueDe(a)).isEqualTo(5);
        assertThat(vendidosDe(a)).isZero();
        assertThat(ultimoHistorico(id).getStatusAnterior()).isEqualTo(StatusPedido.PAGAMENTO_APROVADO);
    }

    // ---------- Resumo por e-mail ----------

    @Test
    @DisplayName("o resumo vai ao e-mail do cliente uma vez e com os valores certos; recusa não envia")
    void resumoEnviado() {
        Usuario cliente = novoCliente();
        noCarrinho(cliente, novaApresentacao("10.00", 5), 2);

        Long id = comprar(cliente, retirada(MetodoPagamento.CARTAO_CREDITO), cartao(CARTAO_RECUSADO));
        assertThat(pedido(id).getResumoEnviadoEm()).isNull();
        verify(envio, times(0)).enviar(any(), anyString(), anyString(), anyString());

        pagamentos.pagar(cliente.getId(), id, UUID.randomUUID(), MetodoPagamento.CARTAO_CREDITO, 1, cartao(CARTAO_APROVADO));

        verify(envio, times(1)).enviar(any(), anyString(), anyString(), anyString());
        assertThat(pedido(id).getResumoEnviadoEm()).isNotNull();
        var mensagem = envio.mensagens().get(0);
        assertThat(mensagem.canal()).isEqualTo(CanalCodigo.EMAIL);
        assertThat(mensagem.destino()).isEqualTo(cliente.getEmail());
        assertThat(mensagem.assunto()).contains("nº " + id);
        assertThat(mensagem.texto()).contains("Total: R$").contains("20,00").contains("Farma++ Centro");
    }

    @Test
    @DisplayName("falha no envio não desfaz o pedido; reenviar funciona e não duplica nada")
    void falhaNoEnvio() {
        Usuario cliente = novoCliente();
        Apresentacao a = novaApresentacao("10.00", 5);
        noCarrinho(cliente, a, 1);
        doThrow(new IllegalStateException("caixa fora do ar")).when(envio).enviar(any(), anyString(), anyString(), anyString());

        Long id = comprar(cliente, retirada(MetodoPagamento.CARTAO_CREDITO), cartao(CARTAO_APROVADO));

        assertThat(pedido(id).getStatus()).isEqualTo(StatusPedido.PAGAMENTO_APROVADO);
        assertThat(pedido(id).getResumoEnviadoEm()).isNull();
        assertThat(pagamentos.telaPedido(cliente.getId(), id).isResumoNaoEnviado()).isTrue();
        assertThat(resumos.reenviar(cliente.getId(), id)).isFalse();

        doCallRealMethod().when(envio).enviar(any(), anyString(), anyString(), anyString());
        assertThat(resumos.reenviar(cliente.getId(), id)).isTrue();

        assertThat(pedido(id).getResumoEnviadoEm()).isNotNull();
        assertThat(pagamentos.telaPedido(cliente.getId(), id).isResumoNaoEnviado()).isFalse();
        assertThat(estoqueDe(a)).isEqualTo(4);
        assertThat(pagamentoRepository.findByPedidoId(id)).hasSize(1);

        // Já enviado: pedir de novo não manda outra mensagem.
        long enviadas = envio.mensagens().stream().filter(m -> m.assunto().endsWith("nº " + id)).count();
        resumos.reenviar(cliente.getId(), id);
        assertThat(envio.mensagens().stream().filter(m -> m.assunto().endsWith("nº " + id)).count()).isEqualTo(enviadas);
    }

    @Test
    @DisplayName("formulário sem método não cria pedido")
    void semMetodo() {
        Usuario cliente = novoCliente();
        noCarrinho(cliente, novaApresentacao("10.00", 5), 1);
        CheckoutForm form = retirada(null);

        assertThatThrownBy(() -> checkout.registrar(cliente.getId(), form, "x", UUID.randomUUID()))
                .isInstanceOf(CheckoutException.class)
                .hasMessageContaining("forma de pagamento");
    }
}
