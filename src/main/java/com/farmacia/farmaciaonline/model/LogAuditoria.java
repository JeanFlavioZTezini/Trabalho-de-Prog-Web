package com.farmacia.farmaciaonline.model;

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
 * Registro de acesso do administrador a dados de clientes (UC11, LGPD): quem acessou, qual ação,
 * em que alvo e de qual IP.
 * Sem {@code @ToString}: a relação {@code usuario} dispararia lazy loading.
 */
@Entity
@Table(name = "log_auditoria")
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(of = "id")
public class LogAuditoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Quem acessou. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    /** Ex.: CLIENTE_LISTAR, CLIENTE_VISUALIZAR, CLIENTE_EXPORTAR. */
    @Column(nullable = false, length = 40)
    private String acao;

    @Column(length = 30)
    private String alvoTipo;

    private Long alvoId;

    /** Filtros usados, etc. */
    @Column(length = 500)
    private String detalhes;

    @Column(length = 45)
    private String ip;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant criadoEm;
}
