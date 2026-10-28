package com.farmacia.farmaciaonline.model;

import java.math.BigDecimal;
import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Item de um {@link Carrinho}: uma {@link Apresentacao} e a quantidade. Cada apresentação
 * aparece uma vez por carrinho (adicionar de novo soma a quantidade).
 * Sem {@code @ToString}: as relações disparariam lazy loading.
 */
@Entity
@Table(name = "item_carrinho")
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(of = "id")
public class ItemCarrinho {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "carrinho_id", nullable = false)
    private Carrinho carrinho;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "apresentacao_id", nullable = false)
    private Apresentacao apresentacao;

    @Column(nullable = false)
    private int quantidade;

    /**
     * Preço da apresentação no momento em que o item foi adicionado. Comparar com o preço
     * atual detecta que o preço mudou (UC05-E3).
     */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal precoVisto;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant criadoEm;

    /** {@code precoVisto × quantidade}. Não usa o preço atual da apresentação. */
    public BigDecimal getSubtotal() {
        return precoVisto.multiply(BigDecimal.valueOf(quantidade));
    }
}
