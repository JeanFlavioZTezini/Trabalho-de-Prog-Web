package com.farmacia.farmaciaonline.repository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import com.farmacia.farmaciaonline.model.Apresentacao;
import com.farmacia.farmaciaonline.model.Categoria;
import com.farmacia.farmaciaonline.model.Produto;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class ProdutoRepositoryCustomImpl implements ProdutoRepositoryCustom {

    private final EntityManager entityManager;

    @Override
    public Page<Produto> buscarCatalogo(String termo, List<Long> categoriaIds, BigDecimal precoMinimo,
            BigDecimal precoMaximo, boolean somenteDisponiveis, OrdenacaoCatalogo ordenacao, Pageable pageable) {
        CriteriaBuilder builder = entityManager.getCriteriaBuilder();

        CriteriaQuery<Produto> consulta = builder.createQuery(Produto.class);
        Root<Produto> produto = consulta.from(Produto.class);
        consulta.select(produto).where(predicados(builder, consulta, produto, termo, categoriaIds,
                precoMinimo, precoMaximo, somenteDisponiveis).toArray(Predicate[]::new));
        ordenar(builder, consulta, produto, ordenacao);

        TypedQuery<Produto> query = entityManager.createQuery(consulta);
        query.setFirstResult(Math.toIntExact(pageable.getOffset()));
        query.setMaxResults(pageable.getPageSize());
        List<Produto> produtos = query.getResultList();

        CriteriaQuery<Long> consultaContagem = builder.createQuery(Long.class);
        Root<Produto> produtoContagem = consultaContagem.from(Produto.class);
        consultaContagem.select(builder.countDistinct(produtoContagem.get("id")))
                .where(predicados(builder, consultaContagem, produtoContagem, termo, categoriaIds,
                        precoMinimo, precoMaximo, somenteDisponiveis).toArray(Predicate[]::new));
        long total = entityManager.createQuery(consultaContagem).getSingleResult();

        return new PageImpl<>(produtos, pageable, total);
    }

    private List<Predicate> predicados(CriteriaBuilder builder, CriteriaQuery<?> consulta, Root<Produto> produto,
            String termo, List<Long> categoriaIds, BigDecimal precoMinimo, BigDecimal precoMaximo,
            boolean somenteDisponiveis) {
        List<Predicate> predicados = new ArrayList<>();
        predicados.add(builder.isTrue(produto.get("ativo")));

        if (termo != null && !termo.isBlank()) {
            String termoEscapado = escaparLike(termo.toLowerCase(java.util.Locale.ROOT));
            String padrao = "%" + termoEscapado + "%";
            Predicate texto = builder.or(
                    builder.like(builder.lower(produto.get("nome")), padrao, '\\'),
                    builder.like(builder.lower(produto.get("marca")), padrao, '\\'),
                    builder.like(builder.lower(builder.coalesce(produto.<String>get("principioAtivo"), "")),
                            padrao, '\\'),
                    builder.like(builder.lower(builder.coalesce(produto.<String>get("indicacao"), "")),
                            padrao, '\\'),
                    correspondeEan(builder, consulta, produto, padrao));
            predicados.add(texto);
        }

        if (!categoriaIds.isEmpty()) {
            Subquery<Long> categoriaEncontrada = consulta.subquery(Long.class);
            Root<Produto> produtoCorrelacionado = categoriaEncontrada.correlate(produto);
            Join<Produto, Categoria> categoria = produtoCorrelacionado.join("categorias", JoinType.INNER);
            categoriaEncontrada.select(categoria.get("id")).where(categoria.get("id").in(categoriaIds));
            predicados.add(builder.exists(categoriaEncontrada));
        }

        if (precoMinimo != null || precoMaximo != null) {
            Subquery<BigDecimal> precoReferencia = precoReferenciaAtivo(builder, consulta, produto);
            if (precoMinimo != null) {
                predicados.add(builder.greaterThanOrEqualTo(precoReferencia, precoMinimo));
            }
            if (precoMaximo != null) {
                predicados.add(builder.lessThanOrEqualTo(precoReferencia, precoMaximo));
            }
        }

        if (somenteDisponiveis) {
            predicados.add(apresentacaoAtivaExiste(builder, consulta, produto));
        }
        return predicados;
    }

    private Predicate correspondeEan(CriteriaBuilder builder, CriteriaQuery<?> consulta, Root<Produto> produto,
            String padrao) {
        Subquery<Long> ean = consulta.subquery(Long.class);
        Root<Apresentacao> apresentacao = ean.from(Apresentacao.class);
        ean.select(apresentacao.get("id")).where(
                builder.equal(apresentacao.get("produto"), produto),
                builder.like(builder.lower(apresentacao.get("ean")), padrao, '\\'));
        return builder.exists(ean);
    }

    private Predicate apresentacaoAtivaExiste(CriteriaBuilder builder, CriteriaQuery<?> consulta,
            Root<Produto> produto) {
        Subquery<Long> apresentacaoExiste = consulta.subquery(Long.class);
        Root<Apresentacao> apresentacao = apresentacaoExiste.from(Apresentacao.class);
        List<Predicate> condicoes = new ArrayList<>();
        condicoes.add(builder.equal(apresentacao.get("produto"), produto));
        condicoes.add(builder.isTrue(apresentacao.get("ativa")));
        condicoes.add(builder.greaterThan(apresentacao.get("estoque"), 0));
        apresentacaoExiste.select(apresentacao.get("id")).where(condicoes.toArray(Predicate[]::new));
        return builder.exists(apresentacaoExiste);
    }

    private void ordenar(CriteriaBuilder builder, CriteriaQuery<?> consulta, Root<Produto> produto,
            OrdenacaoCatalogo ordenacao) {
        if (ordenacao == OrdenacaoCatalogo.NOME || ordenacao == OrdenacaoCatalogo.NOME_DESC) {
            var nome = builder.lower(produto.<String>get("nome"));
            consulta.orderBy(ordenacao == OrdenacaoCatalogo.NOME ? builder.asc(nome) : builder.desc(nome));
            return;
        }

        Subquery<BigDecimal> precoReferencia = precoReferenciaAtivo(builder, consulta, produto);

        var semPreco = builder.<Integer>selectCase().when(precoReferencia.isNull(), 1).otherwise(0);
        var preco = ordenacao == OrdenacaoCatalogo.PRECO
                ? builder.asc(precoReferencia)
                : builder.desc(precoReferencia);
        consulta.orderBy(builder.asc(semPreco), preco, builder.asc(produto.get("id")));
    }

    private Subquery<BigDecimal> precoReferenciaAtivo(CriteriaBuilder builder, CriteriaQuery<?> consulta,
            Root<Produto> produto) {
        Subquery<Integer> menorOrdem = consulta.subquery(Integer.class);
        Root<Apresentacao> candidataOrdem = menorOrdem.from(Apresentacao.class);
        menorOrdem.select(builder.min(candidataOrdem.<Integer>get("ordem"))).where(
                builder.equal(candidataOrdem.get("produto"), produto),
                builder.isTrue(candidataOrdem.get("ativa")));

        Subquery<BigDecimal> precoReferencia = consulta.subquery(BigDecimal.class);
        Root<Apresentacao> candidataPreco = precoReferencia.from(Apresentacao.class);
        precoReferencia.select(builder.min(candidataPreco.<BigDecimal>get("preco"))).where(
                builder.equal(candidataPreco.get("produto"), produto),
                builder.isTrue(candidataPreco.get("ativa")),
                builder.equal(candidataPreco.get("ordem"), menorOrdem));
        return precoReferencia;
    }

    private static String escaparLike(String valor) {
        return valor.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
