package com.farmacia.farmaciaonline.model;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Confirmação da exclusão da própria conta (F-24): identidade (senha) e intenção (palavra digitada). Sem id. */
@Getter
@Setter
@NoArgsConstructor
public class ExclusaoContaForm {

    @NotBlank(message = "Informe a senha atual.")
    private String senhaAtual;

    @NotBlank(message = "Digite EXCLUIR para confirmar.")
    private String confirmacao;
}
