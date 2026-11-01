package com.farmacia.farmaciaonline.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.farmacia.farmaciaonline.model.Apresentacao;
import com.farmacia.farmaciaonline.model.Carrinho;
import com.farmacia.farmaciaonline.model.ImagemProduto;
import com.farmacia.farmaciaonline.model.ItemCarrinho;
import com.farmacia.farmaciaonline.model.Produto;
import com.farmacia.farmaciaonline.repository.ApresentacaoRepository;
import com.farmacia.farmaciaonline.repository.CarrinhoRepository;
import com.farmacia.farmaciaonline.repository.UsuarioRepository;
import com.farmacia.farmaciaonline.service.ResumoCarrinho.LinhaCarrinho;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CarrinhoService {
    private final CarrinhoRepository carrinhoRepository;
    private final ApresentacaoRepository apresentacaoRepository;
    private final UsuarioRepository usuarioRepository;

    @Transactional(readOnly = true)
    public ResumoCarrinho resumo(DonoCarrinho dono) {
        if (dono == null) {
            return ResumoCarrinho.vazio();
        }
        return buscar(dono).map(this::montarResumo).orElseGet(ResumoCarrinho::vazio);
    }

    /** Unidades no carrinho do dono (o número do cabeçalho). Sem carrinho ou sem dono, zero. */
    @Transactional(readOnly = true)
    public int contarUnidades(DonoCarrinho dono) {
        if (dono == null) {
            return 0;
        }
        return buscar(dono)
                .map(carrinho -> carrinho.getItens().stream().mapToInt(ItemCarrinho::getQuantidade).sum())
                .orElse(0);
    }

    @Transactional
    public List<String> adicionar(DonoCarrinho dono, Long apresentacaoId, int quantidade) {
        if (quantidade < 1) {
            throw new CarrinhoException("Informe uma quantidade maior que zero.");
        }
        Apresentacao apresentacao = apresentacaoRepository.findById(apresentacaoId)
                .orElseThrow(() -> new CarrinhoException("Este produto não foi encontrado."));
        exigirCompravel(apresentacao);

        Carrinho carrinho = obterOuCriar(dono);
        Optional<ItemCarrinho> existente = itemDaApresentacao(carrinho, apresentacao.getId());
        int atual = existente.map(ItemCarrinho::getQuantidade).orElse(0);
        int maximo = maximoPermitido(apresentacao);

        if (atual >= maximo) {
            throw new CarrinhoException(atual > 0
                    ? "Você já tem no carrinho a quantidade máxima possível deste item (" + maximo + ")."
                    : "Não há unidades suficientes deste item para adicionar.");
        }

        List<String> avisos = new ArrayList<>();
        long desejada = (long) atual + quantidade;
        int nova = (int) Math.min(desejada, maximo);
        if (nova < desejada) {
            avisos.add(mensagemDeAjuste(apresentacao, nova));
        }

        ItemCarrinho item = existente.orElseGet(() -> novoItem(carrinho, apresentacao));
        avisarMudancaDePreco(item, apresentacao, avisos);
        item.setQuantidade(nova);
        item.setPrecoVisto(apresentacao.getPreco());
        return avisos;
    }

    @Transactional
    public List<String> alterarQuantidade(DonoCarrinho dono, Long itemId, int quantidade) {
        if (quantidade < 1) {
            throw new CarrinhoException("Para tirar o item do carrinho, use a opção Remover.");
        }
        ItemCarrinho item = itemDoDono(dono, itemId);
        Apresentacao apresentacao = item.getApresentacao();
        exigirCompravel(apresentacao);

        List<String> avisos = new ArrayList<>();
        int maximo = maximoPermitido(apresentacao);
        int nova = Math.min(quantidade, maximo);
        if (nova < quantidade) {
            avisos.add(mensagemDeAjuste(apresentacao, nova));
        }
        avisarMudancaDePreco(item, apresentacao, avisos);
        item.setQuantidade(nova);
        item.setPrecoVisto(apresentacao.getPreco());
        return avisos;
    }

    @Transactional
    public void remover(DonoCarrinho dono, Long itemId) {
        ItemCarrinho item = itemDoDono(dono, itemId);
        item.getCarrinho().getItens().remove(item);
    }

    @Transactional
    public void esvaziar(DonoCarrinho dono) {
        buscar(dono).ifPresent(carrinho -> carrinho.getItens().clear());
    }

    @Transactional
    public void aceitarPreco(DonoCarrinho dono, Long itemId) {
        ItemCarrinho item = itemDoDono(dono, itemId);
        item.setPrecoVisto(item.getApresentacao().getPreco());
    }

    @Transactional
    public List<String> mesclar(UUID token, Long usuarioId) {
        Optional<Carrinho> encontrado = carrinhoRepository.findByToken(token);
        if (encontrado.isEmpty()) {
            return List.of();
        }
        Carrinho visitante = encontrado.get();
        List<String> avisos = new ArrayList<>();

        if (!visitante.getItens().isEmpty()) {
            Carrinho destino = obterOuCriar(DonoCarrinho.deUsuario(usuarioId));
            for (ItemCarrinho doVisitante : new ArrayList<>(visitante.getItens())) {
                Apresentacao apresentacao = doVisitante.getApresentacao();
                Optional<ItemCarrinho> doUsuario = itemDaApresentacao(destino, apresentacao.getId());

                long soma = (long) doUsuario.map(ItemCarrinho::getQuantidade).orElse(0) + doVisitante.getQuantidade();
                int teto = tetoNaMesclagem(apresentacao);
                int quantidadeFinal = (int) Math.min(soma, teto);
                if (quantidadeFinal < soma) {
                    avisos.add(mensagemDeAjuste(apresentacao, quantidadeFinal));
                }

                if (doUsuario.isPresent()) {
                    doUsuario.get().setQuantidade(quantidadeFinal);
                } else {
                    ItemCarrinho novo = new ItemCarrinho();
                    novo.setCarrinho(destino);
                    novo.setApresentacao(apresentacao);
                    novo.setQuantidade(quantidadeFinal);
                    novo.setPrecoVisto(doVisitante.getPrecoVisto());
                    destino.getItens().add(novo);
                }
            }
        }
        carrinhoRepository.delete(visitante);
        return avisos;
    }

    private Optional<Carrinho> buscar(DonoCarrinho dono) {
        return dono.isVisitante()
                ? carrinhoRepository.findByToken(dono.token())
                : carrinhoRepository.findByUsuarioId(dono.usuarioId());
    }

    private Carrinho obterOuCriar(DonoCarrinho dono) {
        return buscar(dono).orElseGet(() -> {
            Carrinho novo = new Carrinho();
            if (dono.isVisitante()) {
                novo.setToken(dono.token());
            } else {
                novo.setUsuario(usuarioRepository.getReferenceById(dono.usuarioId()));
            }
            return carrinhoRepository.save(novo);
        });
    }

    private ItemCarrinho itemDoDono(DonoCarrinho dono, Long itemId) {
        if (dono == null || itemId == null) {
            throw new CarrinhoException("Este item não está no seu carrinho.");
        }
        return buscar(dono)
                .flatMap(carrinho -> carrinho.getItens().stream()
                        .filter(item -> Objects.equals(item.getId(), itemId))
                        .findFirst())
                .orElseThrow(() -> new CarrinhoException("Este item não está no seu carrinho."));
    }

    private static Optional<ItemCarrinho> itemDaApresentacao(Carrinho carrinho, Long apresentacaoId) {
        return carrinho.getItens().stream()
                .filter(item -> Objects.equals(item.getApresentacao().getId(), apresentacaoId))
                .findFirst();
    }

    private static ItemCarrinho novoItem(Carrinho carrinho, Apresentacao apresentacao) {
        ItemCarrinho item = new ItemCarrinho();
        item.setCarrinho(carrinho);
        item.setApresentacao(apresentacao);
        item.setPrecoVisto(apresentacao.getPreco());
        carrinho.getItens().add(item);
        return item;
    }

    private static boolean produtoAtivo(Apresentacao apresentacao) {
        return apresentacao.getProduto().isAtivo();
    }

    private static void exigirCompravel(Apresentacao apresentacao) {
        if (!produtoAtivo(apresentacao) || !apresentacao.isAtiva()) {
            throw new CarrinhoException("Este produto não está mais disponível.");
        }
        if (apresentacao.getEstoque() <= 0) {
            throw new CarrinhoException("Este produto está sem estoque no momento.");
        }
    }

    private static int maximoPermitido(Apresentacao apresentacao) {
        if (!produtoAtivo(apresentacao) || !apresentacao.isAtiva()) {
            return 0;
        }
        return Math.max(0, Math.min(apresentacao.getEstoque(), apresentacao.getLimitePorPedido()));
    }

    private static int tetoNaMesclagem(Apresentacao apresentacao) {
        int maximo = maximoPermitido(apresentacao);
        return maximo > 0 ? maximo : apresentacao.getLimitePorPedido();
    }

    private static String mensagemDeAjuste(Apresentacao apresentacao, int quantidadeFinal) {
        String nome = nomeCompleto(apresentacao);
        boolean limitadoPeloEstoque = maximoPermitido(apresentacao) > 0
                && apresentacao.getEstoque() < apresentacao.getLimitePorPedido();
        if (limitadoPeloEstoque) {
            return "A quantidade de " + nome + " foi ajustada para " + quantidadeFinal
                    + ": é o que temos em estoque.";
        }
        return "A quantidade de " + nome + " foi ajustada para " + quantidadeFinal
                + ": o limite é de " + apresentacao.getLimitePorPedido() + " por pedido.";
    }

    private static void avisarMudancaDePreco(ItemCarrinho item, Apresentacao apresentacao, List<String> avisos) {
        if (item.getPrecoVisto() != null && item.getPrecoVisto().compareTo(apresentacao.getPreco()) != 0) {
            avisos.add("O preço de " + nomeCompleto(apresentacao) + " mudou de " + moeda(item.getPrecoVisto())
                    + " para " + moeda(apresentacao.getPreco()) + ".");
        }
    }

    private static String nomeCompleto(Apresentacao apresentacao) {
        return apresentacao.getProduto().getNome() + " " + apresentacao.getDescricao();
    }

    private static String moeda(BigDecimal valor) {
        return NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR")).format(valor);
    }

    private ResumoCarrinho montarResumo(Carrinho carrinho) {
        List<LinhaCarrinho> linhas = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        int unidades = 0;

        for (ItemCarrinho item : carrinho.getItens()) {
            Apresentacao apresentacao = item.getApresentacao();
            Produto produto = apresentacao.getProduto();
            int quantidade = item.getQuantidade();
            BigDecimal precoAtual = apresentacao.getPreco();
            BigDecimal subtotal = precoAtual.multiply(BigDecimal.valueOf(quantidade)).setScale(2, RoundingMode.HALF_UP);
            String problema = problemaDoItem(apresentacao, quantidade);

            if (problema == null) {
                total = total.add(subtotal);
                unidades += quantidade;
            }
            linhas.add(new LinhaCarrinho(
                    item.getId(),
                    produto.getId(),
                    apresentacao.getId(),
                    produto.getNome(),
                    apresentacao.getDescricao(),
                    imagemDe(produto, apresentacao),
                    quantidade,
                    precoAtual,
                    item.getPrecoVisto(),
                    item.getPrecoVisto().compareTo(precoAtual) != 0,
                    subtotal,
                    problema,
                    maximoPermitido(apresentacao)));
        }
        return new ResumoCarrinho(linhas, total.setScale(2, RoundingMode.HALF_UP), unidades);
    }

    private static String problemaDoItem(Apresentacao apresentacao, int quantidade) {
        if (!produtoAtivo(apresentacao) || !apresentacao.isAtiva()) {
            return "Este produto não está mais disponível. Remova-o para continuar.";
        }
        int estoque = apresentacao.getEstoque();
        if (estoque <= 0) {
            return "Sem estoque no momento. Remova-o para continuar.";
        }
        if (quantidade > estoque) {
            return "Estoque insuficiente: " + (estoque == 1 ? "resta 1 unidade." : "restam " + estoque + " unidades.");
        }
        if (quantidade > apresentacao.getLimitePorPedido()) {
            return "O limite é de " + apresentacao.getLimitePorPedido() + " unidades por pedido.";
        }
        return null;
    }

    private static String imagemDe(Produto produto, Apresentacao apresentacao) {
        return produto.getImagens().stream()
                .filter(imagem -> imagem.getApresentacao() != null
                        && Objects.equals(imagem.getApresentacao().getId(), apresentacao.getId())
                        && !Produto.IMAGEM_PADRAO.equals(imagem.getArquivo()))
                .map(ImagemProduto::getArquivo)
                .findFirst()
                .orElseGet(produto::getUrlCapa);
    }
}
