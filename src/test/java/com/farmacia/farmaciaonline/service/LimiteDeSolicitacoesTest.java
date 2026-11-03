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

    @Test
    void chavesVencidasSaoVarridasQuandoOMapaPassaDoMaximo() {
        for (int i = 0; i < 1500; i++) {
            assertThat(limite.registrar("inventado" + i + "@exemplo.com", INICIO)).isZero();
        }
        assertThat(limite.quantidadeDeChaves()).isEqualTo(1500);

        // Duas horas depois, um pedido novo varre as 1500 chaves antigas e fica só ele.
        assertThat(limite.registrar("maria@exemplo.com", INICIO.plus(Duration.ofHours(2)))).isZero();
        assertThat(limite.quantidadeDeChaves()).isEqualTo(1);
    }

    @Test
    void aVarreduraNaoApagaOLimiteDeChaveRecente() {
        Instant agora = INICIO.plus(Duration.ofHours(2));
        assertThat(limite.registrar("maria@exemplo.com", agora)).isZero();
        for (int i = 0; i < 1500; i++) {
            limite.registrar("inventado" + i + "@exemplo.com", INICIO);
        }

        // A varredura roda, mas o pedido de 20 s atrás continua valendo.
        assertThat(limite.registrar("maria@exemplo.com", agora.plusSeconds(20))).isEqualTo(40);
    }

    @Test
    void chaveLongaEhCortadaEm150Caracteres() {
        String longa = "a".repeat(400);
        assertThat(limite.registrar(longa, INICIO)).isZero();

        // Mesmos 150 primeiros caracteres = mesma chave: o segundo pedido espera o intervalo.
        assertThat(limite.registrar("a".repeat(150) + "b", INICIO.plusSeconds(1))).isEqualTo(59);
    }
}
