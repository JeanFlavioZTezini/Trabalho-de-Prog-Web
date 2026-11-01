package com.farmacia.farmaciaonline.service;

import java.math.BigDecimal;
import java.util.List;

import lombok.Value;

@Value
public class ResumoCarrinho {
    List<LinhaCarrinho> linhas;
    BigDecimal total;
    int totalUnidades;

    public static ResumoCarrinho vazio() {
        return new ResumoCarrinho(List.of(), BigDecimal.ZERO.setScale(2), 0);
    }

    public boolean isVazio() {
        return linhas.isEmpty();
    }

    public boolean isPodeFinalizar() {
        return !linhas.isEmpty() && linhas.stream().noneMatch(LinhaCarrinho::isComProblema);
    }

    public boolean isTemPrecoAlterado() {
        return linhas.stream().anyMatch(LinhaCarrinho::isPrecoAlterado);
    }

    @Value
    public static class LinhaCarrinho {
        Long itemId;
        Long produtoId;
        Long apresentacaoId;
        String nome;
        String descricao;
        String imagem;
        int quantidade;
        BigDecimal precoUnitario;
        BigDecimal precoVisto;
        boolean precoAlterado;
        BigDecimal subtotal;
        String problema;
        int maximoPermitido;

        public boolean isComProblema() {
            return problema != null;
        }
    }
}
