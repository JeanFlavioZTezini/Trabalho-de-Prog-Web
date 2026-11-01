package com.farmacia.farmaciaonline.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.farmacia.farmaciaonline.model.Usuario;
import com.farmacia.farmaciaonline.model.enums.Genero;

class DadosDaContaTest {

    @Test
    void formataCpfECelular() {
        assertThat(DadosDaConta.formatarCpf("52998224725")).isEqualTo("529.982.247-25");
        assertThat(DadosDaConta.formatarCelular("67999998888")).isEqualTo("(67) 99999-8888");
    }

    @Test
    void valorAusenteOuInvalidoNaoQuebra() {
        assertThat(DadosDaConta.formatarCpf(null)).isNull();
        assertThat(DadosDaConta.formatarCpf("123")).isNull();
        assertThat(DadosDaConta.formatarCelular(null)).isNull();
    }

    @Test
    void indicaQuaisContatosForamConfirmados() {
        Usuario usuario = Usuario.builder()
                .nome("Maria da Silva")
                .email("maria@farma.com")
                .celular("67999998888")
                .dataNascimento(LocalDate.of(1990, 3, 15))
                .genero(Genero.FEMININO)
                .emailConfirmadoEm(Instant.now())
                .build();

        DadosDaConta dados = DadosDaConta.de(usuario);

        assertThat(dados.isEmailConfirmado()).isTrue();
        assertThat(dados.isCelularConfirmado()).isFalse();
    }
}
