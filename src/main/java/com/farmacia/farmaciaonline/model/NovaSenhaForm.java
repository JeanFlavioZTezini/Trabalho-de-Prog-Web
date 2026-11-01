package com.farmacia.farmaciaonline.model;

import com.farmacia.farmaciaonline.validacao.PoliticaDeSenha;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

/** Nova senha na recuperação (UC14), com a mesma regra do cadastro. */
@Getter
@Setter
public class NovaSenhaForm {

    @NotBlank(message = "Crie a nova senha.")
    @Pattern(regexp = PoliticaDeSenha.PADRAO, message = PoliticaDeSenha.MENSAGEM)
    private String senha;

    @NotBlank(message = "Repita a nova senha.")
    private String confirmarSenha;
}
