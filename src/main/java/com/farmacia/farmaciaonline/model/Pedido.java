package com.farmacia.farmaciaonline.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.farmacia.farmaciaonline.model.enums.StatusPedido;
import com.farmacia.farmaciaonline.model.enums.TipoEntrega;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Pedido. O {@code id} é o número do pedido. O endereço de entrega é uma cópia
 * ({@link EnderecoEntrega}), então mexer em um {@link Endereco} salvo não altera pedidos antigos.
 * <p>
 * Entrega: {@code enderecoEntrega} preenchido e {@code lojaRetirada} nula. Retirada:
 * {@code lojaRetirada} preenchida, {@code enderecoEntrega} nulo e frete zero. O banco recusa
 * as outras combinações e também {@code total != subtotal + frete}: chame
 * {@link #recalcularTotais()} antes de gravar.
 * <p>
 * Os itens são gravados junto com o pedido ({@code cascade = ALL}).
 * Sem {@code @ToString}: as relações disparariam lazy loading.
 */
@Entity
@Table(name = "pedido")
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(of = "id")
public class Pedido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 25)
    private StatusPedido status = StatusPedido.AGUARDANDO_PAGAMENTO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TipoEntrega tipoEntrega;

    /** Loja da retirada. Nula em pedido de entrega. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "loja_retirada_id")
    private Loja lojaRetirada;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "destinatario", column = @Column(name = "entrega_destinatario", length = 150)),
            @AttributeOverride(name = "cep", column = @Column(name = "entrega_cep", length = 8)),
            @AttributeOverride(name = "logradouro", column = @Column(name = "entrega_logradouro", length = 200)),
            @AttributeOverride(name = "numero", column = @Column(name = "entrega_numero", length = 20)),
            @AttributeOverride(name = "complemento", column = @Column(name = "entrega_complemento", length = 100)),
            @AttributeOverride(name = "bairro", column = @Column(name = "entrega_bairro", length = 100)),
            @AttributeOverride(name = "cidade", column = @Column(name = "entrega_cidade", length = 100)),
            @AttributeOverride(name = "uf", column = @Column(name = "entrega_uf", length = 2)),
            @AttributeOverride(name = "referencia", column = @Column(name = "entrega_referencia", length = 200))
    })
    private EnderecoEntrega enderecoEntrega;

    private Integer prazoEntregaDias;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal subtotal;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal frete = BigDecimal.ZERO;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal total;

    /** Limite para pagar Pix ou boleto (UC08-A2). */
    private Instant expiraEm;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant criadoEm;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant atualizadoEm;

    @OneToMany(mappedBy = "pedido", cascade = CascadeType.ALL)
    @OrderBy("id")
    private List<ItemPedido> itens = new ArrayList<>();

    /**
     * Se algum item exige receita, o pedido só pode ser RETIRADA (I3). Só consulta: o banco não
     * garante essa regra (ela cruza tabelas), então o service do checkout precisa validá-la.
     */
    public boolean temItemQueExigeReceita() {
        return itens.stream().anyMatch(ItemPedido::isExigeReceita);
    }

    /**
     * Recalcula {@code subtotal} (soma dos subtotais dos itens) e {@code total}
     * ({@code subtotal + frete}). Tudo com 2 casas (HALF_UP), inclusive o frete, para respeitar o
     * CHECK {@code total = subtotal + frete} do banco. Frete nulo conta como zero.
     */
    public void recalcularTotais() {
        subtotal = itens.stream()
                .map(ItemPedido::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        frete = (frete == null ? BigDecimal.ZERO : frete).setScale(2, RoundingMode.HALF_UP);
        total = subtotal.add(frete);
    }
}
