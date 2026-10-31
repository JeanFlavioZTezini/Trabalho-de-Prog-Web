package com.farmacia.farmaciaonline.model;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;

import com.farmacia.farmaciaonline.model.enums.Genero;
import com.farmacia.farmaciaonline.validacao.CpfValido;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Dados do cadastro (UC03). Só tem os campos que a pessoa preenche: perfil, status, versão dos termos e
 * qualquer outro campo do {@link Usuario} que vier na requisição são ignorados pelo Spring, porque não existem aqui.
 * As regras que dependem de mais de um campo ou do banco (senhas iguais, 18 anos, CPF e e-mail já usados) ficam no
 * {@code CadastroService}.
 */
@Getter
@Setter
public class CadastroForm {

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

    @NotBlank(message = "Informe o e-mail.")
    @Size(max = 254, message = "O e-mail deve ter no máximo 254 caracteres.")
    @Email(regexp = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$", message = "E-mail inválido: use o formato nome@dominio.com.")
    private String email;

    @NotBlank(message = "Crie uma senha.")
    @Pattern(regexp = "^$|^(?=.*\\p{L})(?=.*\\d).{8,}$",
            message = "A senha deve ter 8 caracteres ou mais, com pelo menos uma letra e um número.")
    private String senha;

    @NotBlank(message = "Repita a senha.")
    private String confirmarSenha;

    @AssertTrue(message = "É preciso aceitar os termos para criar a conta.")
    private boolean aceiteTermos;
}
