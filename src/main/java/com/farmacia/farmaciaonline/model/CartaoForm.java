package com.farmacia.farmaciaonline.model;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CartaoForm {

    private String numeroCartao;
    private String validadeCartao;
    private String cvv;
    private String nomeImpresso;

    /** F-25: cartão salvo escolhido; o dono é conferido no serviço (id de outra pessoa é recusado). */
    private Long cartaoSalvoId;

    /** F-25: guardar o token do gateway depois de um pagamento aprovado. */
    private boolean salvarCartao;

    public boolean isUsaCartaoSalvo() {
        return cartaoSalvoId != null;
    }

    public List<String> errosDeFormato() {
        List<String> erros = new ArrayList<>();
        if (isUsaCartaoSalvo()) {
            // Com cartão salvo só o código de segurança é digitado de novo; o resto fica com o gateway.
            if (cvv == null || !cvv.trim().matches("\\d{3,4}")) {
                erros.add("Informe o código de segurança do cartão, com 3 ou 4 números.");
            }
            return erros;
        }
        if (numeroCartao == null || !numeroCartao.replaceAll("[\\s-]", "").matches("\\d{13,19}")) {
            erros.add("Informe o número do cartão, com 13 a 19 números.");
        }
        if (validadeCartao == null || !validadeCartao.trim().matches("(0[1-9]|1[0-2])/\\d{2}")) {
            erros.add("Informe a validade do cartão no formato MM/AA.");
        }
        if (cvv == null || !cvv.trim().matches("\\d{3,4}")) {
            erros.add("Informe o código de segurança do cartão, com 3 ou 4 números.");
        }
        if (nomeImpresso == null || nomeImpresso.isBlank()) {
            erros.add("Informe o nome impresso no cartão.");
        }
        return erros;
    }
}
