package com.farmacia.farmaciaonline.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

/**
 * Teste unitário puro (sem Spring e sem banco), com o relógio controlado pelo teste.
 */
class LimiteDeSolicitacoesTest {

    private static final Instant INICIO = Instant.parse("2026-10-31T10:00:00Z");

    private final LimiteDeSolicitacoes limite = new LimiteDeSolicitacoes(Duration.ofSeconds(60), 5, Duration.ofMinutes(15));

    @Test
    void segundoPedidoAntesDe60SegundosEspera() {
        assertThat(limite.registrar("maria@exemplo.com", INICIO)).isZero();

        assertThat(limite.registrar("maria@exemplo.com", INICIO.plusSeconds(20))).isEqualTo(40);
        assertThat(limite.registrar("maria@exemplo.com", INICIO.plusSeconds(61))).isZero();
    }

    @Test
    void cadaIdentificadorTemOSeuLimite() {
        assertThat(limite.registrar("maria@exemplo.com", INICIO)).isZero();

        assertThat(limite.registrar("ninguem@exemplo.com", INICIO.plusSeconds(1))).isZero();
    }

    @Test
    void depoisDeCincoPedidosNaHoraOProximoSoSai15MinutosDepoisDoAnterior() {
        Instant momento = INICIO;
        for (int pedido = 1; pedido <= 5; pedido++) {
            assertThat(limite.registrar("maria@exemplo.com", momento)).as("pedido %d", pedido).isZero();
            momento = momento.plusSeconds(61);
        }
        Instant ultimo = momento.minusSeconds(61);

        assertThat(limite.registrar("maria@exemplo.com", momento)).isEqualTo(15 * 60 - 61);
        assertThat(limite.registrar("maria@exemplo.com", ultimo.plus(Duration.ofMinutes(15)))).isZero();
    }

    @Test
    void pedidosComMaisDeUmaHoraNaoContam() {
        Instant momento = INICIO;
        for (int pedido = 1; pedido <= 5; pedido++) {
            limite.registrar("maria@exemplo.com", momento);
            momento = momento.plusSeconds(61);
        }

        assertThat(limite.registrar("maria@exemplo.com", INICIO.plus(Duration.ofMinutes(65)))).isZero();
    }

    @Test
    void limparEsqueceTudo() {
        limite.registrar("maria@exemplo.com", INICIO);
        limite.limpar();

        assertThat(limite.registrar("maria@exemplo.com", INICIO.plusSeconds(1))).isZero();
    }
}
