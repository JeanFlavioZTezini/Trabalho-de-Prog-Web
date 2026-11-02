package com.farmacia.farmaciaonline.repository;

import java.math.BigDecimal;
import java.util.List;

import com.farmacia.farmaciaonline.model.enums.TipoProduto;

/**
 * Filtros do catálogo público. {@code categoriaId} é o que veio da tela; o serviço o expande em
 * {@code categoriaIds} (categoria + filhas). Os demais campos nulos/vazios não filtram.
 */
public record FiltroCatalogo(String termo, Long categoriaId, List<Long> categoriaIds, BigDecimal precoMinimo,
        BigDecimal precoMaximo, boolean somenteDisponiveis, String marca, String principioAtivo,
        TipoProduto tipo, String forma, Boolean exigeReceita) {

    public FiltroCatalogo {
        categoriaIds = categoriaIds == null ? List.of() : List.copyOf(categoriaIds);
    }

    public FiltroCatalogo comCategorias(List<Long> ids) {
        return new FiltroCatalogo(termo, categoriaId, ids, precoMinimo, precoMaximo, somenteDisponiveis,
                marca, principioAtivo, tipo, forma, exigeReceita);
    }

    public FiltroCatalogo comTermo(String novoTermo) {
        return new FiltroCatalogo(novoTermo, categoriaId, categoriaIds, precoMinimo, precoMaximo,
                somenteDisponiveis, marca, principioAtivo, tipo, forma, exigeReceita);
    }
}
