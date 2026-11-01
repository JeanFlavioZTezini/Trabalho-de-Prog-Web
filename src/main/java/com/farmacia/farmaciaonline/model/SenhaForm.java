package com.farmacia.farmaciaonline.model;

import com.farmacia.farmaciaonline.validacao.PoliticaDeSenha;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class SenhaForm {

    @NotBlank(message = "Informe a senha atual.")
    private String senhaAtual;

    @NotBlank(message = "Crie uma nova senha.")
    @Pattern(regexp = PoliticaDeSenha.PADRAO, message = PoliticaDeSenha.MENSAGEM)
    private String novaSenha;

    @NotBlank(message = "Repita a nova senha.")
    private String confirmarNovaSenha;
}
