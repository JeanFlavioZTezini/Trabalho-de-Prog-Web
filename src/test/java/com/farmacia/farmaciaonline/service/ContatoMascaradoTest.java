package com.farmacia.farmaciaonline.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Teste unitário puro: sem Spring e sem banco.
 */
class ContatoMascaradoTest {

    @Test
    void emailMostraSoAPrimeiraLetraEODominio() {
        assertThat(ContatoMascarado.email("maria.silva@exemplo.com")).isEqualTo("m***@exemplo.com");
        assertThat(ContatoMascarado.email("a@b.com")).isEqualTo("a***@b.com");
    }

    @Test
    void celularMostraDddPrimeiroDigitoEOsQuatroUltimos() {
        assertThat(ContatoMascarado.celular("67999998888")).isEqualTo("(67) 9****-8888");
        assertThat(ContatoMascarado.celular("(11) 98765-4321")).isEqualTo("(11) 9****-4321");
    }

    @Test
    void contatoAusenteOuInvalidoViraVazio() {
        assertThat(ContatoMascarado.email(null)).isEmpty();
        assertThat(ContatoMascarado.email("sem-arroba")).isEmpty();
        assertThat(ContatoMascarado.email("@dominio.com")).isEmpty();
        assertThat(ContatoMascarado.celular(null)).isEmpty();
        assertThat(ContatoMascarado.celular("12345")).isEmpty();
    }
}
