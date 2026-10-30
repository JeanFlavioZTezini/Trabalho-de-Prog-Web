package com.farmacia.farmaciaonline.gateway.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.farmacia.farmaciaonline.gateway.dto.RequisicaoPagamento;
import com.farmacia.farmaciaonline.gateway.dto.RespostaPagamento;
import com.farmacia.farmaciaonline.model.enums.MetodoPagamento;
import com.farmacia.farmaciaonline.model.enums.StatusPagamento;

class DemoGatewayAdapterTest {

    private DemoGatewayAdapter adapter;
    private String validadeFutura;

    @BeforeEach
    void setUp() {
        adapter = new DemoGatewayAdapter();
        validadeFutura = YearMonth.now().plusYears(2).format(DateTimeFormatter.ofPattern("MM/yy"));
    }

    private RequisicaoPagamento requisicaoCartao(String numero, int parcelas) {
        return new RequisicaoPagamento(
                1L,
                new BigDecimal("99.90"),
                MetodoPagamento.CARTAO_CREDITO,
                numero,
                "123",
                validadeFutura,
                "CLIENTE TESTE",
                parcelas,
                null);
    }

    // ---------- Cartão de Crédito / Débito ----------
    @Test
    @DisplayName("cartão com número válido terminando diferente de zero é aprovado")
    void cartaoAprovado() {
        RequisicaoPagamento req = requisicaoCartao("4000 0000 0000 0002", 1);
        RespostaPagamento resp = adapter.processar(req);

        assertThat(resp.status()).isEqualTo(StatusPagamento.APROVADO);
        assertThat(resp.bandeira()).isEqualTo("VISA");
        assertThat(resp.ultimos4()).isEqualTo("0002");
        assertThat(resp.transacaoGatewayId()).startsWith("CARD-");
        assertThat(resp.valorConfirmado()).isEqualByComparingTo("99.90");
    }

    @Test
    @DisplayName("cartão com número válido terminando em zero é recusado deterministamente")
    void cartaoRecusado() {
        RequisicaoPagamento req = requisicaoCartao("4000-0000-0000-0010", 1);
        RespostaPagamento resp = adapter.processar(req);

        assertThat(resp.status()).isEqualTo(StatusPagamento.RECUSADO);
        assertThat(resp.ultimos4()).isEqualTo("0010");
        assertThat(resp.mensagem()).contains("recusado");
    }

    @Test
    @DisplayName("recusa cartão com formato inválido ou vazio")
    void cartaoFormatoInvalido() {
        assertThatThrownBy(() -> adapter.processar(requisicaoCartao("", 1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("obrigatório");

        assertThatThrownBy(() -> adapter.processar(requisicaoCartao("12345", 1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("entre 13 e 19 dígitos");
    }

    @Test
    @DisplayName("recusa cartão com dígito verificador inválido (falha Luhn)")
    void cartaoFalhaLuhn() {
        assertThatThrownBy(() -> adapter.processar(requisicaoCartao("4000000000000003", 1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dígito verificador");
    }

    @Test
    @DisplayName("recusa cartão com validade vencida ou formato incorreto")
    void cartaoValidadeInvalida() {
        RequisicaoPagamento vencido = new RequisicaoPagamento(
                1L, new BigDecimal("50.00"), MetodoPagamento.CARTAO_CREDITO,
                "4000000000000002", "123", "01/20", "NOME", 1, null);

        assertThatThrownBy(() -> adapter.processar(vencido))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("vencido");

        RequisicaoPagamento formatoInvalido = new RequisicaoPagamento(
                1L, new BigDecimal("50.00"), MetodoPagamento.CARTAO_CREDITO,
                "4000000000000002", "123", "12-30", "NOME", 1, null);

        assertThatThrownBy(() -> adapter.processar(formatoInvalido))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("MM/AA");
    }

    @Test
    @DisplayName("limite de parcelas no crédito é de 1 a 12")
    void parcelasCreditoLimite() {
        assertThatThrownBy(() -> adapter.processar(requisicaoCartao("4000000000000002", 13)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("entre 1 e 12");
    }

    @Test
    @DisplayName("métodos fora de cartão de crédito não aceitam parcelamento")
    void parcelasApenasNoCredito() {
        RequisicaoPagamento pixParcelado = new RequisicaoPagamento(
                1L, new BigDecimal("50.00"), MetodoPagamento.PIX,
                null, null, null, null, 2, null);

        assertThatThrownBy(() -> adapter.processar(pixParcelado))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("não aceita parcelamento");
    }

    // ---------- Pix ----------
    @Test
    @DisplayName("pix retorna status pendente com código demonstrativo e expiração de 30 min")
    void pixDemonstrativo() {
        RequisicaoPagamento req = new RequisicaoPagamento(
                1L, new BigDecimal("75.50"), MetodoPagamento.PIX,
                null, null, null, null, 1, null);

        RespostaPagamento resp = adapter.processar(req);

        assertThat(resp.status()).isEqualTo(StatusPagamento.PENDENTE);
        assertThat(resp.transacaoGatewayId()).startsWith("PIX-");
        assertThat(resp.codigoPagamento()).startsWith("DEMO-PIX-");
        assertThat(resp.expiraEm()).isAfter(Instant.now().plusSeconds(25 * 60));
        assertThat(resp.expiraEm()).isBefore(Instant.now().plusSeconds(31 * 60));
    }

    // ---------- Boleto ----------
    @Test
    @DisplayName("boleto retorna status pendente com linha digitável claramente não pagável")
    void boletoDemonstrativo() {
        RequisicaoPagamento req = new RequisicaoPagamento(
                1L, new BigDecimal("150.00"), MetodoPagamento.BOLETO,
                null, null, null, null, 1, null);

        RespostaPagamento resp = adapter.processar(req);

        assertThat(resp.status()).isEqualTo(StatusPagamento.PENDENTE);
        assertThat(resp.transacaoGatewayId()).startsWith("BOLETO-");
        assertThat(resp.codigoPagamento()).startsWith("9999.0001 DEMO-BOLETO-");
        assertThat(resp.expiraEm()).isAfter(Instant.now().plusSeconds(2 * 24 * 60 * 60));
    }

    @Test
    @DisplayName("consultar transação inexistente retorna recusado com mensagem amigável")
    void consultarInexistente() {
        RespostaPagamento resp = adapter.consultar("TX-INEXISTENTE");
        assertThat(resp.status()).isEqualTo(StatusPagamento.RECUSADO);
        assertThat(resp.mensagem()).contains("não encontrada");
    }

    @Test
    @DisplayName("forcarStatus atualiza o estado da transação demo e permite consultar novo status")
    void forcarStatusTransacaoDemo() {
        RequisicaoPagamento req = new RequisicaoPagamento(
                1L, new BigDecimal("100.00"), MetodoPagamento.PIX,
                null, null, null, null, 1, null);
        RespostaPagamento gerado = adapter.processar(req);

        adapter.forcarStatus(gerado.transacaoGatewayId(), StatusPagamento.APROVADO);
        RespostaPagamento consultado = adapter.consultar(gerado.transacaoGatewayId());

        assertThat(consultado.status()).isEqualTo(StatusPagamento.APROVADO);
        assertThat(consultado.mensagem()).contains("aprovado (demo)");
    }

    @Test
    @DisplayName("forcarStatus em transação inexistente lança IllegalArgumentException")
    void forcarStatusInexistente() {
        assertThatThrownBy(() -> adapter.forcarStatus("ID-NAO-EXISTE", StatusPagamento.APROVADO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Transação demo não encontrada");
    }
}
