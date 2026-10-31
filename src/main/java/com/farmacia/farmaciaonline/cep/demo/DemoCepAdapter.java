package com.farmacia.farmaciaonline.cep.demo;

import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.farmacia.farmaciaonline.cep.CepIndisponivelException;
import com.farmacia.farmaciaonline.cep.ConsultaCep;
import com.farmacia.farmaciaonline.cep.EnderecoCep;

@Service
public class DemoCepAdapter implements ConsultaCep {

    public static final String CEP_QUE_FALHA = "99999999";

    private static final Map<String, EnderecoCep> CONHECIDOS = Map.of(
            "01310100", new EnderecoCep("01310100", "Avenida Paulista", "Bela Vista", "São Paulo", "SP"),
            "04094050", new EnderecoCep("04094050", "Avenida Ibirapuera", "Moema", "São Paulo", "SP"),
            "01001000", new EnderecoCep("01001000", "Praça da Sé", "Sé", "São Paulo", "SP"),
            "20040020", new EnderecoCep("20040020", "Rua da Assembleia", "Centro", "Rio de Janeiro", "RJ"),
            "30130010", new EnderecoCep("30130010", "Avenida Afonso Pena", "Centro", "Belo Horizonte", "MG"));

    @Override
    public Optional<EnderecoCep> consultar(String cep) {
        String digitos = cep == null ? "" : cep.replaceAll("\\D", "");
        if (digitos.length() != 8) {
            return Optional.empty();
        }
        if (CEP_QUE_FALHA.equals(digitos)) {
            throw new CepIndisponivelException("Serviço de CEP indisponível (simulado).");
        }
        return Optional.ofNullable(CONHECIDOS.get(digitos));
    }
}
