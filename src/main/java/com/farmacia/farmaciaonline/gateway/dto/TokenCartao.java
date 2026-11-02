package com.farmacia.farmaciaonline.gateway.dto;

/**
 * Resposta da tokenização (F-25): o token só vale no gateway; o resto é o que pode ser mostrado ao cliente.
 * Número completo e CVV nunca voltam do gateway.
 */
public record TokenCartao(String token, String bandeira, String ultimos4, int validadeMes, int validadeAno) {

    @Override
    public String toString() {
        return "TokenCartao[bandeira=" + bandeira + ", ultimos4=" + ultimos4 + "]";
    }
}
