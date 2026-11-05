package com.farmacia.farmaciaonline.cep.viacep;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.farmacia.farmaciaonline.cep.CepIndisponivelException;
import com.farmacia.farmaciaonline.cep.EnderecoCep;

class ViaCepAdapterTest {

    private static final String URL = "https://viacep.test/ws";

    private MockRestServiceServer servidor;
    private ViaCepAdapter adapter;

    @BeforeEach
    void preparar() {
        RestClient.Builder builder = RestClient.builder();
        servidor = MockRestServiceServer.bindTo(builder).build();
        adapter = new ViaCepAdapter(builder, URL);
    }

    @Test
    void cepEncontradoViraEnderecoComCidadeDaLocalidade() {
        servidor.expect(requestTo(URL + "/79002336/json/")).andRespond(withSuccess("""
                {"cep": "79002-336", "logradouro": "Rua Quatorze de Julho", "complemento": "de 1852 a 2766 - lado par",
                 "bairro": "Centro", "localidade": "Campo Grande", "uf": "MS", "estado": "Mato Grosso do Sul"}
                """, MediaType.APPLICATION_JSON));

        EnderecoCep achado = adapter.consultar("79002-336").orElseThrow();

        assertThat(achado).isEqualTo(new EnderecoCep("79002336", "Rua Quatorze de Julho", "Centro", "Campo Grande", "MS"));
        servidor.verify();
    }

    @Test
    void cepInexistenteVoltaVazio() {
        servidor.expect(requestTo(URL + "/00000000/json/"))
                .andRespond(withSuccess("{\"erro\": \"true\"}", MediaType.APPLICATION_JSON));

        assertThat(adapter.consultar("00000-000")).isEmpty();
    }

    @Test
    void erroComoBooleanoTambemVoltaVazio() {
        servidor.expect(requestTo(URL + "/12345678/json/"))
                .andRespond(withSuccess("{\"erro\": true}", MediaType.APPLICATION_JSON));

        assertThat(adapter.consultar("12345678")).isEmpty();
    }

    @Test
    void cepGeralDaCidadeVemSemLogradouroEBairro() {
        servidor.expect(requestTo(URL + "/79950000/json/")).andRespond(withSuccess("""
                {"cep": "79950-000", "logradouro": "", "bairro": "", "localidade": "Naviraí", "uf": "MS"}
                """, MediaType.APPLICATION_JSON));

        assertThat(adapter.consultar("79950000")).contains(new EnderecoCep("79950000", "", "", "Naviraí", "MS"));
    }

    @Test
    void servicoForaDoArViraCepIndisponivel() {
        servidor.expect(requestTo(URL + "/79002336/json/")).andRespond(withServerError());

        assertThatThrownBy(() -> adapter.consultar("79002336")).isInstanceOf(CepIndisponivelException.class);
    }

    @Test
    void cepSemOitoNumerosNemChamaAApi() {
        assertThat(adapter.consultar("7900")).isEmpty();
        assertThat(adapter.consultar(null)).isEmpty();
        servidor.verify(); // nenhuma requisição esperada, nenhuma feita
    }
}
