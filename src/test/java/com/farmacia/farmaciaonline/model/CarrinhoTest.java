package com.farmacia.farmaciaonline.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Teste unitário puro: sem Spring e sem banco.
 */
class CarrinhoTest {

    @ParameterizedTest(name = "temUsuario={0}, temToken={1} -> deVisitante={2}")
    @CsvSource({
            "false, true,  true",
            "true,  false, false",
            "false, false, false",
            "true,  true,  false"
    })
    @DisplayName("isDeVisitante() só é verdadeiro com token e sem usuário")
    void isDeVisitante(boolean temUsuario, boolean temToken, boolean esperado) {
        Carrinho carrinho = new Carrinho();
        if (temUsuario) {
            carrinho.setUsuario(new Usuario());
        }
        if (temToken) {
            carrinho.setToken(UUID.randomUUID());
        }

        assertThat(carrinho.isDeVisitante()).isEqualTo(esperado);
    }
}
