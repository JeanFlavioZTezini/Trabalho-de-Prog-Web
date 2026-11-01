package com.farmacia.farmaciaonline.model;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;

import com.farmacia.farmaciaonline.model.enums.Genero;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ContaForm {

    @NotBlank(message = "Informe o nome completo.")
    @Size(max = 150, message = "O nome deve ter no máximo 150 caracteres.")
    private String nome;

    @NotNull(message = "Informe a data de nascimento.")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate dataNascimento;

    @NotNull(message = "Informe o gênero.")
    private Genero genero;
}
