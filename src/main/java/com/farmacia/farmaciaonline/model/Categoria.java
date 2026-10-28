package com.farmacia.farmaciaonline.model;

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
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Categoria do catálogo, organizada em hierarquia (categoria raiz → subcategorias).
 * Sem {@code @ToString}: a relação {@code categoriaPai} dispararia lazy loading.
 */
@Entity
@Table(name = "categoria")
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(of = "id")
public class Categoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80)
    private String nome;

    // coluna TEXT: String simples (nada de @Lob, que no Postgres vira oid e quebra o validate)
    private String descricao;

    /** Categoria pai. Nula = categoria raiz. Ciclos são validados na aplicação (UC13-E3). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "categoria_pai_id")
    private Categoria categoriaPai;

    private String imagem;

    @Column(nullable = false)
    private int ordem;

    @Column(nullable = false)
    private boolean ativa = true;

    /** "Controlados": os produtos da categoria herdam essa exigência (UC13). */
    @Column(nullable = false)
    private boolean exigeReceita;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant criadoEm;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant atualizadoEm;
}
