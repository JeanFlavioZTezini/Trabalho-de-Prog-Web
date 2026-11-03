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
import jakarta.persistence.criteria.Expression;
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
        return buscarCatalogo(new FiltroCatalogo(termo, null, categoriaIds, precoMinimo, precoMaximo,
                somenteDisponiveis, null, null, null, null, null), ordenacao, pageable);
    }

    @Override
    public Page<Produto> buscarCatalogo(FiltroCatalogo filtro, OrdenacaoCatalogo ordenacao, Pageable pageable) {
        CriteriaBuilder builder = entityManager.getCriteriaBuilder();

        CriteriaQuery<Produto> consulta = builder.createQuery(Produto.class);
        Root<Produto> produto = consulta.from(Produto.class);
        consulta.select(produto).where(predicados(builder, consulta, produto, filtro).toArray(Predicate[]::new));
        ordenar(builder, consulta, produto, ordenacao, filtro.termo());

        TypedQuery<Produto> query = entityManager.createQuery(consulta);
        query.setFirstResult(Math.toIntExact(pageable.getOffset()));
        query.setMaxResults(pageable.getPageSize());
        List<Produto> produtos = query.getResultList();

        CriteriaQuery<Long> consultaContagem = builder.createQuery(Long.class);
        Root<Produto> produtoContagem = consultaContagem.from(Produto.class);
        consultaContagem.select(builder.countDistinct(produtoContagem.get("id")))
                .where(predicados(builder, consultaContagem, produtoContagem, filtro).toArray(Predicate[]::new));
        long total = entityManager.createQuery(consultaContagem).getSingleResult();

        return new PageImpl<>(produtos, pageable, total);
    }

    /** lower + unaccent: "Paracetamól" e "paracetamol" ficam iguais. */
    private Expression<String> normalizado(CriteriaBuilder builder, Expression<String> campo) {
        return builder.function("unaccent", String.class, builder.lower(campo));
    }

    private Expression<String> semAcento(CriteriaBuilder builder, String texto) {
        return builder.function("unaccent", String.class, builder.literal(texto));
    }

    private Expression<String> padraoContem(CriteriaBuilder builder, String termo) {
        return semAcento(builder, "%" + escaparLike(termo.toLowerCase(java.util.Locale.ROOT)) + "%");
    }

    private Predicate igualSemAcento(CriteriaBuilder builder, Expression<String> campo, String valor) {
        return builder.equal(normalizado(builder, campo),
                semAcento(builder, valor.trim().toLowerCase(java.util.Locale.ROOT)));
    }

    private static boolean preenchido(String valor) {
        return valor != null && !valor.isBlank();
    }

    private List<Predicate> predicados(CriteriaBuilder builder, CriteriaQuery<?> consulta, Root<Produto> produto,
            FiltroCatalogo filtro) {
        List<Predicate> predicados = new ArrayList<>();
        predicados.add(builder.isTrue(produto.get("ativo")));

        if (preenchido(filtro.termo())) {
            Expression<String> padrao = padraoContem(builder, filtro.termo());
            Predicate texto = builder.or(
                    builder.like(normalizado(builder, produto.get("nome")), padrao, '\\'),
                    builder.like(normalizado(builder, produto.get("marca")), padrao, '\\'),
                    builder.like(normalizado(builder, builder.coalesce(produto.<String>get("principioAtivo"), "")),
                            padrao, '\\'),
                    builder.like(normalizado(builder, builder.coalesce(produto.<String>get("indicacao"), "")),
                            padrao, '\\'),
                    correspondeEan(builder, consulta, produto, padrao));
            predicados.add(texto);
        }

        if (!filtro.categoriaIds().isEmpty()) {
            Subquery<Long> categoriaEncontrada = consulta.subquery(Long.class);
            Root<Produto> produtoCorrelacionado = categoriaEncontrada.correlate(produto);
            Join<Produto, Categoria> categoria = produtoCorrelacionado.join("categorias", JoinType.INNER);
            categoriaEncontrada.select(categoria.get("id")).where(categoria.get("id").in(filtro.categoriaIds()));
            predicados.add(builder.exists(categoriaEncontrada));
        }

        if (filtro.precoMinimo() != null || filtro.precoMaximo() != null) {
            Subquery<BigDecimal> precoReferencia = precoReferenciaAtivo(builder, consulta, produto);
            if (filtro.precoMinimo() != null) {
                predicados.add(builder.greaterThanOrEqualTo(precoReferencia, filtro.precoMinimo()));
            }
            if (filtro.precoMaximo() != null) {
                predicados.add(builder.lessThanOrEqualTo(precoReferencia, filtro.precoMaximo()));
            }
        }

        if (filtro.somenteDisponiveis()) {
            predicados.add(apresentacaoAtivaExiste(builder, consulta, produto));
        }
        if (preenchido(filtro.marca())) {
            predicados.add(igualSemAcento(builder, produto.get("marca"), filtro.marca()));
        }
        if (preenchido(filtro.principioAtivo())) {
            predicados.add(igualSemAcento(builder, produto.get("principioAtivo"), filtro.principioAtivo()));
        }
        if (preenchido(filtro.forma())) {
            predicados.add(igualSemAcento(builder, produto.get("formaFarmaceutica"), filtro.forma()));
        }
        if (filtro.tipo() != null) {
            predicados.add(builder.equal(produto.get("tipo"), filtro.tipo()));
        }
        if (filtro.exigeReceita() != null) {
            predicados.add(builder.equal(produto.get("exigeReceita"), filtro.exigeReceita()));
        }
        return predicados;
    }

    private Predicate correspondeEan(CriteriaBuilder builder, CriteriaQuery<?> consulta, Root<Produto> produto,
            Expression<String> padrao) {
        Subquery<Long> ean = consulta.subquery(Long.class);
        Root<Apresentacao> apresentacao = ean.from(Apresentacao.class);
        ean.select(apresentacao.get("id")).where(
                builder.equal(apresentacao.get("produto"), produto),
                builder.like(normalizado(builder, apresentacao.get("ean")), padrao, '\\'));
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
            OrdenacaoCatalogo ordenacao, String termo) {
        if (ordenacao == OrdenacaoCatalogo.RELEVANCIA && preenchido(termo)) {
            Expression<String> comeca = semAcento(builder, escaparLike(termo.toLowerCase(java.util.Locale.ROOT)) + "%");
            Expression<String> contem = padraoContem(builder, termo);
            Expression<String> nome = normalizado(builder, produto.get("nome"));
            var peso = builder.<Integer>selectCase()
                    .when(builder.like(nome, comeca, '\\'), 0)
                    .when(builder.like(nome, contem, '\\'), 1)
                    .when(builder.or(
                            builder.like(normalizado(builder, produto.get("marca")), contem, '\\'),
                            builder.like(normalizado(builder,
                                    builder.coalesce(produto.<String>get("principioAtivo"), "")), contem, '\\')), 2)
                    .otherwise(3);
            consulta.orderBy(builder.asc(peso), builder.asc(builder.lower(produto.<String>get("nome"))),
                    builder.asc(produto.get("id")));
            return;
        }
        if (ordenacao == OrdenacaoCatalogo.MAIS_VENDIDOS) {
            consulta.orderBy(builder.desc(produto.get("qtdVendida")), builder.asc(produto.get("id")));
            return;
        }
        if (ordenacao == OrdenacaoCatalogo.NOME || ordenacao == OrdenacaoCatalogo.NOME_DESC
                || ordenacao == OrdenacaoCatalogo.RELEVANCIA) {
            var nome = builder.lower(produto.<String>get("nome"));
            // O id desempata nomes iguais: sem ele a ordem entre páginas não é fixa e a paginação repete ou pula produtos.
            consulta.orderBy(ordenacao == OrdenacaoCatalogo.NOME_DESC ? builder.desc(nome) : builder.asc(nome),
                    builder.asc(produto.get("id")));
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
