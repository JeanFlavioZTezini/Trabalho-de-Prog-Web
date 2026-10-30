package com.farmacia.farmaciaonline.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Teste unitário puro: sem Spring e sem banco.
 */
class ItemPedidoTest {

    @ParameterizedTest(name = "{0} x {1} = {2}")
    @CsvSource({
            "30.99,  2, 61.98",
            "12.90,  3, 38.70",
            "10.005, 1, 10.01",
            "0.333,  3, 1.00"
    })
    @DisplayName("getSubtotal() é preço unitário vezes quantidade, com 2 casas (HALF_UP)")
    void getSubtotal(String precoUnitario, int quantidade, String esperado) {
        ItemPedido item = new ItemPedido();
        item.setPrecoUnitario(new BigDecimal(precoUnitario));
        item.setQuantidade(quantidade);

        assertThat(item.getSubtotal()).isEqualTo(new BigDecimal(esperado));
    }
}
