package com.farmacia.farmaciaonline.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CategoriaForm {

    @NotBlank(message = "Informe o nome.")
    @Size(max = 80, message = "O nome deve ter no máximo 80 caracteres.")
    private String nome;

    private String descricao;

    private Long paiId;

    @PositiveOrZero(message = "A ordem não pode ser negativa.")
    private int ordem;

    private boolean ativa = true;
}
