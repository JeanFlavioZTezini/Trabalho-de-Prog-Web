package com.farmacia.farmaciaonline.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.farmacia.farmaciaonline.model.Produto;

@Repository
public interface ProdutoRepository extends JpaRepository<Produto, Long> {

    /** Produtos ativos (exclusão lógica), paginados. A busca com filtros é da Fase 2 (UC01). */
    Page<Produto> findByAtivoTrue(Pageable pageable);

    List<Produto> findTop8ByAtivoTrueOrderByQtdVendidaDesc();

    List<Produto> findTop8ByAtivoTrueOrderByVisualizacoesDesc();

    /** Produtos vinculados a uma categoria (UC13: reatribuição antes de excluir). */
    List<Produto> findByCategoriasId(Long categoriaId);
}
