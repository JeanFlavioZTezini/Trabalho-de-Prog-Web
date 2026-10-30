package com.farmacia.farmaciaonline.service;

import java.util.List;

public class ProdutoException extends RuntimeException {

    private final List<String> erros;

    public ProdutoException(List<String> erros) {
        super(String.join(" ", erros));
        this.erros = erros;
    }

    public ProdutoException(String erro) {
        this(List.of(erro));
    }

    public List<String> getErros() {
        return erros;
    }
}
