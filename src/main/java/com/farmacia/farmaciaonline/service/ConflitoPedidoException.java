package com.farmacia.farmaciaonline.service;

/** A tela foi aberta com uma versão anterior ao estado atualmente persistido. */
public class ConflitoPedidoException extends RuntimeException {
    public ConflitoPedidoException(String message) {
        super(message);
    }
}
