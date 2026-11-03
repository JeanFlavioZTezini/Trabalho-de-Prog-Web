package com.farmacia.farmaciaonline.social.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.farmacia.farmaciaonline.model.enums.ProvedorSocial;
import com.farmacia.farmaciaonline.social.IdentidadeExterna;

/**
 * Teste unitário puro (sem Spring), com o relógio controlado pelo teste.
 */
class DemoProvedorIdentidadeTest {

    private static final Instant INICIO = Instant.parse("2026-11-03T10:00:00Z");
    private static final IdentidadeExterna MARIA =
            new IdentidadeExterna(ProvedorSocial.GOOGLE, "sub-1", "Maria", "maria@exemplo.com", false);

    private final DemoProvedorIdentidade provedor = new DemoProvedorIdentidade();

    @Test
    void codigoDentroDaValidadeTrocaUmaVezSo() {
        String codigo = provedor.emitirCodigo(MARIA, INICIO);

        assertThat(provedor.trocarCodigo(ProvedorSocial.GOOGLE, codigo, INICIO.plusSeconds(299))).isEqualTo(MARIA);
        assertThatThrownBy(() -> provedor.trocarCodigo(ProvedorSocial.GOOGLE, codigo, INICIO.plusSeconds(300)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void codigoComMaisDeCincoMinutosEhRecusado() {
        String codigo = provedor.emitirCodigo(MARIA, INICIO);

        assertThatThrownBy(() -> provedor.trocarCodigo(ProvedorSocial.GOOGLE, codigo, INICIO.plusSeconds(301)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void codigosAbandonadosSaoVarridosAoEmitirUmNovo() {
        for (int i = 0; i < 100; i++) {
            provedor.emitirCodigo(MARIA, INICIO);
        }
        assertThat(provedor.quantidadeDeCodigos()).isEqualTo(100);

        provedor.emitirCodigo(MARIA, INICIO.plusSeconds(600));

        assertThat(provedor.quantidadeDeCodigos()).isEqualTo(1);
    }
}
