package com.farmacia.farmaciaonline.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Loja para retirada de pedidos. Pedido com item que exige receita só pode ser retirado em
 * loja (I3).
 */
@Entity
@Table(name = "loja")
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(of = "id")
public class Loja {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String nome;

    /** Só dígitos (8). */
    @Column(nullable = false, length = 8)
    private String cep;

    @Column(nullable = false, length = 200)
    private String logradouro;

    @Column(nullable = false, length = 20)
    private String numero;

    @Column(nullable = false, length = 100)
    private String bairro;

    @Column(nullable = false, length = 100)
    private String cidade;

    @Column(nullable = false, length = 2)
    private String uf;

    @Column(length = 200)
    private String horarioFuncionamento;

    @Column(nullable = false)
    private boolean ativa = true;
}
