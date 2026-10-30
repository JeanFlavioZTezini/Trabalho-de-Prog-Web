package com.farmacia.farmaciaonline.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Carrinho de compras. O dono é um {@link Usuario} (carrinho de quem está logado) ou um
 * {@code token} UUID guardado em cookie (carrinho do visitante), nunca os dois: o banco recusa
 * as demais combinações. No login, o carrinho do token é mesclado ao do usuário (I12).
 * <p>
 * Os itens são gravados e removidos junto com o carrinho ({@code cascade = ALL} e
 * {@code orphanRemoval = true}): para tirar um item, remova-o da lista {@link #getItens()}.
 * Sem {@code @ToString}: as relações disparariam lazy loading.
 */
@Entity
@Table(name = "carrinho")
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(of = "id")
public class Carrinho {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Dono do carrinho de quem está logado. Nulo no carrinho de visitante. */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    /** Identifica o visitante (cookie). Nulo no carrinho de quem está logado. */
    private UUID token;

    @OneToMany(mappedBy = "carrinho", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<ItemCarrinho> itens = new ArrayList<>();

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant criadoEm;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant atualizadoEm;

    /** Carrinho de visitante: identificado só pelo token, sem usuário. */
    public boolean isDeVisitante() {
        return usuario == null && token != null;
    }
}
