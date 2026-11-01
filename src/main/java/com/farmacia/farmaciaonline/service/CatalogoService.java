package com.farmacia.farmaciaonline.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.farmacia.farmaciaonline.model.Categoria;
import com.farmacia.farmaciaonline.model.Produto;
import com.farmacia.farmaciaonline.repository.CategoriaRepository;
import com.farmacia.farmaciaonline.repository.OrdenacaoCatalogo;
import com.farmacia.farmaciaonline.repository.ProdutoRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CatalogoService {

    private final ProdutoRepository produtoRepository;
    private final CategoriaRepository categoriaRepository;

    public Page<Produto> buscar(String termo, Long categoriaId, BigDecimal precoMinimo, BigDecimal precoMaximo,
            boolean somenteDisponiveis, OrdenacaoCatalogo ordenacao, Pageable pageable) {
        List<Long> categoriaIds = new ArrayList<>();
        if (categoriaId != null) {
            Categoria categoria = categoriaRepository.findByIdAndAtivaTrue(categoriaId).orElse(null);
            if (categoria == null) {
                return Page.empty(pageable);
            }
            categoriaIds.add(categoria.getId());
            if (categoria.getCategoriaPai() == null) {
                categoriaRepository.findByCategoriaPaiIdAndAtivaTrueOrderByOrdemAsc(categoriaId)
                        .stream()
                        .map(Categoria::getId)
                        .forEach(categoriaIds::add);
            }
        }

        String termoNormalizado = termo == null || termo.isBlank() ? null : termo.trim();
        return produtoRepository.buscarCatalogo(termoNormalizado, categoriaIds, precoMinimo, precoMaximo,
                somenteDisponiveis, ordenacao, pageable);
    }
}
