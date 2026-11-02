package com.farmacia.farmaciaonline.service;

/** A conta não pode ser excluída agora (pedido em andamento, conta sem senha ou bloqueada por tentativas). */
public class ExclusaoContaRecusadaException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ExclusaoContaRecusadaException(String mensagem) {
        super(mensagem);
    }
}
