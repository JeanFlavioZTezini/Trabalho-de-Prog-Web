package com.farmacia.farmaciaonline.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.farmacia.farmaciaonline.model.enums.MetodoPagamento;

class CartaoFormTest {

    private static CartaoForm valido() {
        CartaoForm form = new CartaoForm();
        form.setNumeroCartao("4111 1111 1111 1111");
        form.setValidadeCartao("12/30");
        form.setCvv("123");
        form.setNomeImpresso("CLIENTE TESTE");
        return form;
    }

    @Test
    @DisplayName("cartão no formato certo não tem erro")
    void formatoValido() {
        assertThat(valido().errosDeFormato()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "1234", "abcd efgh ijkl mnop"})
    @DisplayName("número fora do formato é recusado")
    void numeroInvalido(String numero) {
        CartaoForm form = valido();
        form.setNumeroCartao(numero);

        assertThat(form.errosDeFormato()).hasSize(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "13/30", "1/30", "12-30"})
    @DisplayName("validade fora de MM/AA é recusada")
    void validadeInvalida(String validade) {
        CartaoForm form = valido();
        form.setValidadeCartao(validade);

        assertThat(form.errosDeFormato()).hasSize(1);
    }

    @Test
    @DisplayName("sem nada preenchido, aponta os quatro campos")
    void vazio() {
        assertThat(new CartaoForm().errosDeFormato()).hasSize(4);
    }

    @Test
    @DisplayName("o toString do formulário não mostra o número nem o código de segurança")
    void toStringNaoVazaDados() {
        assertThat(valido().toString()).doesNotContain("4111").doesNotContain("123");
    }

    @Test
    @DisplayName("parcelas só valem no crédito")
    void parcelasSoNoCredito() {
        CheckoutForm form = new CheckoutForm();
        form.setParcelas(6);

        form.setMetodo(MetodoPagamento.CARTAO_CREDITO);
        assertThat(form.parcelasEfetivas()).isEqualTo(6);

        form.setMetodo(MetodoPagamento.PIX);
        assertThat(form.parcelasEfetivas()).isEqualTo(1);

        form.setMetodo(MetodoPagamento.CARTAO_CREDITO);
        form.setParcelas(-3);
        assertThat(form.parcelasEfetivas()).isEqualTo(1);
    }
}
