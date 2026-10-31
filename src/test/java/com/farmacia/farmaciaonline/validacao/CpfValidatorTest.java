package com.farmacia.farmaciaonline.validacao;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Teste unitário puro: sem Spring e sem banco.
 */
class CpfValidatorTest {

    @ParameterizedTest(name = "{0} é válido")
    @ValueSource(strings = {"52998224725", "529.982.247-25", "11144477735", "111.444.777-35", "390.533.447-05",
            "12345678909", " 529.982.247-25 "})
    @DisplayName("aceita CPF com os dígitos verificadores certos, com ou sem pontuação")
    void aceitaCpfValido(String cpf) {
        assertThat(CpfValidator.valido(cpf)).isTrue();
    }

    @ParameterizedTest(name = "\"{0}\" é inválido")
    @ValueSource(strings = {"52998224724", "529.982.247-26", "12345678900", "11111111111", "00000000000", "999.999.999-99",
            "5299822472", "529982247255", "abc52998224725", "529.982.247-2x", "52998224725a", ""})
    @DisplayName("recusa dígito verificador errado, números repetidos, tamanho errado e letras")
    void recusaCpfInvalido(String cpf) {
        assertThat(CpfValidator.valido(cpf)).isFalse();
    }

    @Test
    @DisplayName("campo vazio ou nulo passa no validador: quem exige o preenchimento é o @NotBlank")
    void vazioPassa() {
        CpfValidator validador = new CpfValidator();

        assertThat(validador.isValid(null, null)).isTrue();
        assertThat(validador.isValid("", null)).isTrue();
        assertThat(validador.isValid("   ", null)).isTrue();
        assertThat(validador.isValid("52998224724", null)).isFalse();
    }
}
