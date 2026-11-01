package com.farmacia.farmaciaonline.service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.farmacia.farmaciaonline.gateway.GatewayDePagamento;
import com.farmacia.farmaciaonline.gateway.dto.RequisicaoPagamento;
import com.farmacia.farmaciaonline.gateway.dto.RespostaPagamento;
import com.farmacia.farmaciaonline.model.CartaoForm;
import com.farmacia.farmaciaonline.model.CheckoutForm;
import com.farmacia.farmaciaonline.model.Pagamento;
import com.farmacia.farmaciaonline.model.Pedido;
import com.farmacia.farmaciaonline.model.enums.MetodoPagamento;
import com.farmacia.farmaciaonline.model.enums.StatusPagamento;
import com.farmacia.farmaciaonline.model.enums.StatusPedido;
import com.farmacia.farmaciaonline.repository.PagamentoRepository;
import com.farmacia.farmaciaonline.repository.PedidoRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PagamentoPedidoService {

    private static final Logger log = LoggerFactory.getLogger(PagamentoPedidoService.class);

    /** O que a tela do pedido precisa mostrar. */
    public record TelaPedido(Pedido pedido, List<Pagamento> pagamentos, Pagamento pendente, boolean podePagar,
            boolean semResposta, UUID chave, List<MetodoPagamento> metodos) {

        private static final DateTimeFormatter DATA_HORA =
                DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm").withZone(ZoneId.of("America/Sao_Paulo"));

        public String getReservaAte() {
            return pedido.getExpiraEm() == null ? null : DATA_HORA.format(pedido.getExpiraEm());
        }

        /** Última tentativa, se foi recusada: é o motivo que a tela mostra. */
        public Pagamento getRecusado() {
            return pagamentos.isEmpty() || pagamentos.get(0).getStatus() != StatusPagamento.RECUSADO
                    ? null : pagamentos.get(0);
        }

        public boolean isPedeCartao() {
            return metodos.contains(MetodoPagamento.CARTAO_CREDITO) || metodos.contains(MetodoPagamento.CARTAO_DEBITO);
        }

        public boolean isAguardandoGateway() {
            return pendente != null && pendente.getTransacaoGatewayId() != null;
        }

        /** O resumo sai quando o pedido é aceito (aprovado, ou Pix/boleto gerado). Ainda sem envio: falhou. */
        public boolean isResumoNaoEnviado() {
            boolean aceito = pedido.getStatus() != StatusPedido.CANCELADO
                    && (pedido.getStatus() != StatusPedido.AGUARDANDO_PAGAMENTO || isAguardandoGateway());
            return aceito && pedido.getResumoEnviadoEm() == null;
        }
    }

    private final CheckoutService checkout;
    private final EstadoPedidoService estado;
    private final ResumoPedidoService resumos;
    private final GatewayDePagamento gateway;
    private final PedidoRepository pedidoRepository;
    private final PagamentoRepository pagamentoRepository;

    /**
     * Registra o pedido e faz a primeira cobrança.
     */
    public Long confirmarCompra(Long usuarioId, CheckoutForm form, String conferencia, UUID chave, CartaoForm cartao) {
        Long pedidoId = checkout.registrar(usuarioId, form, conferencia, chave);
        pagar(usuarioId, pedidoId, chave, form.getMetodo(), form.parcelasEfetivas(), cartao);
        return pedidoId;
    }

    /** Nova tentativa no mesmo pedido, depois de uma recusa ou de uma resposta perdida. */
    public void pagar(Long usuarioId, Long pedidoId, UUID chave, MetodoPagamento metodo, int parcelas,
            CartaoForm cartao) {
        checkout.exigirClienteAtivo(usuarioId);
        estado.abrirTentativa(usuarioId, pedidoId, chave, metodo, parcelas)
                .ifPresent(tentativa -> cobrar(tentativa, cartao));
    }

    private void cobrar(EstadoPedidoService.Tentativa tentativa, CartaoForm cartao) {
        RespostaPagamento retorno;
        try {
            retorno = gateway.processar(requisicao(tentativa, cartao));
        } catch (IllegalArgumentException e) {
            estado.recusar(tentativa.pagamentoId(), e.getMessage());
            return;
        } catch (RuntimeException e) {
            // A tentativa continua pendente e sem transação: a tela do pedido oferece repetir com a mesma chave.
            log.warn("Gateway indisponível: pedido={} erro={}", tentativa.pedidoId(), e.getClass().getSimpleName());
            return;
        }
        estado.aplicarRetorno(tentativa.pagamentoId(), retorno);
        if (retorno.status() == StatusPagamento.APROVADO || retorno.status() == StatusPagamento.PENDENTE) {
            resumos.enviarSePendente(tentativa.pedidoId());
        }
    }

    private static RequisicaoPagamento requisicao(EstadoPedidoService.Tentativa tentativa, CartaoForm cartao) {
        boolean temCartao = cartao != null && tentativa.metodo() != MetodoPagamento.PIX
                && tentativa.metodo() != MetodoPagamento.BOLETO;
        return new RequisicaoPagamento(tentativa.pedidoId(), tentativa.valor(), tentativa.metodo(),
                temCartao ? cartao.getNumeroCartao() : null,
                temCartao ? cartao.getCvv() : null,
                temCartao && cartao.getValidadeCartao() != null ? cartao.getValidadeCartao().trim() : null,
                temCartao ? cartao.getNomeImpresso() : null,
                tentativa.parcelas(), null, tentativa.chave().toString());
    }

    /**
     * Retorno assíncrono: um evento só traz o id da transação; o que vale é o que o gateway responde na consulta.
     * Transação desconhecida é ignorada e evento repetido não muda nada.
     */
    public void processarEvento(String transacaoGatewayId) {
        pagamentoRepository.findByTransacaoGatewayId(transacaoGatewayId).ifPresent(pagamento ->
                estado.aplicarRetorno(pagamento.getId(), gateway.consultar(transacaoGatewayId)));
    }

    /** "Já paguei": consulta o gateway para a tentativa pendente do pedido. */
    public void atualizarSituacao(Long usuarioId, Long pedidoId) {
        transacaoPendente(usuarioId, pedidoId).ifPresent(this::processarEvento);
    }

    /** Transação da tentativa pendente de um pedido da própria pessoa; pedido alheio responde 404. */
    public Optional<String> transacaoPendente(Long usuarioId, Long pedidoId) {
        Pedido pedido = pedidoDoCliente(usuarioId, pedidoId);
        return pagamentoRepository.findByPedidoIdAndStatus(pedido.getId(), StatusPagamento.PENDENTE)
                .map(Pagamento::getTransacaoGatewayId);
    }

    public TelaPedido telaPedido(Long usuarioId, Long pedidoId) {
        Pedido pedido = pedidoDoCliente(usuarioId, pedidoId);
        if (pedido.getStatus() == StatusPedido.AGUARDANDO_PAGAMENTO && pedido.getExpiraEm() != null
                && pedido.getExpiraEm().isBefore(Instant.now())) {
            expirarSeVencido(pedidoId);
            pedido = pedidoDoCliente(usuarioId, pedidoId);
        }

        List<Pagamento> pagamentos = pagamentoRepository.findByPedidoIdOrderByIdDesc(pedidoId);
        Pagamento pendente = pagamentos.stream().filter(p -> p.getStatus() == StatusPagamento.PENDENTE)
                .findFirst().orElse(null);
        boolean semResposta = pendente != null && pendente.getTransacaoGatewayId() == null;
        boolean aguardando = pedido.getStatus() == StatusPedido.AGUARDANDO_PAGAMENTO;
        boolean podePagar = aguardando && (pendente == null || semResposta);

        // Sem resposta do gateway, repete a mesma tentativa (mesma chave e método): se a cobrança chegou a acontecer,
        // o gateway devolve o resultado dela em vez de cobrar de novo.
        UUID chave = semResposta ? pendente.getChaveIdempotencia() : UUID.randomUUID();
        List<MetodoPagamento> metodos = semResposta ? List.of(pendente.getMetodo()) : List.of(MetodoPagamento.values());
        return new TelaPedido(pedido, pagamentos, pendente, podePagar, semResposta, chave, metodos);
    }

    /** Roda periodicamente ({@code ExpiracaoPedidosJob}). Um pedido com problema não impede os demais. */
    public int expirarVencidos() {
        int expirados = 0;
        for (Long pedidoId : pedidoRepository.idsComReservaVencida(Instant.now())) {
            try {
                if (expirarSeVencido(pedidoId)) {
                    expirados++;
                }
            } catch (RuntimeException e) {
                log.warn("Não foi possível expirar o pedido {}: {}", pedidoId, e.getClass().getSimpleName());
            }
        }
        return expirados;
    }

    /**
     * Antes de expirar, pergunta ao gateway: um Pix pago no último minuto não pode virar pedido cancelado. Se o gateway
     * não responder, o pedido fica para a próxima rodada.
     */
    private boolean expirarSeVencido(Long pedidoId) {
        Optional<Pagamento> pendente = pagamentoRepository.findByPedidoIdAndStatus(pedidoId, StatusPagamento.PENDENTE)
                .filter(p -> p.getTransacaoGatewayId() != null);
        if (pendente.isPresent()) {
            try {
                RespostaPagamento retorno = gateway.consultar(pendente.get().getTransacaoGatewayId());
                if (retorno.status() == StatusPagamento.APROVADO) {
                    estado.aplicarRetorno(pendente.get().getId(), retorno);
                    return false;
                }
            } catch (RuntimeException e) {
                log.warn("Gateway indisponível ao expirar o pedido {}: {}", pedidoId, e.getClass().getSimpleName());
                return false;
            }
        }
        return estado.expirar(pedidoId);
    }

    private Pedido pedidoDoCliente(Long usuarioId, Long pedidoId) {
        checkout.exigirClienteAtivo(usuarioId);
        return pedidoRepository.findByIdAndUsuarioId(pedidoId, usuarioId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
}
