package com.farmacia.farmaciaonline.validacao;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class CpfValidator implements ConstraintValidator<CpfValido, String> {

    @Override
    public boolean isValid(String valor, ConstraintValidatorContext contexto) {
        return valor == null || valor.isBlank() || valido(valor);
    }

    /** Aceita só dígitos, pontos, traço e espaços; recusa CPF de números repetidos (111.111.111-11 etc.). */
    public static boolean valido(String texto) {
        if (texto == null || !texto.matches("[0-9.\\-\\s]+")) {
            return false;
        }
        String digitos = texto.replaceAll("\\D", "");
        if (digitos.length() != 11 || digitos.chars().distinct().count() == 1) {
            return false;
        }
        return digitoVerificador(digitos, 9) == digitos.charAt(9) - '0'
                && digitoVerificador(digitos, 10) == digitos.charAt(10) - '0';
    }

    /** Soma ponderada dos primeiros {@code quantidade} dígitos, com pesos de {@code quantidade + 1} até 2. */
    private static int digitoVerificador(String digitos, int quantidade) {
        int soma = 0;
        for (int i = 0; i < quantidade; i++) {
            soma += (digitos.charAt(i) - '0') * (quantidade + 1 - i);
        }
        int resto = (soma * 10) % 11;
        return resto == 10 ? 0 : resto;
    }
}
