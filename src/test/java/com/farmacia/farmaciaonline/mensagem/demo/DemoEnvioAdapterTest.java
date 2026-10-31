package com.farmacia.farmaciaonline.mensagem.demo;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.farmacia.farmaciaonline.mensagem.MensagemEnviada;
import com.farmacia.farmaciaonline.model.enums.CanalCodigo;

/**
 * Teste unitário puro: sem Spring e sem banco.
 */
class DemoEnvioAdapterTest {

    @Test
    void guardaAsMensagensDaMaisRecenteParaAMaisAntiga() {
        DemoEnvioAdapter caixa = new DemoEnvioAdapter();
        caixa.enviar(CanalCodigo.EMAIL, "a@exemplo.com", "Primeira", "texto 1");
        caixa.enviar(CanalCodigo.SMS, "67999998888", "Segunda", "texto 2");

        assertThat(caixa.mensagens()).extracting(MensagemEnviada::assunto).containsExactly("Segunda", "Primeira");
        assertThat(caixa.mensagens().get(0).canal()).isEqualTo(CanalCodigo.SMS);
    }

    @Test
    void guardaSoAsUltimasCinquenta() {
        DemoEnvioAdapter caixa = new DemoEnvioAdapter();
        for (int i = 1; i <= 55; i++) {
            caixa.enviar(CanalCodigo.EMAIL, "a@exemplo.com", "Mensagem " + i, "texto");
        }

        assertThat(caixa.mensagens()).hasSize(DemoEnvioAdapter.LIMITE);
        assertThat(caixa.mensagens().get(0).assunto()).isEqualTo("Mensagem 55");
        assertThat(caixa.mensagens().get(49).assunto()).isEqualTo("Mensagem 6");
    }
}
