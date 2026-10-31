package com.farmacia.farmaciaonline.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.support.TransactionTemplate;

import com.farmacia.farmaciaonline.TestcontainersConfiguration;
import com.farmacia.farmaciaonline.gateway.demo.DemoGatewayAdapter;
import com.farmacia.farmaciaonline.mensagem.demo.DemoEnvioAdapter;
import com.farmacia.farmaciaonline.model.Apresentacao;
import com.farmacia.farmaciaonline.model.Carrinho;
import com.farmacia.farmaciaonline.model.CartaoForm;
import com.farmacia.farmaciaonline.model.CheckoutForm;
import com.farmacia.farmaciaonline.model.EnderecoForm;
import com.farmacia.farmaciaonline.model.ItemCarrinho;
import com.farmacia.farmaciaonline.model.Produto;
import com.farmacia.farmaciaonline.model.Usuario;
import com.farmacia.farmaciaonline.model.enums.MetodoPagamento;
import com.farmacia.farmaciaonline.model.enums.Perfil;
import com.farmacia.farmaciaonline.model.enums.StatusUsuario;
import com.farmacia.farmaciaonline.model.enums.TipoEntrega;
import com.farmacia.farmaciaonline.repository.ApresentacaoRepository;
import com.farmacia.farmaciaonline.repository.CarrinhoRepository;
import com.farmacia.farmaciaonline.repository.HistoricoStatusPedidoRepository;
import com.farmacia.farmaciaonline.repository.PagamentoRepository;
import com.farmacia.farmaciaonline.repository.PedidoRepository;
import com.farmacia.farmaciaonline.repository.ProdutoRepository;
import com.farmacia.farmaciaonline.repository.UsuarioRepository;

/**
 * Base dos testes do checkout. Sem {@code @Transactional} de propósito: o assunto aqui são commits, travas e
 * concorrência, e um rollback no fim do teste esconderia justamente isso. Por isso cada teste cria o próprio cliente e o
 * próprio produto, e este contexto (propriedades e espiões próprios) sobe um banco separado, sem sujar o seed dos outros
 * testes. O cron desligado impede o job de expirar pedidos no meio de um teste.
 */
@SpringBootTest(properties = "app.checkout.expiracao.cron=-")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
abstract class CheckoutTesteBase {

    /** Números fictícios: passam no dígito verificador; o gateway demo recusa os que terminam em 0. */
    static final String CARTAO_APROVADO = "4111111111111111";
    static final String CARTAO_RECUSADO = "4111111111111210";

    private static final AtomicInteger SEQUENCIA = new AtomicInteger(1000);

    @SpyBean
    DemoGatewayAdapter gateway;

    @SpyBean
    DemoEnvioAdapter envio;

    @Autowired
    CheckoutService checkout;

    @Autowired
    PagamentoPedidoService pagamentos;

    @Autowired
    EstadoPedidoService estado;

    @Autowired
    ResumoPedidoService resumos;

    @Autowired
    EnderecoService enderecos;

    @Autowired
    UsuarioRepository usuarioRepository;

    @Autowired
    ProdutoRepository produtoRepository;

    @Autowired
    ApresentacaoRepository apresentacaoRepository;

    @Autowired
    CarrinhoRepository carrinhoRepository;

    @Autowired
    PedidoRepository pedidoRepository;

    @Autowired
    PagamentoRepository pagamentoRepository;

    @Autowired
    HistoricoStatusPedidoRepository historicoRepository;

    @Autowired
    TransactionTemplate transacao;

    Usuario novoCliente() {
        return novoUsuario(Perfil.CLIENTE, StatusUsuario.ATIVA);
    }

    Usuario novoUsuario(Perfil perfil, StatusUsuario status) {
        int n = SEQUENCIA.incrementAndGet();
        return usuarioRepository.saveAndFlush(Usuario.builder()
                .cpf(String.format("%011d", n))
                .nome("Cliente " + n)
                .email("cliente" + n + "@teste.com")
                .dataNascimento(LocalDate.of(1995, 5, 20))
                .senhaHash("hash")
                .perfil(perfil)
                .status(status)
                .termosVersao("1.0")
                .termosAceitosEm(Instant.now())
                .build());
    }

    Apresentacao novaApresentacao(String preco, int estoque) {
        return novaApresentacao(preco, estoque, false);
    }

