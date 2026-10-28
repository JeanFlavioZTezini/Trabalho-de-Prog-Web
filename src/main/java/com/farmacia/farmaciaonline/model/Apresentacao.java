package com.farmacia.farmaciaonline.model;

import java.math.BigDecimal;
import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Unidade de venda de um {@link Produto} (ex.: 10, 20 ou 30 comprimidos).
 * Preço, SKU, EAN e estoque ficam aqui, e não no produto.
 * O nome completo exibido ao cliente é {@code produto.nome + " " + apresentacao.descricao}.
 * Sem {@code @ToString}: a relação {@code produto} dispararia lazy loading.
 */
@Entity
@Table(name = "apresentacao")
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(of = "id")
public class Apresentacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "produto_id", nullable = false)
    private Produto produto;

    /** Ex.: "20 comprimidos". */
    @Column(nullable = false, length = 60)
    private String descricao;

    @Column(nullable = false, length = 40, unique = true)
    private String sku;

    @Column(nullable = false, length = 14, unique = true)
    private String ean;

    /** Preço por unidade da apresentação. */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal preco;

    @Column(nullable = false)
    private int estoque;

    /** Limite de unidades por item no pedido (I5). */
    @Column(nullable = false)
    private int limitePorPedido = 10;

    /** Ordem de exibição; a de menor ordem é a apresentação padrão do produto. */
    @Column(nullable = false)
    private int ordem;

    @Column(nullable = false)
    private boolean ativa = true;

    /**
     * Lock otimista do estoque (usado no checkout). Gerenciado pelo Hibernate:
     * começa em 0 e sobe a cada UPDATE. Duas compras simultâneas da mesma apresentação
     * fazem a segunda falhar com {@code ObjectOptimisticLockingFailureException}.
     * É {@code Long} (e não {@code long}) para que "nulo" signifique "ainda não gravada".
     */
    @Version
    private Long versao;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant criadoEm;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant atualizadoEm;

    /** Pode ser comprada agora: está ativa e tem estoque. */
    public boolean isDisponivel() {
        return ativa && estoque > 0;
    }
}
