package com.farmacia.farmaciaonline.service;

/** A operação solicitada não é permitida para o estado ou modalidade atuais do pedido. */
public class AcaoPedidoInvalidaException extends RuntimeException {
    public AcaoPedidoInvalidaException(String message) {
        super(message);
    }
}
