package com.farmacia.farmaciaonline.service;

import java.util.Map;

public class ContaInvalidaException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final transient Map<String, String> erros;

    public ContaInvalidaException(Map<String, String> erros) {
        super("Dados da conta inválidos: " + erros);
        this.erros = Map.copyOf(erros);
    }

    public Map<String, String> getErros() {
        return erros;
    }
}
