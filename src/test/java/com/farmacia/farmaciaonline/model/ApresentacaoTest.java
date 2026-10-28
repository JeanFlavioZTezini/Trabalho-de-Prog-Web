package com.farmacia.farmaciaonline.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Teste unitário puro: sem Spring e sem banco.
 */
class ApresentacaoTest {

    @ParameterizedTest(name = "ativa={0}, estoque={1} -> disponivel={2}")
    @CsvSource({
            "true,  10, true",
            "true,   1, true",
            "true,   0, false",
            "true,  -1, false",
            "false, 10, false",
            "false,  0, false"
    })
    @DisplayName("isDisponivel() só é verdadeiro quando está ativa E tem estoque maior que zero")
    void isDisponivel(boolean ativa, int estoque, boolean esperado) {
        Apresentacao apresentacao = new Apresentacao();
        apresentacao.setAtiva(ativa);
        apresentacao.setEstoque(estoque);

        assertThat(apresentacao.isDisponivel()).isEqualTo(esperado);
    }
}
