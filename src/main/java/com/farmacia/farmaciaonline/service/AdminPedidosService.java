package com.farmacia.farmaciaonline.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
import com.farmacia.farmaciaonline.model.Usuario;
import com.farmacia.farmaciaonline.model.enums.StatusPedido;
import com.farmacia.farmaciaonline.model.enums.TipoEntrega;
import com.farmacia.farmaciaonline.repository.HistoricoStatusPedidoRepository;
import com.farmacia.farmaciaonline.repository.PagamentoRepository;
import com.farmacia.farmaciaonline.repository.PedidoRepository;
import com.farmacia.farmaciaonline.repository.UsuarioRepository;

import lombok.RequiredArgsConstructor;

/** Consultas e comandos do painel administrativo de pedidos (UC17). */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminPedidosService {

    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm")
            .withZone(ZoneId.of("America/Sao_Paulo"));

    private final PedidoRepository pedidoRepository;
    private final PagamentoRepository pagamentoRepository;
    private final HistoricoStatusPedidoRepository historicoRepository;
    private final UsuarioRepository usuarioRepository;
    private final EstadoPedidoService estadoPedidoService;

    public Page<Resumo> listar(Long numero, StatusPedido status, Pageable pageable) {
        return pedidoRepository.buscarParaAdministracao(numero, status, pageable).map(AdminPedidosService::resumo);
    }

    public Detalhe detalhar(Long id) {
        Pedido pedido = pedidoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        List<LinhaItem> itens = pedido.getItens().stream().map(AdminPedidosService::linhaItem).toList();
        List<LinhaPagamento> pagamentos = pagamentoRepository.findByPedidoIdOrderByIdDesc(id).stream()
                .map(AdminPedidosService::linhaPagamento).toList();
        List<Evento> eventos = historicoRepository.findByPedidoIdOrderByCriadoEmAscIdAsc(id).stream()
                .map(AdminPedidosService::evento).toList();
        Usuario conferidoPor = pedido.getReceitaConferidaPor();
        return new Detalhe(pedido.getId(), pedido.getVersao(), pedido.getCriadoEm(), formatar(pedido.getCriadoEm()),
                pedido.getStatus(), pedido.getTipoEntrega(), pedido.getUsuario().getNome(), pedido.getUsuario().getEmail(),
                itens, pagamentos, eventos, pedido.getSubtotal(), pedido.getFrete(), pedido.getTotal(),
                localDoPedido(pedido.getTipoEntrega(), pedido.getEnderecoEntrega(), pedido.getLocalRetirada()),
                itens.stream().anyMatch(LinhaItem::exigeReceita), pedido.getReceitaConferidaEm(),
                pedido.getReceitaConferidaEm() == null ? null : formatar(pedido.getReceitaConferidaEm()),
                conferidoPor == null ? null : conferidoPor.getNome(), pedido.isEstornoPendente(),
                proximosStatus(pedido.getStatus(), pedido.getTipoEntrega(), pedido.temItemQueExigeReceita(),
                        pedido.getReceitaConferidaEm() != null));
    }

    @Transactional
    public void transicionar(Long pedidoId, Long versao, StatusPedido destino, Long administradorId, String observacao) {
        Usuario admin = administrador(administradorId);
        estadoPedidoService.transicionarAdministrativamente(pedidoId, versao, destino, admin, observacao);
    }

    @Transactional
    public void registrarConferencia(Long pedidoId, Long versao, Long administradorId) {
        Usuario admin = administrador(administradorId);
        estadoPedidoService.registrarConferenciaReceita(pedidoId, versao, admin);
    }

    private Usuario administrador(Long id) {
        if (id == null) {
            throw new AcaoPedidoInvalidaException("Não foi possível identificar o administrador autenticado.");
        }
        return usuarioRepository.findById(id)
                .orElseThrow(() -> new AcaoPedidoInvalidaException("Administrador não encontrado."));
    }

    private static Resumo resumo(Pedido pedido) {
        return new Resumo(pedido.getId(), pedido.getUsuario().getNome(), formatar(pedido.getCriadoEm()),
                pedido.getStatus(), pedido.getTipoEntrega(), pedido.getTotal());
    }

    private static LinhaItem linhaItem(ItemPedido item) {
        return new LinhaItem(item.getNomeProduto(), item.getDescricaoApresentacao(), item.getSku(), item.getQuantidade(),
                item.getPrecoUnitario(), item.getSubtotal(), item.isExigeReceita());
    }

    private static LinhaPagamento linhaPagamento(Pagamento pagamento) {
        String cartaoMascarado = pagamento.getCartaoUltimos4() == null ? null
                : (pagamento.getCartaoBandeira() == null ? "Cartão" : pagamento.getCartaoBandeira())
                        + " final " + pagamento.getCartaoUltimos4();
        return new LinhaPagamento(pagamento.getMetodo().getRotulo(), pagamento.getStatus().name(),
                pagamento.getValor(), pagamento.getParcelas(), cartaoMascarado, formatar(pagamento.getCriadoEm()));
    }

    private static Evento evento(HistoricoStatusPedido evento) {
        return new Evento(evento.getStatusAnterior(), evento.getStatus(),
                evento.getAlteradoPor() == null ? "Sistema" : evento.getAlteradoPor().getNome(),
                evento.getObservacao(), formatar(evento.getCriadoEm()));
    }

    private static String localDoPedido(TipoEntrega tipo, EnderecoEntrega endereco, LocalRetirada retirada) {
        if (tipo == TipoEntrega.RETIRADA && retirada != null) {
            return retirada.getNome() + " — " + retirada.getLogradouro() + ", " + retirada.getNumero() + " — "
                    + retirada.getBairro() + ", " + retirada.getCidade() + "/" + retirada.getUf()
                    + (retirada.getHorarioFuncionamento() == null ? "" : " — " + retirada.getHorarioFuncionamento());
        }
        if (endereco == null) {
            return "Endereço não registrado";
        }
        return endereco.getDestinatario() + " — " + endereco.getLogradouro() + ", " + endereco.getNumero()
                + (endereco.getComplemento() == null || endereco.getComplemento().isBlank()
                        ? "" : " (" + endereco.getComplemento() + ")")
                + " — " + endereco.getBairro() + ", " + endereco.getCidade() + "/" + endereco.getUf()
                + " — CEP " + endereco.getCep();
    }

    private static List<StatusPedido> proximosStatus(StatusPedido atual, TipoEntrega tipo, boolean exigeReceita,
            boolean receitaConferida) {
        return switch (atual) {
            case AGUARDANDO_PAGAMENTO -> List.of(StatusPedido.CANCELADO);
            case PAGAMENTO_APROVADO -> List.of(StatusPedido.EM_SEPARACAO, StatusPedido.CANCELADO);
            case EM_SEPARACAO -> tipo == TipoEntrega.ENTREGA
                    ? List.of(StatusPedido.ENVIADO, StatusPedido.CANCELADO)
                    : List.of(StatusPedido.PRONTO_PARA_RETIRADA, StatusPedido.CANCELADO);
            case ENVIADO -> List.of(StatusPedido.ENTREGUE);
            case PRONTO_PARA_RETIRADA -> !exigeReceita || receitaConferida
                    ? List.of(StatusPedido.ENTREGUE) : List.of();
            case ENTREGUE, CANCELADO -> List.of();
        };
    }

    private static String formatar(Instant instante) {
        return instante == null ? "" : DATA_HORA.format(instante);
    }

    public record Resumo(Long id, String cliente, String criadoEm, StatusPedido status, TipoEntrega tipoEntrega,
            BigDecimal total) {}

    public record LinhaItem(String nome, String apresentacao, String sku, int quantidade, BigDecimal precoUnitario,
            BigDecimal subtotal, boolean exigeReceita) {}

    public record LinhaPagamento(String metodo, String status, BigDecimal valor, int parcelas,
            String cartaoMascarado, String criadoEm) {}

    public record Evento(StatusPedido anterior, StatusPedido status, String autor, String observacao, String criadoEm) {}

    public record Detalhe(Long id, Long versao, Instant criadoEm, String criadoEmFormatado, StatusPedido status,
            TipoEntrega tipoEntrega, String cliente, String email, List<LinhaItem> itens, List<LinhaPagamento> pagamentos,
            List<Evento> historico, BigDecimal subtotal, BigDecimal frete, BigDecimal total, String local,
            boolean exigeReceita, Instant receitaConferidaEm, String receitaConferidaEmFormatado,
            String receitaConferidaPor, boolean estornoPendente, List<StatusPedido> proximosStatus) {}
}
