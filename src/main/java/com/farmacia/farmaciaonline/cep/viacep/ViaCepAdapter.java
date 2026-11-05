package com.farmacia.farmaciaonline.cep.viacep;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.databind.JsonNode;
import com.farmacia.farmaciaonline.cep.CepIndisponivelException;
import com.farmacia.farmaciaonline.cep.ConsultaCep;
import com.farmacia.farmaciaonline.cep.EnderecoCep;

/**
 * Consulta o CEP na API pública do ViaCEP (https://viacep.com.br). CEP que não existe volta {@code {"erro": "true"}}
 * com status 200. Os tempos limite ficam em {@code spring.http.client.*} no application.properties.
 */
@Service
@ConditionalOnProperty(name = "app.cep.provedor", havingValue = "viacep", matchIfMissing = true)
public class ViaCepAdapter implements ConsultaCep {

    private final RestClient http;

    public ViaCepAdapter(RestClient.Builder builder, @Value("${app.cep.viacep.url:https://viacep.com.br/ws}") String url) {
        this.http = builder.baseUrl(url).build();
    }

    @Override
    public Optional<EnderecoCep> consultar(String cep) {
        String digitos = cep == null ? "" : cep.replaceAll("\\D", "");
        if (digitos.length() != 8) {
            return Optional.empty();
        }
        JsonNode resposta;
        try {
            resposta = http.get().uri("/{cep}/json/", digitos).retrieve().body(JsonNode.class);
        } catch (RestClientException e) {
            throw new CepIndisponivelException("ViaCEP indisponível: " + e.getMessage());
        }
        if (resposta == null || resposta.path("erro").asBoolean(false) || resposta.path("uf").asText().isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new EnderecoCep(digitos, texto(resposta, "logradouro"), texto(resposta, "bairro"),
                texto(resposta, "localidade"), texto(resposta, "uf")));
    }

    private static String texto(JsonNode no, String campo) {
        return no.path(campo).asText("").trim();
    }
}
