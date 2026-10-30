package com.farmacia.farmaciaonline.model;

import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;

import com.farmacia.farmaciaonline.model.enums.StatusPedido;

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
 * Uma mudança de status de um {@link Pedido}. {@code alteradoPor} nulo significa que foi o
 * sistema (por exemplo, pagamento aprovado pelo gateway).
 * Sem {@code @ToString}: as relações disparariam lazy loading.
 */
@Entity
@Table(name = "historico_status_pedido")
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(of = "id")
public class HistoricoStatusPedido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pedido_id", nullable = false)
    private Pedido pedido;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 25)
    private StatusPedido status;

    /** Quem alterou. Nulo = sistema. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "alterado_por_id")
    private Usuario alteradoPor;

    @Column(length = 255)
    private String observacao;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant criadoEm;
}
