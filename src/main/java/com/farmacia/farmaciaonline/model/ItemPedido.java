package com.farmacia.farmaciaonline.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

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
 * Item de um {@link Pedido}. Guarda uma cópia (snapshot) do nome, da descrição, do SKU, do preço
 * e da exigência de receita no momento da compra, então mudanças no catálogo não alteram o
 * histórico. A {@link Apresentacao} fica só como referência.
 * Sem {@code @ToString}: as relações disparariam lazy loading.
 */
@Entity
@Table(name = "item_pedido")
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(of = "id")
public class ItemPedido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pedido_id", nullable = false)
    private Pedido pedido;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "apresentacao_id", nullable = false)
    private Apresentacao apresentacao;

    @Column(nullable = false, length = 200)
    private String nomeProduto;

    @Column(nullable = false, length = 60)
    private String descricaoApresentacao;

    @Column(nullable = false, length = 40)
    private String sku;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal precoUnitario;

    @Column(nullable = false)
    private int quantidade;

    @Column(nullable = false)
    private boolean exigeReceita;

    /** {@code precoUnitario × quantidade}, com 2 casas (HALF_UP). */
    public BigDecimal getSubtotal() {
        return precoUnitario.multiply(BigDecimal.valueOf(quantidade)).setScale(2, RoundingMode.HALF_UP);
    }
}
