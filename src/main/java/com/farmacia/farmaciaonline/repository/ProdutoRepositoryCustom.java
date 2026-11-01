package com.farmacia.farmaciaonline.repository;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.farmacia.farmaciaonline.model.Produto;

public interface ProdutoRepositoryCustom {

    Page<Produto> buscarCatalogo(String termo, List<Long> categoriaIds, BigDecimal precoMinimo,
            BigDecimal precoMaximo, boolean somenteDisponiveis, OrdenacaoCatalogo ordenacao, Pageable pageable);
}
