package com.farmacia.farmaciaonline.model;

import java.math.BigDecimal;
import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.farmacia.farmaciaonline.model.enums.MetodoPagamento;
import com.farmacia.farmaciaonline.model.enums.StatusPagamento;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * Tentativa de pagamento de um {@link Pedido}. Um pedido pode ter várias (UC08-E2: recusado,
 * tenta outro método). Do cartão só ficam dados mascarados: nunca número completo nem CVV.
 * Parcelas acima de 1 só no cartão de crédito (o banco confere).
 * Sem {@code @ToString}: as relações disparariam lazy loading.
 */
@Entity
@Table(name = "pagamento")
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(of = "id")
public class Pagamento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pedido_id", nullable = false)
    private Pedido pedido;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private MetodoPagamento metodo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private StatusPagamento status = StatusPagamento.PENDENTE;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal valor;

    @Column(nullable = false)
    private int parcelas = 1;

    @Column(length = 100)
    private String transacaoGatewayId;

    @Column(length = 20)
    private String cartaoBandeira;

    @Column(length = 4)
    private String cartaoUltimos4;

    /** Cartão salvo usado. Fica nulo se o cartão for excluído depois. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cartao_salvo_id")
    private CartaoSalvo cartaoSalvo;

    /** Pix copia-e-cola ou linha digitável do boleto. */
    // coluna TEXT: String simples (nada de @Lob, que no Postgres vira oid e quebra o validate)
    private String codigoPagamento;

    private Instant expiraEm;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant criadoEm;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant atualizadoEm;
}
