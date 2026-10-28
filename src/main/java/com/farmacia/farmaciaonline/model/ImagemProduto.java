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
 * Imagem de um {@link Produto}. Sem {@code @ToString}: as relações disparariam lazy loading.
 */
@Entity
@Table(name = "imagem_produto")
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(of = "id")
public class ImagemProduto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "produto_id", nullable = false)
    private Produto produto;

    /**
     * Nula = a imagem vale para todas as apresentações do produto.
     * Com valor, a imagem só aparece (e troca na galeria) ao selecionar essa apresentação (UC02-A1).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "apresentacao_id")
    private Apresentacao apresentacao;

    @Column(nullable = false)
    private String arquivo;

    @Column(nullable = false)
    private int ordem;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant criadoEm;
}
