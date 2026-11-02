package com.farmacia.farmaciaonline.model;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;

import com.farmacia.farmaciaonline.model.enums.Genero;
import com.farmacia.farmaciaonline.validacao.CpfValido;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Primeiro acesso pelo login social (F-20): o que o provedor não informa e o cadastro exige. Sem senha, perfil,
 * status, id ou termos: a conta nasce CLIENTE e o resto é decidido no {@code LoginSocialService}. O e-mail só é usado
 * quando o provedor não mandou um.
 */
@Getter
@Setter
public class CompletarSocialForm {

    @NotBlank(message = "Informe o nome completo.")
    @Size(max = 150, message = "O nome deve ter no máximo 150 caracteres.")
    private String nome;

    @NotBlank(message = "Informe o CPF.")
    @CpfValido(message = "CPF inválido: confira os 11 números.")
    private String cpf;

    @NotNull(message = "Informe a data de nascimento.")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate dataNascimento;

    @NotNull(message = "Informe o gênero.")
    private Genero genero;

    @NotBlank(message = "Informe o celular.")
    @Pattern(regexp = "^$|^\\(?[1-9][1-9]\\)?\\s?9\\d{4}-?\\d{4}$",
            message = "Celular inválido: use o DDD e 9 dígitos, por exemplo (67) 99999-9999.")
    private String celular;

    @Size(max = 254, message = "O e-mail deve ter no máximo 254 caracteres.")
    private String email;

    @AssertTrue(message = "É preciso aceitar os termos para criar a conta.")
    private boolean aceiteTermos;
}
