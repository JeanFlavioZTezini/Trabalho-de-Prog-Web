package com.farmacia.farmaciaonline.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.farmacia.farmaciaonline.model.Produto;

@Repository
public interface ProdutoRepository extends JpaRepository<Produto, Long>, ProdutoRepositoryCustom {

    /** Produtos ativos (exclusão lógica), paginados. A busca com filtros é da Fase 2 (UC01). */
    Page<Produto> findByAtivoTrue(Pageable pageable);

    List<Produto> findTop8ByAtivoTrueOrderByQtdVendidaDesc();

    List<Produto> findTop8ByAtivoTrueOrderByVisualizacoesDesc();

    /** Produtos vinculados a uma categoria (UC13: reatribuição antes de excluir). */
    List<Produto> findByCategoriasId(Long categoriaId);

    /** Soma ao "Mais comprados" (negativo ao cancelar um pedido pago). Direto no banco: compras simultâneas não se perdem. */
    @Modifying
    @Query("update Produto p set p.qtdVendida = p.qtdVendida + :quantidade where p.id = :id")
    int somarVendidos(@Param("id") Long id, @Param("quantidade") long quantidade);

    /** Incrementa visualizações sem perder atualizações simultâneas de outras visitas. */
    @Modifying
    @Query("update Produto p set p.visualizacoes = p.visualizacoes + 1 where p.id = :id and p.ativo = true")
    int incrementarVisualizacoes(@Param("id") Long id);

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

    /** Opções dos filtros do catálogo: só produtos ativos, em ordem alfabética. */
    @Query("select distinct p.marca from Produto p where p.ativo = true order by p.marca")
    List<String> marcasAtivas();

    @Query("select distinct p.principioAtivo from Produto p where p.ativo = true and p.principioAtivo is not null order by p.principioAtivo")
    List<String> principiosAtivosAtivos();

    @Query("select distinct p.formaFarmaceutica from Produto p where p.ativo = true and p.formaFarmaceutica is not null order by p.formaFarmaceutica")
    List<String> formasAtivas();

    /** Palavra do catálogo (nome, marca, princípio ativo; sem acento) mais parecida com o token. "Você quis dizer". */
    @Query(value = """
            select w.palavra as palavra,
                   cast(similarity(w.palavra, unaccent(lower(:token))) as double precision) as similaridade
            from (select distinct unnest(regexp_split_to_array(
                         unaccent(lower(p.nome || ' ' || p.marca || ' ' || coalesce(p.principio_ativo, ''))),
                         '[^a-z0-9]+')) as palavra
                  from produto p where p.ativo) w
            where length(w.palavra) >= 3
            order by similarity(w.palavra, unaccent(lower(:token))) desc, w.palavra
            limit 1
            """, nativeQuery = true)
    java.util.Optional<PalavraParecida> palavraMaisParecida(@Param("token") String token);

    interface PalavraParecida {
        String getPalavra();

        Double getSimilaridade();
    }
}
