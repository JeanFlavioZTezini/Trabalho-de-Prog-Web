package com.farmacia.farmaciaonline.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.farmacia.farmaciaonline.model.enums.Tarja;
import com.farmacia.farmaciaonline.model.enums.TipoProduto;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Produto do catálogo (o "medicamento" em si). Preço, SKU, EAN e estoque ficam nas
 * {@link Apresentacao apresentações}, não aqui.
 * <p>
 * As coleções {@code categorias}, {@code apresentacoes} e {@code imagens} são LAZY: acesse-as
 * dentro de uma transação (ou com {@code JOIN FETCH}) para evitar {@code LazyInitializationException}
 * e consultas N+1. Também não há cascade nesta fase: grave cada {@link Apresentacao} e
 * {@link ImagemProduto} pelo próprio repository.
 * <p>
 * Sem {@code @ToString}: as relações disparariam lazy loading.
 */
@Entity
@Table(name = "produto")
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(of = "id")
public class Produto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String nome;

    @Column(nullable = false, length = 100)
    private String marca;

    @Column(length = 150)
    private String principioAtivo;

    /** Nulo para itens que não são medicamento. */
    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private TipoProduto tipo;

    /** Comprimido, xarope, gotas, pomada... */
    @Column(length = 30)
    private String formaFarmaceutica;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Tarja tarja = Tarja.SEM_TARJA;

    /** I3: se verdadeiro, o pedido só pode ser RETIRADA. Tarja diferente de SEM_TARJA exige receita. */
    @Column(nullable = false)
    private boolean exigeReceita;

    /** Registro no Ministério da Saúde. */
    @Column(length = 30)
    private String registroMs;

    private String bulaUrl;

    // colunas TEXT: String simples (nada de @Lob, que no Postgres vira oid e quebra o validate)
    private String indicacao;

    private String acao;

    private String viaAdministracao;

    private String publicoAlvo;

    /** Exclusão lógica (UC12). */
    @Column(nullable = false)
    private boolean ativo = true;

    /** Base do "Mais vistos" da home. */
    @Column(nullable = false)
    private long visualizacoes;

    /** Base do "Mais comprados" e da ordenação por mais vendidos. */
    @Column(nullable = false)
    private long qtdVendida;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant criadoEm;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant atualizadoEm;

    /** Último administrador que alterou o produto (UC12). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "atualizado_por_id")
    private Usuario atualizadoPor;

    /** Dono do relacionamento N:N. {@code Set} evita o "bag" do Hibernate e duplicatas. */
    @ManyToMany
    @JoinTable(
            name = "produto_categoria",
            joinColumns = @JoinColumn(name = "produto_id"),
            inverseJoinColumns = @JoinColumn(name = "categoria_id"))
    private Set<Categoria> categorias = new HashSet<>();

    @BatchSize(size = 16)
    @OneToMany(mappedBy = "produto")
    @OrderBy("ordem")
    private List<Apresentacao> apresentacoes = new ArrayList<>();

    @BatchSize(size = 16)
    @OneToMany(mappedBy = "produto")
    @OrderBy("ordem")
    private List<ImagemProduto> imagens = new ArrayList<>();

    /**
     * Apresentação padrão: a de menor {@code ordem}. Não olha estoque nem se está ativa.
     *
     * @return a apresentação padrão, ou {@code null} se o produto não tem apresentações
     */
    public Apresentacao getApresentacaoPadrao() {
        return apresentacoes.stream()
                .min(Comparator.comparingInt(Apresentacao::getOrdem))
                .orElse(null);
    }

    /**
     * Produto indisponível: nenhuma de suas apresentações está disponível
     * (ver {@link Apresentacao#isDisponivel()}). Sem apresentações, também é indisponível.
     */
    public boolean isIndisponivel() {
        return apresentacoes.stream().noneMatch(Apresentacao::isDisponivel);
    }
}
