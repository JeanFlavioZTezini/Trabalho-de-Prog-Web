package com.farmacia.farmaciaonline.cep.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.farmacia.farmaciaonline.cep.CepIndisponivelException;
import com.farmacia.farmaciaonline.cep.EnderecoCep;

class DemoCepAdapterTest {

    private final DemoCepAdapter adapter = new DemoCepAdapter();

    @Test
    void cepConhecidoVoltaComOEndereco() {
        EnderecoCep achado = adapter.consultar("01310100").orElseThrow();

        assertThat(achado.logradouro()).isEqualTo("Avenida Paulista");
        assertThat(achado.bairro()).isEqualTo("Bela Vista");
        assertThat(achado.cidade()).isEqualTo("São Paulo");
        assertThat(achado.uf()).isEqualTo("SP");
    }

    @Test
    void aceitaCepComTracoEComEspacos() {
        assertThat(adapter.consultar(" 01310-100 ")).isPresent();
    }

    @Test
    void cepDesconhecidoNaoELocalizado() {
        assertThat(adapter.consultar("12345678")).isEmpty();
    }

    @Test
    void textoSemOitoDigitosNaoELocalizado() {
        assertThat(adapter.consultar(null)).isEmpty();
        assertThat(adapter.consultar("")).isEmpty();
        assertThat(adapter.consultar("0131010")).isEmpty();
        assertThat(adapter.consultar("abc")).isEmpty();
    }

    @Test
    void cepQueFalhaSimulaServicoIndisponivel() {
        assertThatThrownBy(() -> adapter.consultar(DemoCepAdapter.CEP_QUE_FALHA))
                .isInstanceOf(CepIndisponivelException.class);
    }
}
