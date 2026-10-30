package com.farmacia.farmaciaonline.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;

import com.farmacia.farmaciaonline.TestcontainersConfiguration;
import com.farmacia.farmaciaonline.model.Apresentacao;
import com.farmacia.farmaciaonline.model.EnderecoEntrega;
import com.farmacia.farmaciaonline.model.HistoricoStatusPedido;
import com.farmacia.farmaciaonline.model.ItemPedido;
import com.farmacia.farmaciaonline.model.LogAuditoria;
import com.farmacia.farmaciaonline.model.Loja;
import com.farmacia.farmaciaonline.model.Pagamento;
import com.farmacia.farmaciaonline.model.Pedido;
import com.farmacia.farmaciaonline.model.Usuario;
import com.farmacia.farmaciaonline.model.enums.MetodoPagamento;
import com.farmacia.farmaciaonline.model.enums.StatusPagamento;
import com.farmacia.farmaciaonline.model.enums.StatusPedido;
import com.farmacia.farmaciaonline.model.enums.TipoEntrega;

/**
 * Pedido, pagamento, histórico e auditoria contra o PostgreSQL real (V1 + seed do V2).
 * Cada teste é desfeito no fim (@DataJpaTest é transacional).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class PedidoRepositoryTest {

    @Autowired
    private PedidoRepository pedidoRepository;

    @Autowired
    private PagamentoRepository pagamentoRepository;

    @Autowired
    private HistoricoStatusPedidoRepository historicoRepository;

    @Autowired
    private LogAuditoriaRepository logAuditoriaRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private ApresentacaoRepository apresentacaoRepository;

    @Autowired
    private LojaRepository lojaRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbc;

    private Usuario cliente() {
        return usuarioRepository.findByEmailIgnoreCase("cliente@farma.com").orElseThrow();
    }

    private Usuario admin() {
        return usuarioRepository.findByEmailIgnoreCase("admin@farma.com").orElseThrow();
    }

    private Apresentacao apresentacao(String sku) {
        return apresentacaoRepository.findBySku(sku).orElseThrow();
    }

    private ItemPedido item(Pedido pedido, Apresentacao apresentacao, int quantidade) {
        ItemPedido item = new ItemPedido();
        item.setPedido(pedido);
        item.setApresentacao(apresentacao);
        item.setNomeProduto(apresentacao.getProduto().getNome());
        item.setDescricaoApresentacao(apresentacao.getDescricao());
        item.setSku(apresentacao.getSku());
        item.setPrecoUnitario(apresentacao.getPreco());
        item.setQuantidade(quantidade);
        item.setExigeReceita(apresentacao.getProduto().isExigeReceita());
        return item;
    }

    private Pedido pedidoDeEntrega(Usuario usuario) {
        EnderecoEntrega endereco = new EnderecoEntrega();
        endereco.setDestinatario("Cliente Teste");
        endereco.setCep("01310100");
        endereco.setLogradouro("Avenida Paulista");
        endereco.setNumero("1000");
        endereco.setComplemento("Apto 12");
        endereco.setBairro("Bela Vista");
        endereco.setCidade("São Paulo");
        endereco.setUf("SP");
        endereco.setReferencia("Portaria 24h");

        Pedido pedido = new Pedido();
        pedido.setUsuario(usuario);
        pedido.setTipoEntrega(TipoEntrega.ENTREGA);
        pedido.setEnderecoEntrega(endereco);
        pedido.setPrazoEntregaDias(5);
        pedido.setFrete(new BigDecimal("9.90"));
        pedido.getItens().add(item(pedido, apresentacao("NOV-DIP1G-20"), 2));
        pedido.getItens().add(item(pedido, apresentacao("PRA-DIP500-20"), 1));
        pedido.recalcularTotais();
        return pedido;
    }

    private Pedido pedidoDeRetirada(Usuario usuario, String sku) {
        Loja loja = lojaRepository.findByAtivaTrue().get(0);

        Pedido pedido = new Pedido();
        pedido.setUsuario(usuario);
        pedido.setTipoEntrega(TipoEntrega.RETIRADA);
        pedido.setLojaRetirada(loja);
        pedido.getItens().add(item(pedido, apresentacao(sku), 1));
        pedido.recalcularTotais();
        return pedido;
    }

    private Pagamento pagamento(Pedido pedido, MetodoPagamento metodo, StatusPagamento status, int parcelas) {
        Pagamento pagamento = new Pagamento();
        pagamento.setPedido(pedido);
        pagamento.setMetodo(metodo);
        pagamento.setStatus(status);
        pagamento.setValor(pedido.getTotal());
        pagamento.setParcelas(parcelas);
        return pagamento;
    }

    private HistoricoStatusPedido historico(Pedido pedido, StatusPedido status, Usuario alteradoPor) {
        HistoricoStatusPedido historico = new HistoricoStatusPedido();
        historico.setPedido(pedido);
        historico.setStatus(status);
        historico.setAlteradoPor(alteradoPor);
        return historico;
    }

    /** criado_em é preenchido pelo Hibernate; fixar datas distintas evita depender do relógio. */
    private void definirCriadoEm(String tabela, Long id, int diasAtras) {
        jdbc.update("UPDATE " + tabela + " SET criado_em = ? WHERE id = ?",
                Timestamp.from(Instant.now().minus(diasAtras, ChronoUnit.DAYS)), id);
    }

    // ---------- Pedido ----------

    @Test
    @DisplayName("pedido de entrega grava itens e o endereço nas colunas entrega_*")
    void pedidoDeEntregaGravaItensEEndereco() {
        Pedido pedido = pedidoRepository.saveAndFlush(pedidoDeEntrega(cliente()));
        entityManager.clear();

        Pedido salvo = pedidoRepository.findById(pedido.getId()).orElseThrow();

        assertThat(salvo.getStatus()).isEqualTo(StatusPedido.AGUARDANDO_PAGAMENTO);
        assertThat(salvo.getSubtotal()).isEqualByComparingTo("74.88");
        assertThat(salvo.getTotal()).isEqualByComparingTo("84.78");
        assertThat(salvo.getLojaRetirada()).isNull();
        assertThat(salvo.getEnderecoEntrega().getCep()).isEqualTo("01310100");
        assertThat(salvo.getEnderecoEntrega().getReferencia()).isEqualTo("Portaria 24h");
        assertThat(jdbc.queryForObject("SELECT entrega_cep FROM pedido WHERE id = ?", String.class, pedido.getId()))
                .isEqualTo("01310100");
        assertThat(salvo.getItens()).extracting(ItemPedido::getSku).containsExactly("NOV-DIP1G-20", "PRA-DIP500-20");
        assertThat(salvo.getItens().get(0).getNomeProduto()).isEqualTo("Novalgina Dipirona 1g");
    }

    @Test
    @DisplayName("pedido de retirada fica sem endereço, com a loja, e guarda a exigência de receita do item")
    void pedidoDeRetiradaFicaSemEndereco() {
        Pedido pedido = pedidoRepository.saveAndFlush(pedidoDeRetirada(cliente(), "MED-AMOX500-21"));
        entityManager.clear();

        Pedido salvo = pedidoRepository.findById(pedido.getId()).orElseThrow();

        assertThat(salvo.getEnderecoEntrega()).isNull();
        assertThat(salvo.getLojaRetirada()).isNotNull();
        assertThat(salvo.getFrete()).isEqualByComparingTo("0.00");
        assertThat(salvo.temItemQueExigeReceita()).isTrue();
        assertThat(jdbc.queryForObject("SELECT entrega_cep FROM pedido WHERE id = ?", String.class, pedido.getId()))
                .isNull();
    }

    @Test
    @DisplayName("o banco recusa pedido de entrega sem endereço")
    void entregaSemEnderecoEhRecusada() {
        Pedido semEndereco = pedidoDeEntrega(cliente());
        semEndereco.setEnderecoEntrega(null);

        assertThatThrownBy(() -> pedidoRepository.saveAndFlush(semEndereco))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("o banco recusa pedido de retirada com frete")
    void retiradaComFreteEhRecusada() {
        Pedido comFrete = pedidoDeRetirada(cliente(), "NOV-DIP1G-20");
        comFrete.setFrete(new BigDecimal("5.00"));
        comFrete.recalcularTotais();

        assertThatThrownBy(() -> pedidoRepository.saveAndFlush(comFrete))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("o banco recusa total diferente de subtotal + frete")
    void totalErradoEhRecusado() {
        Pedido totalErrado = pedidoDeEntrega(cliente());
        totalErrado.setTotal(totalErrado.getTotal().add(new BigDecimal("0.01")));

        assertThatThrownBy(() -> pedidoRepository.saveAndFlush(totalErrado))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("o usuário só encontra os próprios pedidos")
    void usuarioSoVeOsProprioPedidos() {
        Usuario cliente = cliente();
        Pedido pedido = pedidoRepository.saveAndFlush(pedidoDeRetirada(cliente, "NOV-DIP1G-20"));
        entityManager.clear();

        assertThat(pedidoRepository.findByIdAndUsuarioId(pedido.getId(), cliente.getId())).isPresent();
        assertThat(pedidoRepository.findByIdAndUsuarioId(pedido.getId(), admin().getId())).isEmpty();
    }

    @Test
    @DisplayName("pedidos do usuário vêm do mais recente para o mais antigo, paginados")
    void pedidosDoUsuarioVemDoMaisRecenteEPaginados() {
        Usuario cliente = cliente();
        Pedido antigo = pedidoRepository.saveAndFlush(pedidoDeRetirada(cliente, "NOV-DIP1G-20"));
        Pedido recente = pedidoRepository.saveAndFlush(pedidoDeRetirada(cliente, "NOV-DIP1G-20"));
        Pedido meio = pedidoRepository.saveAndFlush(pedidoDeRetirada(cliente, "NOV-DIP1G-20"));
        definirCriadoEm("pedido", antigo.getId(), 3);
        definirCriadoEm("pedido", recente.getId(), 1);
        definirCriadoEm("pedido", meio.getId(), 2);
        entityManager.clear();

        Page<Pedido> pagina = pedidoRepository.findByUsuarioIdOrderByCriadoEmDesc(cliente.getId(), PageRequest.of(0, 2));

        assertThat(pagina.getContent()).extracting(Pedido::getId).containsExactly(recente.getId(), meio.getId());
        assertThat(pagina.getTotalElements()).isEqualTo(3);
        assertThat(pagina.getTotalPages()).isEqualTo(2);
    }

    @Test
    @DisplayName("o banco aceita o status PRONTO_PARA_RETIRADA no pedido e no histórico")
    void bancoAceitaProntoParaRetirada() {
        Pedido pedido = pedidoRepository.saveAndFlush(pedidoDeRetirada(cliente(), "NOV-DIP1G-20"));
        pedido.setStatus(StatusPedido.PRONTO_PARA_RETIRADA);
        pedidoRepository.saveAndFlush(pedido);
        historicoRepository.saveAndFlush(historico(pedido, StatusPedido.PRONTO_PARA_RETIRADA, admin()));
        entityManager.clear();

        assertThat(pedidoRepository.findById(pedido.getId()).orElseThrow().getStatus())
                .isEqualTo(StatusPedido.PRONTO_PARA_RETIRADA);
        assertThat(historicoRepository.findByPedidoIdOrderByCriadoEmAsc(pedido.getId()))
                .extracting(HistoricoStatusPedido::getStatus)
                .containsExactly(StatusPedido.PRONTO_PARA_RETIRADA);
    }

    // ---------- Pagamento ----------

    @Test
    @DisplayName("um pedido pode ter várias tentativas de pagamento")
    void pedidoTemVariasTentativasDePagamento() {
        Pedido pedido = pedidoRepository.saveAndFlush(pedidoDeEntrega(cliente()));
        pagamentoRepository.saveAndFlush(pagamento(pedido, MetodoPagamento.CARTAO_CREDITO, StatusPagamento.RECUSADO, 3));
        pagamentoRepository.saveAndFlush(pagamento(pedido, MetodoPagamento.PIX, StatusPagamento.APROVADO, 1));
        entityManager.clear();

        List<Pagamento> pagamentos = pagamentoRepository.findByPedidoId(pedido.getId());

        assertThat(pagamentos).extracting(Pagamento::getStatus)
                .containsExactlyInAnyOrder(StatusPagamento.RECUSADO, StatusPagamento.APROVADO);
    }

    @Test
    @DisplayName("o banco recusa parcelamento fora do cartão de crédito")
    void parcelasSoNoCartaoDeCredito() {
        Pedido pedido = pedidoRepository.saveAndFlush(pedidoDeEntrega(cliente()));
        Pagamento pixParcelado = pagamento(pedido, MetodoPagamento.PIX, StatusPagamento.PENDENTE, 3);

        assertThatThrownBy(() -> pagamentoRepository.saveAndFlush(pixParcelado))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ---------- Histórico ----------

    @Test
    @DisplayName("histórico vem em ordem cronológica e aceita alteradoPor nulo (sistema)")
    void historicoVemEmOrdemEAceitaSistema() {
        Usuario admin = admin();
        Pedido pedido = pedidoRepository.saveAndFlush(pedidoDeRetirada(cliente(), "NOV-DIP1G-20"));
        HistoricoStatusPedido separacao = historicoRepository.saveAndFlush(historico(pedido, StatusPedido.EM_SEPARACAO, admin));
        HistoricoStatusPedido aguardando = historicoRepository.saveAndFlush(historico(pedido, StatusPedido.AGUARDANDO_PAGAMENTO, null));
        HistoricoStatusPedido aprovado = historicoRepository.saveAndFlush(historico(pedido, StatusPedido.PAGAMENTO_APROVADO, null));
        definirCriadoEm("historico_status_pedido", aguardando.getId(), 3);
        definirCriadoEm("historico_status_pedido", aprovado.getId(), 2);
        definirCriadoEm("historico_status_pedido", separacao.getId(), 1);
        entityManager.clear();

        List<HistoricoStatusPedido> linhaDoTempo = historicoRepository.findByPedidoIdOrderByCriadoEmAsc(pedido.getId());

        assertThat(linhaDoTempo).extracting(HistoricoStatusPedido::getStatus)
                .containsExactly(StatusPedido.AGUARDANDO_PAGAMENTO, StatusPedido.PAGAMENTO_APROVADO, StatusPedido.EM_SEPARACAO);
        assertThat(linhaDoTempo.get(0).getAlteradoPor()).isNull();
        assertThat(linhaDoTempo.get(2).getAlteradoPor().getId()).isEqualTo(admin.getId());
    }

    // ---------- Auditoria ----------

    @Test
    @DisplayName("log de auditoria grava quem acessou, a ação, o alvo e o IP")
    void logDeAuditoriaGravaOAcesso() {
        Usuario admin = admin();
        LogAuditoria log = new LogAuditoria();
        log.setUsuario(admin);
        log.setAcao("CLIENTE_VISUALIZAR");
        log.setAlvoTipo("USUARIO");
        log.setAlvoId(cliente().getId());
        log.setDetalhes("Consulta ao cadastro do cliente");
        log.setIp("203.0.113.10");
        logAuditoriaRepository.saveAndFlush(log);
        entityManager.clear();

        LogAuditoria salvo = logAuditoriaRepository.findById(log.getId()).orElseThrow();

        assertThat(salvo.getAcao()).isEqualTo("CLIENTE_VISUALIZAR");
        assertThat(salvo.getIp()).isEqualTo("203.0.113.10");
        assertThat(salvo.getUsuario().getId()).isEqualTo(admin.getId());
        assertThat(salvo.getCriadoEm()).isNotNull();
    }
}
