package com.farmacia.farmaciaonline.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.DigestUtils;
import org.springframework.web.server.ResponseStatusException;

import com.farmacia.farmaciaonline.model.Apresentacao;
import com.farmacia.farmaciaonline.model.Carrinho;
import com.farmacia.farmaciaonline.model.CheckoutForm;
import com.farmacia.farmaciaonline.model.Endereco;
import com.farmacia.farmaciaonline.model.EnderecoEntrega;
import com.farmacia.farmaciaonline.model.HistoricoStatusPedido;
import com.farmacia.farmaciaonline.model.ItemCarrinho;
import com.farmacia.farmaciaonline.model.ItemPedido;
import com.farmacia.farmaciaonline.model.LocalRetirada;
import com.farmacia.farmaciaonline.model.Loja;
import com.farmacia.farmaciaonline.model.Pagamento;
import com.farmacia.farmaciaonline.model.Pedido;
import com.farmacia.farmaciaonline.model.Produto;
import com.farmacia.farmaciaonline.model.Usuario;
import com.farmacia.farmaciaonline.model.enums.Perfil;
import com.farmacia.farmaciaonline.model.enums.StatusPedido;
import com.farmacia.farmaciaonline.model.enums.StatusUsuario;
import com.farmacia.farmaciaonline.model.enums.TipoEntrega;
import com.farmacia.farmaciaonline.repository.ApresentacaoRepository;
import com.farmacia.farmaciaonline.repository.CarrinhoRepository;
import com.farmacia.farmaciaonline.repository.HistoricoStatusPedidoRepository;
import com.farmacia.farmaciaonline.repository.LojaRepository;
import com.farmacia.farmaciaonline.repository.PagamentoRepository;
import com.farmacia.farmaciaonline.repository.PedidoRepository;
import com.farmacia.farmaciaonline.repository.UsuarioRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class CheckoutService {

    /** O que a tela de escolha precisa mostrar. */
    public record TelaEscolha(boolean carrinhoVazio, boolean exigeReceita, List<Endereco> enderecos, List<Loja> lojas) {}

    private final CarrinhoRepository carrinhoRepository;
    private final UsuarioRepository usuarioRepository;
    private final ApresentacaoRepository apresentacaoRepository;
    private final LojaRepository lojaRepository;
    private final PedidoRepository pedidoRepository;
    private final PagamentoRepository pagamentoRepository;
    private final HistoricoStatusPedidoRepository historicoRepository;
    private final EnderecoService enderecoService;
    private final FreteDemo freteDemo;

    @Value("${app.checkout.validade-reserva}")
    private Duration validadeReserva;

    @Transactional(readOnly = true)
    public TelaEscolha telaEscolha(Long usuarioId) {
        exigirClienteAtivo(usuarioId);
        List<ItemCarrinho> itens = itensDoCarrinho(usuarioId);
        boolean exigeReceita = itens.stream().anyMatch(i -> i.getApresentacao().getProduto().isExigeReceita());
        return new TelaEscolha(itens.isEmpty(), exigeReceita, enderecoService.listar(usuarioId),
                lojaRepository.findByAtivaTrue());
    }

    @Transactional(readOnly = true)
    public Optional<ResumoCheckout> montarResumo(Long usuarioId, CheckoutForm form) {
        exigirClienteAtivo(usuarioId);
        List<ItemCarrinho> itens = itensDoCarrinho(usuarioId);
        return itens.isEmpty() ? Optional.empty() : Optional.of(calcular(usuarioId, itens, form));
    }

    public void aceitarPrecos(Long usuarioId) {
        exigirClienteAtivo(usuarioId);
        itensDoCarrinho(usuarioId).forEach(i -> i.setPrecoVisto(i.getApresentacao().getPreco()));
    }

    public Long registrar(Long usuarioId, CheckoutForm form, String conferencia, UUID chave) {
        Usuario usuario = exigirClienteAtivo(usuarioId);

        Optional<Carrinho> travado = carrinhoRepository.buscarTravadoPorUsuario(usuarioId);

        Optional<Pedido> jaRegistrado = pedidoRepository.findByUsuarioIdAndChaveIdempotencia(usuarioId, chave);
        if (jaRegistrado.isPresent()) {
            return jaRegistrado.get().getId();
        }
        if (travado.isEmpty() || travado.get().getItens().isEmpty()) {
            throw new CheckoutException("Seu carrinho está vazio.");
        }
        Carrinho carrinho = travado.get();

        ResumoCheckout resumo = calcular(usuarioId, carrinho.getItens(), form);
        if (!resumo.problemas().isEmpty()) {
            throw new CheckoutException(resumo.problemas());
        }
        if (resumo.precoAlterado()) {
            throw new CheckoutException("O preço de um item mudou. Confira o novo total para confirmar.");
        }
        if (!resumo.conferencia().equals(conferencia)) {
            throw new CheckoutException("O resumo do pedido mudou. Confira os dados e confirme de novo.");
        }

        reservarEstoque(carrinho);
        Pedido pedido = pedidoRepository.saveAndFlush(novoPedido(usuario, carrinho, resumo, chave));
        pagamentoRepository.saveAndFlush(novoPagamento(pedido, form, chave));
        historicoRepository.saveAndFlush(historicoInicial(pedido));
        carrinho.getItens().clear();
        return pedido.getId();
    }

    public Usuario exigirClienteAtivo(Long usuarioId) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN));
        if (usuario.getPerfil() != Perfil.CLIENTE || usuario.getStatus() != StatusUsuario.ATIVA
                || usuario.getExcluidoEm() != null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        return usuario;
    }

    private List<ItemCarrinho> itensDoCarrinho(Long usuarioId) {
        return carrinhoRepository.findByUsuarioId(usuarioId).map(Carrinho::getItens).orElse(List.of());
    }

    private ResumoCheckout calcular(Long usuarioId, List<ItemCarrinho> carrinho, CheckoutForm form) {
        List<String> problemas = new ArrayList<>();
        List<ResumoCheckout.Item> itens = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO.setScale(2);
        for (ItemCarrinho item : carrinho) {
            Apresentacao apresentacao = item.getApresentacao();
            Produto produto = apresentacao.getProduto();
            problemaDoItem(item).ifPresent(problemas::add);
            BigDecimal subtotalItem = apresentacao.getPreco().multiply(BigDecimal.valueOf(item.getQuantidade()))
                    .setScale(2, RoundingMode.HALF_UP);
            subtotal = subtotal.add(subtotalItem);
            itens.add(new ResumoCheckout.Item(apresentacao.getId(), produto.getNome(), apresentacao.getDescricao(),
                    apresentacao.getSku(), apresentacao.getPreco(), item.getPrecoVisto(), item.getQuantidade(),
                    produto.isExigeReceita(), subtotalItem));
        }
        boolean exigeReceita = itens.stream().anyMatch(ResumoCheckout.Item::exigeReceita);
        boolean precoAlterado = itens.stream().anyMatch(ResumoCheckout.Item::isPrecoAlterado);

        EnderecoEntrega endereco = null;
        Loja loja = null;
        BigDecimal frete = BigDecimal.ZERO.setScale(2);
        Integer prazo = null;
        if (form.getTipoEntrega() == null) {
            problemas.add("Escolha entre entrega e retirada em loja.");
        } else if (form.getTipoEntrega() == TipoEntrega.RETIRADA) {
            loja = lojaAtiva(form.getLojaId()).orElse(null);
            if (loja == null) {
                problemas.add("Escolha uma loja disponível para a retirada.");
            }
        } else {
            if (exigeReceita) {
                problemas.add("Este pedido tem item que exige receita: só pode ser retirado em loja.");
            }
            if (form.getEnderecoId() == null) {
                problemas.add("Escolha o endereço de entrega.");
            } else {
                endereco = enderecoService.copiaParaEntrega(usuarioId, form.getEnderecoId());
                Optional<FreteDemo.Cotacao> cotacao = freteDemo.cotar(endereco.getUf(), endereco.getCep());
                if (cotacao.isEmpty()) {
                    problemas.add("Entregamos somente em Mato Grosso do Sul (MS). Escolha outro endereço ou retire em uma loja.");
                } else {
                    frete = cotacao.get().valor();
                    prazo = cotacao.get().prazoDias();
                }
            }
        }
        if (form.getMetodo() == null) {
            problemas.add("Escolha a forma de pagamento.");
        }

        BigDecimal total = subtotal.add(frete);
        return new ResumoCheckout(itens, subtotal, frete, total, prazo, exigeReceita, precoAlterado, problemas,
                form.getTipoEntrega(), endereco, loja, form.getMetodo(),
                conferencia(itens, total, form, endereco));
    }

    private static Optional<String> problemaDoItem(ItemCarrinho item) {
        Apresentacao apresentacao = item.getApresentacao();
        String nome = apresentacao.getProduto().getNome() + " " + apresentacao.getDescricao();
        if (!apresentacao.getProduto().isAtivo() || !apresentacao.isAtiva()) {
            return Optional.of(nome + " não está mais à venda. Remova-o do carrinho.");
        }
        if (apresentacao.getEstoque() == 0) {
            return Optional.of(nome + " está sem estoque. Remova-o do carrinho.");
        }
        if (item.getQuantidade() > apresentacao.getEstoque()) {
            return Optional.of(nome + ": só restam " + apresentacao.getEstoque() + " unidades. Ajuste a quantidade no carrinho.");
        }
        if (item.getQuantidade() > apresentacao.getLimitePorPedido()) {
            return Optional.of(nome + ": o limite é de " + apresentacao.getLimitePorPedido() + " unidades por pedido. Ajuste a quantidade no carrinho.");
        }
        return Optional.empty();
    }

    private Optional<Loja> lojaAtiva(Long lojaId) {
        return lojaId == null ? Optional.empty() : lojaRepository.findById(lojaId).filter(Loja::isAtiva);
    }

    private static String conferencia(List<ResumoCheckout.Item> itens, BigDecimal total, CheckoutForm form,
            EnderecoEntrega endereco) {
        StringBuilder texto = new StringBuilder();
        itens.forEach(i -> texto.append(i.apresentacaoId()).append(':').append(i.quantidade()).append(':')
                .append(i.preco().toPlainString()).append(';'));
        texto.append(total.toPlainString()).append(';').append(form.getTipoEntrega()).append(';')
                .append(form.getLojaId()).append(';').append(form.getEnderecoId()).append(';')
                .append(enderecoNaConferencia(endereco)).append(';')
                .append(form.getMetodo()).append(';').append(form.parcelasEfetivas());
        return DigestUtils.md5DigestAsHex(texto.toString().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * O endereço inteiro entra na conferência, e não só o CEP: se o endereço for editado em outra aba (mesmo CEP, outra
     * rua) entre o resumo e a confirmação, a conferência muda e o pedido não sai para um endereço que a pessoa não viu.
     */
    private static String enderecoNaConferencia(EnderecoEntrega endereco) {
        if (endereco == null) {
            return "";
        }
        return String.join("|", String.valueOf(endereco.getDestinatario()), String.valueOf(endereco.getCep()),
                String.valueOf(endereco.getLogradouro()), String.valueOf(endereco.getNumero()),
                String.valueOf(endereco.getComplemento()), String.valueOf(endereco.getBairro()),
                String.valueOf(endereco.getCidade()), String.valueOf(endereco.getUf()));
    }

    private void reservarEstoque(Carrinho carrinho) {
        carrinho.getItens().stream()
                .sorted(Comparator.comparing((ItemCarrinho i) -> i.getApresentacao().getId()))
                .forEach(item -> {
                    Apresentacao apresentacao = item.getApresentacao();
                    int reservou = apresentacaoRepository.reservarEstoque(
                            apresentacao.getId(), item.getQuantidade(), apresentacao.getPreco());
                    if (reservou == 0) {
                        throw new CheckoutException(apresentacao.getProduto().getNome() + " "
                                + apresentacao.getDescricao() + " acabou de mudar ou de esgotar. Confira o resumo.");
                    }
                });
    }

    private Pedido novoPedido(Usuario usuario, Carrinho carrinho, ResumoCheckout resumo, UUID chave) {
        Pedido pedido = new Pedido();
        pedido.setUsuario(usuario);
        pedido.setChaveIdempotencia(chave);
        pedido.setTipoEntrega(resumo.tipoEntrega());
        pedido.setFrete(resumo.frete());
        pedido.setPrazoEntregaDias(resumo.prazoEntregaDias());
        pedido.setEnderecoEntrega(resumo.endereco());
        if (resumo.loja() != null) {
            pedido.setLojaRetirada(resumo.loja());
            pedido.setLocalRetirada(LocalRetirada.de(resumo.loja()));
        }
        pedido.setExpiraEm(Instant.now().plus(validadeReserva));
        for (ItemCarrinho origem : carrinho.getItens()) {
            Apresentacao apresentacao = origem.getApresentacao();
            ItemPedido item = new ItemPedido();
            item.setPedido(pedido);
            item.setApresentacao(apresentacao);
            item.setNomeProduto(apresentacao.getProduto().getNome());
            item.setDescricaoApresentacao(apresentacao.getDescricao());
            item.setSku(apresentacao.getSku());
            item.setPrecoUnitario(apresentacao.getPreco());
            item.setQuantidade(origem.getQuantidade());
            item.setExigeReceita(apresentacao.getProduto().isExigeReceita());
            pedido.getItens().add(item);
        }
        pedido.recalcularTotais();
        return pedido;
    }

    private static Pagamento novoPagamento(Pedido pedido, CheckoutForm form, UUID chave) {
        Pagamento pagamento = new Pagamento();
        pagamento.setPedido(pedido);
        pagamento.setMetodo(form.getMetodo());
        pagamento.setParcelas(form.parcelasEfetivas());
        pagamento.setValor(pedido.getTotal());
        pagamento.setChaveIdempotencia(chave);
        return pagamento;
    }

    private static HistoricoStatusPedido historicoInicial(Pedido pedido) {
        HistoricoStatusPedido historico = new HistoricoStatusPedido();
        historico.setPedido(pedido);
        historico.setStatus(StatusPedido.AGUARDANDO_PAGAMENTO);
        historico.setObservacao("Pedido registrado e estoque reservado.");
        return historico;
    }
}
