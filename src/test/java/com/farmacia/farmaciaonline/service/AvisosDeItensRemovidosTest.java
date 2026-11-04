package com.farmacia.farmaciaonline.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

/**
 * Teste unitário puro (sem Spring), com o relógio controlado pelo teste.
 */
class AvisosDeItensRemovidosTest {

    private static final Instant AGORA = Instant.parse("2026-11-04T10:00:00Z");

    private final AvisosDeItensRemovidos avisos = new AvisosDeItensRemovidos();

    @Test
    void avisoApareceUmaVezSoESemRepetirNome() {
        avisos.anotar(1L, "Dipirona 1 g", AGORA);
        avisos.anotar(1L, "Dipirona 1 g", AGORA);
        avisos.anotar(1L, "Dipirona 500 mg", AGORA);

        assertThat(avisos.consumir(1L, AGORA)).containsExactly("Dipirona 1 g", "Dipirona 500 mg");
        assertThat(avisos.consumir(1L, AGORA)).isEmpty();
        assertThat(avisos.consumir(2L, AGORA)).isEmpty();
        assertThat(avisos.consumir(null, AGORA)).isEmpty();
    }

    @Test
    void avisoVencidoNaoAparece() {
        avisos.anotar(1L, "Dipirona 1 g", AGORA);

        assertThat(avisos.consumir(1L, AGORA.plus(AvisosDeItensRemovidos.VALIDADE).plusSeconds(1))).isEmpty();
    }

    @Test
    void acimaDoTetoOsVencidosSaoVarridos() {
        for (long i = 0; i < AvisosDeItensRemovidos.MAXIMO_DE_CHAVES; i++) {
            avisos.anotar(i, "Produto", AGORA);
        }
        assertThat(avisos.quantidadeDeChaves()).isEqualTo(AvisosDeItensRemovidos.MAXIMO_DE_CHAVES);

        avisos.anotar(-1L, "Produto novo", AGORA.plus(AvisosDeItensRemovidos.VALIDADE).plusSeconds(1));

        assertThat(avisos.quantidadeDeChaves()).isEqualTo(1);
    }
}
