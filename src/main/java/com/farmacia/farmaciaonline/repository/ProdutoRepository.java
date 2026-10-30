package com.farmacia.farmaciaonline.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    /** Lista administrativa (UC12): nome, marca, princípio ativo, SKU ou EAN; {@code termo} já vem com %...%. */
    @Query("""
            select p from Produto p
            where p.ativo in :estados
              and (lower(p.nome) like :termo
                or lower(p.marca) like :termo
                or lower(coalesce(p.principioAtivo, '')) like :termo
                or exists (select a.id from Apresentacao a
                           where a.produto = p and (lower(a.sku) like :termo or a.ean like :termo)))
            """)
    Page<Produto> buscarAdmin(@Param("termo") String termo, @Param("estados") List<Boolean> estados,
            Pageable pageable);
}
