package com.farmacia.farmaciaonline.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import com.farmacia.farmaciaonline.gateway.dto.RespostaPagamento;
import com.farmacia.farmaciaonline.model.HistoricoStatusPedido;
import com.farmacia.farmaciaonline.model.ItemPedido;
import com.farmacia.farmaciaonline.model.Pagamento;
import com.farmacia.farmaciaonline.model.Pedido;
import com.farmacia.farmaciaonline.model.Usuario;
import com.farmacia.farmaciaonline.model.enums.MetodoPagamento;
import com.farmacia.farmaciaonline.model.enums.StatusPagamento;
import com.farmacia.farmaciaonline.model.enums.StatusPedido;
import com.farmacia.farmaciaonline.model.enums.TipoEntrega;
import com.farmacia.farmaciaonline.repository.ApresentacaoRepository;
import com.farmacia.farmaciaonline.repository.HistoricoStatusPedidoRepository;
import com.farmacia.farmaciaonline.repository.PagamentoRepository;
import com.farmacia.farmaciaonline.repository.PedidoRepository;
import com.farmacia.farmaciaonline.repository.ProdutoRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class EstadoPedidoService {

    private static final Logger log = LoggerFactory.getLogger(EstadoPedidoService.class);

    public record Tentativa(Long pagamentoId, Long pedidoId, BigDecimal valor, MetodoPagamento metodo, int parcelas,
            UUID chave) {}

    private final PedidoRepository pedidoRepository;
    private final PagamentoRepository pagamentoRepository;
    private final HistoricoStatusPedidoRepository historicoRepository;
    private final ApresentacaoRepository apresentacaoRepository;
    private final ProdutoRepository produtoRepository;

    @PersistenceContext
    private EntityManager em;

    public Optional<Tentativa> abrirTentativa(Long usuarioId, Long pedidoId, UUID chave, MetodoPagamento metodo,
            int parcelas) {
        pedidoRepository.findByIdAndUsuarioId(pedidoId, usuarioId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        Pedido pedido = travar(pedidoId);

        Optional<Pagamento> mesmaChave = pagamentoRepository.findByPedidoIdAndChaveIdempotencia(pedidoId, chave);
        if (mesmaChave.isPresent()) {
            Pagamento pagamento = mesmaChave.get();
            em.refresh(pagamento);
            boolean semResposta = pagamento.getStatus() == StatusPagamento.PENDENTE
                    && pagamento.getTransacaoGatewayId() == null;
            return semResposta ? Optional.of(tentativa(pagamento)) : Optional.empty();
        }
        if (pedido.getStatus() != StatusPedido.AGUARDANDO_PAGAMENTO || reservaVencida(pedido) || metodo == null
                || pagamentoRepository.findByPedidoIdAndStatus(pedidoId, StatusPagamento.PENDENTE).isPresent()) {
            return Optional.empty();
        }

        Pagamento pagamento = new Pagamento();
        pagamento.setPedido(pedido);
        pagamento.setMetodo(metodo);
        pagamento.setParcelas(parcelas);
        pagamento.setValor(pedido.getTotal());
        pagamento.setChaveIdempotencia(chave);
        return Optional.of(tentativa(pagamentoRepository.saveAndFlush(pagamento)));
    }

   public void aplicarRetorno(Long pagamentoId, RespostaPagamento retorno) {
        Pagamento pagamento = pagamentoRepository.findById(pagamentoId)
                .orElseThrow(() -> new IllegalArgumentException("Pagamento não encontrado: " + pagamentoId));
        Pedido pedido = travar(pagamento.getPedido().getId());
        em.refresh(pagamento);
        if (pagamento.getStatus() != StatusPagamento.PENDENTE) {
            log.info("Retorno repetido ou atrasado ignorado: pagamento={} status={} retorno={}",
                    pagamentoId, pagamento.getStatus(), retorno.status());
            return;
        }

        pagamento.setTransacaoGatewayId(retorno.transacaoGatewayId());
        pagamento.setCartaoBandeira(retorno.bandeira());
        pagamento.setCartaoUltimos4(retorno.ultimos4());
        pagamento.setCodigoPagamento(retorno.codigoPagamento());
        pagamento.setExpiraEm(retorno.expiraEm());
        pagamento.setMensagem(StringUtils.truncate(retorno.mensagem(), 255));

        switch (retorno.status()) {
            case PENDENTE -> {
                if (retorno.expiraEm() != null) {
                    pedido.setExpiraEm(retorno.expiraEm());
                }
            }
            case APROVADO -> aprovar(pedido, pagamento, retorno);
            case RECUSADO -> pagamento.setStatus(StatusPagamento.RECUSADO);
            case EXPIRADO -> {
                pagamento.setStatus(StatusPagamento.EXPIRADO);
                encerrar(pedido, null, "Pagamento expirado; estoque liberado.");
            }
        }
    }

    /** O gateway recusou os dados antes de criar uma transação */
    public void recusar(Long pagamentoId, String motivo) {
        Pagamento pagamento = pagamentoRepository.findById(pagamentoId)
                .orElseThrow(() -> new IllegalArgumentException("Pagamento não encontrado: " + pagamentoId));
        travar(pagamento.getPedido().getId());
        em.refresh(pagamento);
        if (pagamento.getStatus() == StatusPagamento.PENDENTE) {
            pagamento.setStatus(StatusPagamento.RECUSADO);
            pagamento.setMensagem(StringUtils.truncate(motivo, 255));
        }
    }

    /** Encerra o pedido cuja reserva venceu e devolve o estoque. @return se expirou agora */
    public boolean expirar(Long pedidoId) {
        Pedido pedido = travar(pedidoId);
        if (pedido.getStatus() != StatusPedido.AGUARDANDO_PAGAMENTO || !reservaVencida(pedido)) {
            return false;
        }
        encerrar(pedido, null, "Reserva expirada sem pagamento; estoque liberado.");
        return true;
    }

    /**
     * Cancela o pedido e devolve o estoque uma vez. É o contrato para a administração de pedidos (F-17), que decide
     * quem pode cancelar e registra a necessidade de estorno de pedido já pago.
     */
    public boolean cancelar(Long pedidoId, Usuario autor, String observacao) {
        Pedido pedido = travar(pedidoId);
        if (!cancelavel(pedido.getStatus())) {
            return false;
        }
        cancelarTrancado(pedido, autor, observacao);
        return true;
    }

    /** Aplica uma transição solicitada pelo painel admin com trava, versão e histórico na mesma transação. */
    public void transicionarAdministrativamente(Long pedidoId, Long versaoEsperada, StatusPedido destino,
            Usuario autor, String observacao) {
        Pedido pedido = travarComVersao(pedidoId, versaoEsperada);
        StatusPedido origem = pedido.getStatus();
        if (!transicaoPermitida(pedido, destino)) {
            throw new AcaoPedidoInvalidaException("Essa mudança de status não é permitida para o pedido atual.");
        }
        if (destino == StatusPedido.ENTREGUE && pedido.temItemQueExigeReceita()
                && pedido.getReceitaConferidaEm() == null) {
            throw new AcaoPedidoInvalidaException(
                    "Registre a conferência presencial da receita antes de concluir a entrega.");
        }
        if (destino == StatusPedido.CANCELADO) {
            cancelarTrancado(pedido, autor, observacao);
            return;
        }
        pedido.setStatus(destino);
        registrarHistorico(pedido, origem, autor, normalizarObservacao(observacao));
    }

    /** Registra, uma única vez, quem conferiu presencialmente a receita antes da retirada. */
    public void registrarConferenciaReceita(Long pedidoId, Long versaoEsperada, Usuario autor) {
        Pedido pedido = travarComVersao(pedidoId, versaoEsperada);
        if (autor == null) {
            throw new AcaoPedidoInvalidaException("Não foi possível identificar o administrador.");
        }
        if (!pedido.temItemQueExigeReceita() || pedido.getTipoEntrega() != TipoEntrega.RETIRADA
                || pedido.getStatus() != StatusPedido.PRONTO_PARA_RETIRADA) {
            throw new AcaoPedidoInvalidaException(
                    "A conferência só pode ser registrada presencialmente para um pedido com receita pronto para retirada.");
        }
        if (pedido.getReceitaConferidaEm() != null) {
            throw new AcaoPedidoInvalidaException("A conferência presencial já foi registrada para este pedido.");
        }
        pedido.setReceitaConferidaEm(Instant.now());
        pedido.setReceitaConferidaPor(autor);
    }

    private void cancelarTrancado(Pedido pedido, Usuario autor, String observacao) {
        boolean pago = pedido.getStatus() == StatusPedido.PAGAMENTO_APROVADO
                || pedido.getStatus() == StatusPedido.EM_SEPARACAO;
        String nota = normalizarObservacao(observacao);
        if (pago) {
            pedido.setEstornoPendente(true);
            String pendencia = "Pedido pago cancelado; estorno pendente de processamento pelo gateway (não confirmado).";
            nota = StringUtils.hasText(nota) ? StringUtils.truncate(nota + " " + pendencia, 255) : pendencia;
            pedido.getItens().forEach(i -> produtoRepository.somarVendidos(
                    i.getApresentacao().getProduto().getId(), -i.getQuantidade()));
        }
        encerrar(pedido, autor, nota);
    }

    private static boolean cancelavel(StatusPedido status) {
        return status == StatusPedido.AGUARDANDO_PAGAMENTO || status == StatusPedido.PAGAMENTO_APROVADO
                || status == StatusPedido.EM_SEPARACAO;
    }

    private static boolean transicaoPermitida(Pedido pedido, StatusPedido destino) {
        if (destino == null) {
            return false;
        }
        return switch (pedido.getStatus()) {
            case AGUARDANDO_PAGAMENTO -> destino == StatusPedido.CANCELADO;
            case PAGAMENTO_APROVADO -> destino == StatusPedido.EM_SEPARACAO || destino == StatusPedido.CANCELADO;
            case EM_SEPARACAO -> destino == StatusPedido.CANCELADO
                    || (pedido.getTipoEntrega() == TipoEntrega.ENTREGA && destino == StatusPedido.ENVIADO)
                    || (pedido.getTipoEntrega() == TipoEntrega.RETIRADA
                            && destino == StatusPedido.PRONTO_PARA_RETIRADA);
            case ENVIADO, PRONTO_PARA_RETIRADA -> destino == StatusPedido.ENTREGUE;
            case ENTREGUE, CANCELADO -> false;
        };
    }

    private Pedido travarComVersao(Long pedidoId, Long versaoEsperada) {
        pedidoRepository.travarLinha(pedidoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        Pedido pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        em.refresh(pedido);
        if (versaoEsperada == null || !Objects.equals(pedido.getVersao(), versaoEsperada)) {
            throw new ConflitoPedidoException("O pedido foi atualizado por outra pessoa. Recarregue a página.");
        }
        return pedido;
    }

    private static String normalizarObservacao(String observacao) {
        return StringUtils.hasText(observacao) ? StringUtils.truncate(observacao.trim(), 255) : null;
    }

    private void aprovar(Pedido pedido, Pagamento pagamento, RespostaPagamento retorno) {
        if (retorno.valorConfirmado() == null || retorno.valorConfirmado().compareTo(pagamento.getValor()) != 0) {
            log.warn("Aprovação com valor diferente recusada: pagamento={}", pagamento.getId());
            pagamento.setStatus(StatusPagamento.RECUSADO);
            pagamento.setMensagem("O valor confirmado não confere com o valor do pedido.");
            return;
        }
        StatusPedido anterior = pedido.getStatus();
        pagamento.setStatus(StatusPagamento.APROVADO);
        pedido.setStatus(StatusPedido.PAGAMENTO_APROVADO);
        pedido.setExpiraEm(null);
        pedido.getItens().forEach(i -> produtoRepository.somarVendidos(
                i.getApresentacao().getProduto().getId(), i.getQuantidade()));
        registrarHistorico(pedido, anterior, null, "Pagamento aprovado; reserva de estoque consumida.");
    }

    private void encerrar(Pedido pedido, Usuario autor, String observacao) {
        pagamentoRepository.findByPedidoIdAndStatus(pedido.getId(), StatusPagamento.PENDENTE)
                .ifPresent(p -> p.setStatus(StatusPagamento.EXPIRADO));
        pedido.getItens().stream()
                .sorted(Comparator.comparing((ItemPedido i) -> i.getApresentacao().getId()))
                .forEach(i -> apresentacaoRepository.devolverEstoque(i.getApresentacao().getId(), i.getQuantidade()));
        StatusPedido anterior = pedido.getStatus();
        pedido.setStatus(StatusPedido.CANCELADO);
        pedido.setExpiraEm(null);
        registrarHistorico(pedido, anterior, autor, observacao);
    }

    /**
     * Grava a mudança de status. {@code anterior} é lido por quem muda o status, antes de mudá-lo: depois do
     * {@code setStatus} o pedido já está no estado novo e a origem se perderia.
     */
    private void registrarHistorico(Pedido pedido, StatusPedido anterior, Usuario autor, String observacao) {
        HistoricoStatusPedido historico = new HistoricoStatusPedido();
        historico.setPedido(pedido);
        historico.setStatusAnterior(anterior);
        historico.setStatus(pedido.getStatus());
        historico.setAlteradoPor(autor);
        historico.setObservacao(observacao);
        historicoRepository.save(historico);
    }

    /**
     * Trava a linha e só depois lê o pedido. 
     */
    private Pedido travar(Long pedidoId) {
        pedidoRepository.travarLinha(pedidoId);
        Pedido pedido = pedidoRepository.findById(pedidoId).orElseThrow();
        em.refresh(pedido);
        return pedido;
    }

    private static boolean reservaVencida(Pedido pedido) {
        return pedido.getExpiraEm() != null && pedido.getExpiraEm().isBefore(Instant.now());
    }

    private static Tentativa tentativa(Pagamento pagamento) {
        return new Tentativa(pagamento.getId(), pagamento.getPedido().getId(), pagamento.getValor(),
                pagamento.getMetodo(), pagamento.getParcelas(), pagamento.getChaveIdempotencia());
    }
}
