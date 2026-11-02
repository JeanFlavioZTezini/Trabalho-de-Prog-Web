package com.farmacia.farmaciaonline.service;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.farmacia.farmaciaonline.model.Apresentacao;
import com.farmacia.farmaciaonline.model.Categoria;
import com.farmacia.farmaciaonline.model.ImagemProduto;
import com.farmacia.farmaciaonline.model.Produto;
import com.farmacia.farmaciaonline.model.enums.Tarja;
import com.farmacia.farmaciaonline.repository.ProdutoRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DetalheProdutoService {

    private final ProdutoRepository produtos;

    /**
     * Carrega um produto público e incrementa visualizações uma vez por abertura válida da página.
     * Uma apresentação solicitada só é aceita se estiver ativa e pertencer ao produto.
     */
    @Transactional
    public Optional<DetalheProduto> buscar(Long produtoId, Long apresentacaoId) {
        Optional<Produto> encontrado = produtos.findById(produtoId).filter(Produto::isAtivo);
        if (encontrado.isEmpty()) {
            return Optional.empty();
        }

        Produto produto = encontrado.get();
        List<Apresentacao> ativas = produto.getApresentacoes().stream()
                .filter(Apresentacao::isAtiva)
                .sorted(Comparator.comparingInt(Apresentacao::getOrdem)
                        .thenComparing(Apresentacao::getPreco)
                        .thenComparing(Apresentacao::getId))
                .toList();

        Apresentacao selecionada;
        if (apresentacaoId == null) {
            selecionada = ativas.stream().findFirst().orElse(null);
        } else {
            selecionada = ativas.stream()
                    .filter(apresentacao -> Objects.equals(apresentacao.getId(), apresentacaoId))
                    .findFirst()
                    .orElse(null);
            if (selecionada == null) {
                return Optional.empty();
            }
        }

        DetalheProduto detalhe = montarDetalhe(produto, ativas, selecionada);
        if (produtos.incrementarVisualizacoes(produtoId) != 1) {
            return Optional.empty();
        }
        return Optional.of(detalhe);
    }

    private static DetalheProduto montarDetalhe(Produto produto, List<Apresentacao> ativas,
            Apresentacao selecionada) {
        List<DetalheProduto.OpcaoApresentacao> opcoes = ativas.stream()
                .map(DetalheProdutoService::mapearApresentacao)
                .toList();
        DetalheProduto.OpcaoApresentacao opcaoSelecionada = selecionada == null
                ? null
                : mapearApresentacao(selecionada);
        int quantidadeMaxima = selecionada == null || !selecionada.isDisponivel()
                ? 0
                : Math.min(selecionada.getEstoque(), selecionada.getLimitePorPedido());
        LinkBula bula = linkBulaSeguro(produto.getBulaUrl());

        return new DetalheProduto(
                produto.getId(),
                produto.getNome(),
                produto.getMarca(),
                produto.getPrincipioAtivo(),
                produto.getFormaFarmaceutica(),
                produto.getAcao(),
                produto.getViaAdministracao(),
                produto.getPublicoAlvo(),
                produto.getRegistroMs(),
                bula.url(),
                bula.invalida(),
                rotuloTarja(produto.getTarja()),
                produto.isExigeReceita(),
                topicos(produto.getIndicacao()),
                opcoes,
                opcaoSelecionada,
                imagens(produto, selecionada),
                selecionada != null && selecionada.isDisponivel(),
                quantidadeMaxima,
                migalhas(produto));
    }

    /**
     * Caminho da raiz até a categoria ativa mais funda do produto (empate: menor ordem, depois menor id).
     * Categoria com algum ancestral inativo fica de fora; sem nenhuma, as migalhas vão direto para Produtos.
     */
    private static List<DetalheProduto.CategoriaMigalha> migalhas(Produto produto) {
        return produto.getCategorias().stream()
                .map(DetalheProdutoService::caminhoAtivo)
                .filter(caminho -> !caminho.isEmpty())
                .max(Comparator.<List<Categoria>>comparingInt(List::size)
                        .thenComparing(caminho -> caminho.get(caminho.size() - 1).getOrdem(), Comparator.reverseOrder())
                        .thenComparing(caminho -> caminho.get(caminho.size() - 1).getId(), Comparator.reverseOrder()))
                .map(caminho -> caminho.stream()
                        .map(categoria -> new DetalheProduto.CategoriaMigalha(categoria.getId(), categoria.getNome()))
                        .toList())
                .orElse(List.of());
    }

    private static List<Categoria> caminhoAtivo(Categoria categoria) {
        List<Categoria> caminho = new ArrayList<>();
        for (Categoria atual = categoria; atual != null; atual = atual.getCategoriaPai()) {
            if (!atual.isAtiva() || caminho.contains(atual)) {
                return List.of();
            }
            caminho.add(0, atual);
        }
        return caminho;
    }

    private static DetalheProduto.OpcaoApresentacao mapearApresentacao(Apresentacao apresentacao) {
        return new DetalheProduto.OpcaoApresentacao(
                apresentacao.getId(),
                apresentacao.getDescricao(),
                apresentacao.getEan(),
                apresentacao.getPreco(),
                apresentacao.getEstoque(),
                apresentacao.getLimitePorPedido(),
                apresentacao.isDisponivel());
    }

    private static List<DetalheProduto.ImagemDetalhe> imagens(Produto produto, Apresentacao selecionada) {
        List<ImagemProduto> imagens = produto.getImagens().stream()
                .filter(imagem -> imagem.getApresentacao() == null
                        || (selecionada != null
                                && Objects.equals(imagem.getApresentacao().getId(), selecionada.getId())))
                .sorted(Comparator
                        .comparing((ImagemProduto imagem) -> imagem.getApresentacao() == null)
                        .thenComparingInt(ImagemProduto::getOrdem))
                .toList();

        List<DetalheProduto.ImagemDetalhe> resultado = imagens.stream()
                .filter(imagem -> !Produto.IMAGEM_PADRAO.equals(imagem.getArquivo()))
                .map(imagem -> new DetalheProduto.ImagemDetalhe(
                        imagem.getArquivo(),
                        altImagem(produto, selecionada, imagem)))
                .toList();
        if (!resultado.isEmpty()) {
            return resultado;
        }
        return List.of(new DetalheProduto.ImagemDetalhe(Produto.IMAGEM_PADRAO, produto.getNome()));
    }

    private static String altImagem(Produto produto, Apresentacao selecionada, ImagemProduto imagem) {
        if (selecionada != null && imagem.getApresentacao() != null) {
            return produto.getNome() + " — " + selecionada.getDescricao();
        }
        return produto.getNome();
    }

    private static List<String> topicos(String indicacao) {
        if (indicacao == null || indicacao.isBlank()) {
            return List.of();
        }
        return indicacao.lines()
                .map(String::strip)
                .filter(linha -> !linha.isEmpty())
                .map(linha -> linha.startsWith("-") ? linha.substring(1).strip() : linha)
                .filter(linha -> !linha.isEmpty())
                .toList();
    }

    private static String rotuloTarja(Tarja tarja) {
        if (tarja == null || tarja == Tarja.SEM_TARJA) {
            return "Sem tarja";
        }
        return tarja == Tarja.VERMELHA ? "Tarja vermelha" : "Tarja preta";
    }

    private static LinkBula linkBulaSeguro(String bulaUrl) {
        if (bulaUrl == null || bulaUrl.isBlank()) {
            return new LinkBula(null, false);
        }
        try {
            URI uri = new URI(bulaUrl.strip());
            String esquema = uri.getScheme();
            if (uri.isAbsolute() && ("https".equalsIgnoreCase(esquema) || "http".equalsIgnoreCase(esquema))) {
                return new LinkBula(uri.toASCIIString(), false);
            }
            return new LinkBula(null, true);
        } catch (URISyntaxException invalida) {
            return new LinkBula(null, true);
        }
    }

    private record LinkBula(String url, boolean invalida) {
    }
}
