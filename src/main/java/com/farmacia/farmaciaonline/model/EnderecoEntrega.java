package com.farmacia.farmaciaonline.model;

import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Cópia (snapshot) do endereço de entrega, embutida no {@link Pedido}. As colunas
 * {@code entrega_*} são definidas no {@code @AttributeOverrides} do pedido.
 * Em pedido de retirada o endereço é nulo.
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
public class EnderecoEntrega {

    private String destinatario;

    /** Só dígitos (8). */
    private String cep;

    private String logradouro;

    private String numero;

    private String complemento;

    private String bairro;

    private String cidade;

    private String uf;

    private String referencia;
}