    Apresentacao novaApresentacao(String preco, int estoque, boolean exigeReceita) {
        int n = SEQUENCIA.incrementAndGet();
        Produto produto = new Produto();
        produto.setNome("Produto " + n);
        produto.setMarca("Marca");
        produto.setExigeReceita(exigeReceita);
        produto = produtoRepository.saveAndFlush(produto);

        Apresentacao apresentacao = new Apresentacao();
        apresentacao.setProduto(produto);
        apresentacao.setDescricao("10 unidades");
        apresentacao.setSku("SKU-" + n);
        apresentacao.setEan(String.format("%013d", n));
        apresentacao.setPreco(new BigDecimal(preco));
        apresentacao.setEstoque(estoque);
        return apresentacaoRepository.saveAndFlush(apresentacao);
    }

    void noCarrinho(Usuario cliente, Apresentacao apresentacao, int quantidade) {
        transacao.executeWithoutResult(status -> {
            Carrinho carrinho = carrinhoRepository.findByUsuarioId(cliente.getId()).orElseGet(() -> {
                Carrinho novo = new Carrinho();
                novo.setUsuario(cliente);
                return carrinhoRepository.save(novo);
            });
            ItemCarrinho item = new ItemCarrinho();
            item.setCarrinho(carrinho);
            item.setApresentacao(apresentacaoRepository.findById(apresentacao.getId()).orElseThrow());
            item.setQuantidade(quantidade);
            item.setPrecoVisto(item.getApresentacao().getPreco());
            carrinho.getItens().add(item);
        });
    }

    int itensNoCarrinho(Usuario cliente) {
        return transacao.execute(status -> carrinhoRepository.findByUsuarioId(cliente.getId())
                .map(c -> c.getItens().size()).orElse(0));
    }

    int estoqueDe(Apresentacao apresentacao) {
        return apresentacaoRepository.findById(apresentacao.getId()).orElseThrow().getEstoque();
    }

    long vendidosDe(Apresentacao apresentacao) {
        return produtoRepository.findById(apresentacao.getProduto().getId()).orElseThrow().getQtdVendida();
    }

    void mudarPreco(Apresentacao apresentacao, String preco) {
        Apresentacao atual = apresentacaoRepository.findById(apresentacao.getId()).orElseThrow();
        atual.setPreco(new BigDecimal(preco));
        apresentacaoRepository.saveAndFlush(atual);
    }

    Long novoEndereco(Usuario cliente, String cep, String uf) {
        EnderecoForm form = new EnderecoForm();
        form.setDestinatario("Destinatário");
        form.setCep(cep);
        form.setLogradouro("Avenida Teste");
        form.setNumero("100");
        form.setBairro("Centro");
        form.setCidade("Cidade");
        form.setUf(uf);
        return enderecos.salvar(cliente.getId(), null, form);
    }

    static CheckoutForm entrega(Long enderecoId, MetodoPagamento metodo) {
        CheckoutForm form = new CheckoutForm();
        form.setTipoEntrega(TipoEntrega.ENTREGA);
        form.setEnderecoId(enderecoId);
        form.setMetodo(metodo);
        return form;
    }

    /** A loja 1 vem do seed. */
    static CheckoutForm retirada(MetodoPagamento metodo) {
        CheckoutForm form = new CheckoutForm();
        form.setTipoEntrega(TipoEntrega.RETIRADA);
        form.setLojaId(1L);
        form.setMetodo(metodo);
        return form;
    }

    static CartaoForm cartao(String numero) {
        CartaoForm form = new CartaoForm();
        form.setNumeroCartao(numero);
        form.setValidadeCartao("12/40");
        form.setCvv("123");
        form.setNomeImpresso("CLIENTE TESTE");
        return form;
    }

    String conferenciaDe(Usuario cliente, CheckoutForm form) {
        return checkout.montarResumo(cliente.getId(), form).orElseThrow().conferencia();
    }

    /** Percurso normal: monta o resumo e confirma com o que foi mostrado. */
    Long comprar(Usuario cliente, CheckoutForm form, CartaoForm cartao) {
        return pagamentos.confirmarCompra(cliente.getId(), form, conferenciaDe(cliente, form), UUID.randomUUID(), cartao);
    }
}
