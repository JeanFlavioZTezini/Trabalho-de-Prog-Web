package com.farmacia.farmaciaonline.service;

import java.math.BigDecimal;
import java.util.List;

public record DetalheProduto(
        Long id,
        String nome,
        String marca,
        String principioAtivo,
        String formaFarmaceutica,
        String acao,
        String viaAdministracao,
        String publicoAlvo,
        String registroMs,
        String bulaUrl,
        boolean bulaUrlInvalida,
        String tarja,
        boolean exigeReceita,
        List<String> indicacoes,
        List<OpcaoApresentacao> apresentacoes,
        OpcaoApresentacao apresentacaoSelecionada,
        List<ImagemDetalhe> imagens,
        boolean disponivel,
        int quantidadeMaxima) {

    public record OpcaoApresentacao(
            Long id,
            String descricao,
            String ean,
            BigDecimal preco,
            int estoque,
            int limitePorPedido,
            boolean disponivel) {
    }

    public record ImagemDetalhe(String arquivo, String alt) {
    }
}
