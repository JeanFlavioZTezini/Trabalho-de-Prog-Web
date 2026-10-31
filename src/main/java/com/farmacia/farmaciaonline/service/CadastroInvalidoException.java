package com.farmacia.farmaciaonline.service;

import java.util.Map;

/** O cadastro violou uma regra do serviço. {@code erros} guarda campo -> mensagem. */
public class CadastroInvalidoException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final transient Map<String, String> erros;

    public CadastroInvalidoException(Map<String, String> erros) {
        super("Cadastro inválido: " + erros);
        this.erros = Map.copyOf(erros);
    }

    public Map<String, String> getErros() {
        return erros;
    }

    /** CPF ou e-mail que já têm conta: a tela orienta a entrar ou recuperar a senha. */
    public boolean isDuplicidade() {
        return erros.containsKey("cpf") || erros.containsKey("email");
    }
}
