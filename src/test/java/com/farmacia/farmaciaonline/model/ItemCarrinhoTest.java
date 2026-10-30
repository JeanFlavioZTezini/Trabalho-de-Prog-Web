package com.farmacia.farmaciaonline.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Teste unitário puro: sem Spring e sem banco.
 */
class ItemCarrinhoTest {

    @ParameterizedTest(name = "{0} x {1} = {2}")
    @CsvSource({
            "30.99, 2, 61.98",
            "20.99, 1, 20.99",
            "12.90, 3, 38.70",
            "0.01,  10, 0.10"
    })
    @DisplayName("getSubtotal() é precoVisto vezes quantidade")
    void getSubtotal(String precoVisto, int quantidade, String esperado) {
        ItemCarrinho item = new ItemCarrinho();
        item.setPrecoVisto(new BigDecimal(precoVisto));
        item.setQuantidade(quantidade);

        assertThat(item.getSubtotal()).isEqualByComparingTo(esperado);
    }

    @Test
    @DisplayName("getSubtotal() usa o preço visto, não o preço atual da apresentação")
    void naoUsaOPrecoAtualDaApresentacao() {
        Apresentacao apresentacao = new Apresentacao();
        apresentacao.setPreco(new BigDecimal("40.00"));

        ItemCarrinho item = new ItemCarrinho();
        item.setApresentacao(apresentacao);
        item.setPrecoVisto(new BigDecimal("30.99"));
        item.setQuantidade(2);

        assertThat(item.getSubtotal()).isEqualByComparingTo("61.98");
    }
}
