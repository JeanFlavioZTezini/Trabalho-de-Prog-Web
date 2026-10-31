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
            "MS, 79000000, 9.90, 2",
            "MS, 79129999, 9.90, 2",
            "MS, 79130000, 14.90, 3",
            "MS, 79999999, 14.90, 3"
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
            "SP, 01310100",
            "RJ, 20040020",
            "MG, 30130010",
            "MT, 78000000",
            "MS, 78999999",
            "MS, 80000000"
    })
    @DisplayName("fora de Mato Grosso do Sul não é atendido")
    void regioesNaoAtendidas(String uf, String cep) {
        assertThat(frete.cotar(uf, cep)).isEmpty();
    }

    @Test
    @DisplayName("CEP de uma UF com a sigla de outra não é atendido")
    void cepEUfPrecisamCombinar() {
        assertThat(frete.cotar("SP", "79002000")).isEmpty();
    }

    @Test
    @DisplayName("dados incompletos não são atendidos, sem erro")
    void dadosInvalidos() {
        assertThat(frete.cotar(null, "79002000")).isEmpty();
        assertThat(frete.cotar("MS", null)).isEmpty();
        assertThat(frete.cotar("MS", "7900")).isEmpty();
    }
}
