package com.farmacia.farmaciaonline.gateway.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.farmacia.farmaciaonline.gateway.dto.RequisicaoPagamento;
import com.farmacia.farmaciaonline.gateway.dto.TokenCartao;
import com.farmacia.farmaciaonline.model.enums.MetodoPagamento;
import com.farmacia.farmaciaonline.model.enums.StatusPagamento;

/** F-25: tokenização do gateway demo, sem Spring. */
class DemoGatewayTokenizacaoTest {

    private final DemoGatewayAdapter gateway = new DemoGatewayAdapter();

    private static RequisicaoPagamento comToken(String token, String cvv) {
        return new RequisicaoPagamento(1L, new BigDecimal("10.00"), MetodoPagamento.CARTAO_CREDITO, null, cvv, null,
                "CLIENTE", 1, 5L, null, token);
    }

    @Test
    @DisplayName("tokenizar devolve token opaco, bandeira, final e validade; nunca o número")
    void tokeniza() {
        TokenCartao token = gateway.tokenizar("4111 1111 1111 1111", "12/30", "CLIENTE");

        assertThat(token.token()).startsWith("tok_demo_").doesNotContain("4111");
        assertThat(token.bandeira()).isEqualTo("VISA");
        assertThat(token.ultimos4()).isEqualTo("1111");
        assertThat(token.validadeMes()).isEqualTo(12);
        assertThat(token.validadeAno()).isEqualTo(2030);
        assertThat(token.toString()).doesNotContain(token.token());
    }

    @Test
    @DisplayName("cartão inválido ou vencido não é tokenizado")
    void recusaCartaoInvalido() {
        assertThatThrownBy(() -> gateway.tokenizar("4111111111111112", "12/30", "X"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> gateway.tokenizar("4111111111111111", "01/20", "X"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("pagamento com token aprova como o cartão original e exige CVV")
    void pagaComToken() {
        TokenCartao aprova = gateway.tokenizar("4111111111111111", "12/30", "X");
        TokenCartao recusa = gateway.tokenizar("4111111111111210", "12/30", "X");

        assertThat(gateway.processar(comToken(aprova.token(), "123")).status()).isEqualTo(StatusPagamento.APROVADO);
        assertThat(gateway.processar(comToken(recusa.token(), "123")).status()).isEqualTo(StatusPagamento.RECUSADO);
        assertThatThrownBy(() -> gateway.processar(comToken(aprova.token(), null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("token desconhecido é recusado")
    void tokenDesconhecido() {
        assertThatThrownBy(() -> gateway.processar(comToken("tok_demo_inexistente", "123")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("não reconhecido");
    }
}
