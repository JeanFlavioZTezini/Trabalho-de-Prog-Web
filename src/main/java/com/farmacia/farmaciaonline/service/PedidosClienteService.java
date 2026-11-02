package com.farmacia.farmaciaonline.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.farmacia.farmaciaonline.model.EnderecoEntrega;
import com.farmacia.farmaciaonline.model.HistoricoStatusPedido;
import com.farmacia.farmaciaonline.model.ItemPedido;
import com.farmacia.farmaciaonline.model.LocalRetirada;
import com.farmacia.farmaciaonline.model.Pagamento;
import com.farmacia.farmaciaonline.model.Pedido;
import com.farmacia.farmaciaonline.model.enums.StatusPagamento;
import com.farmacia.farmaciaonline.model.enums.StatusPedido;
import com.farmacia.farmaciaonline.model.enums.TipoEntrega;
import com.farmacia.farmaciaonline.repository.HistoricoStatusPedidoRepository;
import com.farmacia.farmaciaonline.repository.ItemPedidoRepository;
import com.farmacia.farmaciaonline.repository.PagamentoRepository;
import com.farmacia.farmaciaonline.repository.PedidoRepository;

import lombok.RequiredArgsConstructor;
import lombok.Value;

/**
 * "Meus pedidos" (UC06): a consulta do cliente aos próprios pedidos. Só lê; nada aqui altera pedido, pagamento ou
 * estoque, e o status só muda pelo {@link EstadoPedidoService}.
 *
 * <p>Toda consulta recebe o id de quem está logado e filtra por ele (o pedido é buscado por {@code id E dono}): pedido
 * de outra pessoa responde igual a pedido inexistente (404), sem revelar que existe. Quem chama passa o id da
 * <em>sessão</em>, nunca um id vindo da URL ou do formulário. O administrador não usa esta classe: o histórico de um
 * cliente na tela do administrador (F-09) é outra consulta, atrás de {@code /admin/**}.
 *
 * <p>As telas recebem cópias simples (sem entidades): a consulta termina antes da renderização e nada depende de
 * carregamento tardio.
 *
 * <p>O que o cliente <strong>não</strong> vê na linha do tempo: o autor da mudança e a observação interna. O autor é um
 * administrador (dado pessoal de outra pessoa) e a observação pode ser anotação interna da F-17.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PedidosClienteService {

    public static final int TAMANHO_PAGINA = 10;

    private static final DateTimeFormatter DATA_HORA =
            DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm").withZone(ZoneId.of("America/Sao_Paulo"));

    private final CheckoutService checkout;
    private final PedidoRepository pedidoRepository;
    private final ItemPedidoRepository itemPedidoRepository;
    private final PagamentoRepository pagamentoRepository;
    private final HistoricoStatusPedidoRepository historicoRepository;

    /** Página de pedidos do cliente, do mais recente para o mais antigo. Página fora do intervalo cai na última. */
    public PaginaDePedidos listar(Long usuarioId, int pagina) {
        return listar(usuarioId, FiltroMeusPedidos.VAZIO, pagina);
    }

    /** F-23: status e período de criação (opcionais); o dono continua vindo só da sessão. */
    public PaginaDePedidos listar(Long usuarioId, FiltroMeusPedidos filtro, int pagina) {
        checkout.exigirClienteAtivo(usuarioId);
        List<StatusPedido> statuses = filtro.status() == null ? List.of(StatusPedido.values()) : List.of(filtro.status());
        PeriodoFiltro periodo = filtro.periodo() == null ? PeriodoFiltro.VAZIO : filtro.periodo();
        Page<Pedido> resultado = pedidoRepository.buscarDoCliente(usuarioId, statuses, periodo.inicio(), periodo.fim(),
                PageRequest.of(Math.max(pagina, 0), TAMANHO_PAGINA));
        if (resultado.isEmpty() && resultado.getTotalPages() > 0) {
            resultado = pedidoRepository.buscarDoCliente(usuarioId, statuses, periodo.inicio(), periodo.fim(),
                    PageRequest.of(resultado.getTotalPages() - 1, TAMANHO_PAGINA));
        }

        Map<Long, Long> unidades = new HashMap<>();
        if (!resultado.isEmpty()) {
            itemPedidoRepository.somarUnidadesPorPedido(resultado.getContent().stream().map(Pedido::getId).toList())
                    .forEach(u -> unidades.put(u.getPedidoId(), u.getUnidades()));
        }
        List<ResumoPedido> resumos = resultado.getContent().stream()
                .map(p -> new ResumoPedido(p.getId(), p.getCriadoEm(), p.getTotal(),
                        unidades.getOrDefault(p.getId(), 0L), p.getStatus(), p.getTipoEntrega()))
                .toList();
        return new PaginaDePedidos(resumos, resultado.getNumber(), resultado.getTotalPages(),
                resultado.getTotalElements());
    }

    /** Detalhe de um pedido do cliente. 404 se não existir ou for de outra pessoa. */
    public DetalhePedido detalhar(Long usuarioId, Long pedidoId) {
        checkout.exigirClienteAtivo(usuarioId);
        Pedido pedido = pedidoRepository.findByIdAndUsuarioId(pedidoId, usuarioId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        List<LinhaItem> itens = pedido.getItens().stream().map(PedidosClienteService::linhaDe).toList();
        List<LinhaPagamento> pagamentos = pagamentoRepository.findByPedidoIdOrderByIdDesc(pedidoId).stream()
                .map(PedidosClienteService::linhaDe).toList();
        return new DetalhePedido(pedido.getId(), pedido.getCriadoEm(), pedido.getStatus(), pedido.getTipoEntrega(),
                itens, pedido.getSubtotal(), pedido.getFrete(), pedido.getTotal(), pedido.getPrazoEntregaDias(),
                pedido.getEnderecoEntrega(), pedido.getLocalRetirada(), pagamentos, linhaDoTempo(pedido),
                itens.stream().anyMatch(LinhaItem::isExigeReceita));
    }

    private List<PassoDoHistorico> linhaDoTempo(Pedido pedido) {
        List<HistoricoStatusPedido> eventos = historicoRepository.findByPedidoIdOrderByCriadoEmAscIdAsc(pedido.getId());
        if (eventos.isEmpty()) {
            // Pedido sem histórico gravado: mostra ao menos onde ele está agora.
            return List.of(new PassoDoHistorico(pedido.getStatus(), formatar(pedido.getAtualizadoEm()), true));
        }
        return java.util.stream.IntStream.range(0, eventos.size())
                .mapToObj(i -> new PassoDoHistorico(eventos.get(i).getStatus(),
                        formatar(eventos.get(i).getCriadoEm()), i == eventos.size() - 1))
                .toList();
    }

    private static LinhaItem linhaDe(ItemPedido i) {
        return new LinhaItem(i.getNomeProduto(), i.getDescricaoApresentacao(), i.getPrecoUnitario(),
                i.getQuantidade(), i.getSubtotal(), i.isExigeReceita());
    }

    /** Só o que é seguro mostrar: método, situação, valor e o cartão mascarado. Nunca o código do Pix/boleto nem o id do gateway. */
    private static LinhaPagamento linhaDe(Pagamento p) {
        String cartao = p.getCartaoUltimos4() == null ? null
                : (p.getCartaoBandeira() == null ? "Cartão" : p.getCartaoBandeira()) + " final " + p.getCartaoUltimos4();
        String motivo = p.getStatus() == StatusPagamento.RECUSADO ? p.getMensagem() : null;
        return new LinhaPagamento(p.getMetodo().getRotulo(), situacaoDe(p.getStatus()), p.getValor(), p.getParcelas(),
                cartao, motivo, formatar(p.getCriadoEm()));
    }

    private static String situacaoDe(StatusPagamento status) {
        return switch (status) {
            case PENDENTE -> "Aguardando pagamento";
            case APROVADO -> "Aprovado";
            case RECUSADO -> "Recusado";
            case EXPIRADO -> "Expirado";
        };
    }

    private static String formatar(Instant instante) {
        return instante == null ? "" : DATA_HORA.format(instante);
    }

    public record FiltroMeusPedidos(StatusPedido status, PeriodoFiltro periodo) {
        public static final FiltroMeusPedidos VAZIO = new FiltroMeusPedidos(null, PeriodoFiltro.VAZIO);

        public boolean informado() {
            return status != null || (periodo != null && periodo.informado());
        }
    }

    @Value
    public static class ResumoPedido {
        Long id;
        Instant criadoEm;
        BigDecimal total;
        long unidades;
        StatusPedido status;
        TipoEntrega tipoEntrega;

        public String getCriadoEmFormatado() {
            return formatar(criadoEm);
        }
    }

    @Value
    public static class PaginaDePedidos {
        List<ResumoPedido> pedidos;
        int pagina;
        int totalPaginas;
        long totalPedidos;

        public boolean isVazia() {
            return pedidos.isEmpty();
        }

        public boolean isTemAnterior() {
            return pagina > 0;
        }

        public boolean isTemProxima() {
            return pagina + 1 < totalPaginas;
        }
    }

    @Value
    public static class LinhaItem {
        String nome;
        String descricao;
        BigDecimal precoUnitario;
        int quantidade;
        BigDecimal subtotal;
        boolean exigeReceita;
    }

    @Value
    public static class LinhaPagamento {
        String metodo;
        String situacao;
        BigDecimal valor;
        int parcelas;
        /** Ex.: "VISA final 1111". Nulo em Pix e boleto. */
        String cartaoMascarado;
        /** Motivo informado pelo gateway, só quando recusado. */
        String motivoRecusa;
        String quandoFormatado;
    }

    @Value
    public static class PassoDoHistorico {
        StatusPedido status;
        String quandoFormatado;
        /** O último passo é onde o pedido está agora. */
        boolean atual;
    }

    @Value
    public static class DetalhePedido {
        Long id;
        Instant criadoEm;
        StatusPedido status;
        TipoEntrega tipoEntrega;
        List<LinhaItem> itens;
        BigDecimal subtotal;
        BigDecimal frete;
        BigDecimal total;
        Integer prazoEntregaDias;
        EnderecoEntrega enderecoEntrega;
        LocalRetirada localRetirada;
        List<LinhaPagamento> pagamentos;
        List<PassoDoHistorico> historico;
        boolean exigeReceita;

        public String getCriadoEmFormatado() {
            return formatar(criadoEm);
        }

        public boolean isAguardandoPagamento() {
            return status == StatusPedido.AGUARDANDO_PAGAMENTO;
        }

        public boolean isProntoParaRetirada() {
            return status == StatusPedido.PRONTO_PARA_RETIRADA;
        }

        public boolean isEntrega() {
            return tipoEntrega == TipoEntrega.ENTREGA;
        }
    }
}
