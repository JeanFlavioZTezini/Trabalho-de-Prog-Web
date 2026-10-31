package com.farmacia.farmaciaonline.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class FreteDemoTest {

    private final FreteDemo frete = new FreteDemo();

    @ParameterizedTest
    @CsvSource({
            "SP, 01310100, 9.90, 2",
            "SP, 09999999, 9.90, 2",
            "SP, 13000000, 14.90, 3",
            "RJ, 20040020, 19.90, 4",
            "MG, 30130010, 19.90, 4"
    })
    @DisplayName("a tabela demo devolve o frete e o prazo da faixa de CEP")
    void regioesAtendidas(String uf, String cep, String valor, int prazo) {
        FreteDemo.Cotacao cotacao = frete.cotar(uf, cep).orElseThrow();

        assertThat(cotacao.valor()).isEqualByComparingTo(valor);
        assertThat(cotacao.prazoDias()).isEqualTo(prazo);
    }

    @ParameterizedTest
    @CsvSource({
            "AC, 69900000",
            "BA, 40000000",
            "SP, 10000000"
    })
    @DisplayName("região fora da tabela não é atendida")
    void regioesNaoAtendidas(String uf, String cep) {
        assertThat(frete.cotar(uf, cep)).isEmpty();
    }

    @Test
    @DisplayName("CEP de uma UF com a sigla de outra não é atendido")
    void cepEUfPrecisamCombinar() {
        assertThat(frete.cotar("RJ", "01310100")).isEmpty();
    }

    @Test
    @DisplayName("dados incompletos não são atendidos, sem erro")
    void dadosInvalidos() {
        assertThat(frete.cotar(null, "01310100")).isEmpty();
        assertThat(frete.cotar("SP", null)).isEmpty();
        assertThat(frete.cotar("SP", "0131")).isEmpty();
    }
}
