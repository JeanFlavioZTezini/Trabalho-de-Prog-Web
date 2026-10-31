package com.farmacia.farmaciaonline.cep;

public class CepIndisponivelException extends RuntimeException {

    public CepIndisponivelException(String message) {
        super(message);
    }
}
