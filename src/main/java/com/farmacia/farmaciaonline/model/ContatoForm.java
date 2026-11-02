package com.farmacia.farmaciaonline.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Pedido de troca de e-mail ou celular (UC07). O formato depende do tipo e é conferido no {@code ContatoService}. */
@Getter
@Setter
@NoArgsConstructor
public class ContatoForm {

    @NotBlank(message = "Informe o novo contato.")
    @Size(max = 254, message = "O contato deve ter no máximo 254 caracteres.")
    private String novoContato;

    @NotBlank(message = "Informe a senha atual.")
    private String senhaAtual;
}
