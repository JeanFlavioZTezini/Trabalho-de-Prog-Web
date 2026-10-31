package com.farmacia.farmaciaonline.service;

import java.math.BigDecimal;
import java.util.List;

import com.farmacia.farmaciaonline.model.EnderecoEntrega;
import com.farmacia.farmaciaonline.model.Loja;
import com.farmacia.farmaciaonline.model.enums.MetodoPagamento;
import com.farmacia.farmaciaonline.model.enums.TipoEntrega;

public record ResumoCheckout(
        List<Item> itens,
        BigDecimal subtotal,
        BigDecimal frete,
        BigDecimal total,
        Integer prazoEntregaDias,
        boolean exigeReceita,
        boolean precoAlterado,
        List<String> problemas,
        TipoEntrega tipoEntrega,
        EnderecoEntrega endereco,
        Loja loja,
        MetodoPagamento metodo,
        String conferencia) {

    public record Item(
            Long apresentacaoId,
            String nomeProduto,
            String descricao,
            String sku,
            BigDecimal preco,
            BigDecimal precoVisto,
            int quantidade,
            boolean exigeReceita,
            BigDecimal subtotal) {

        public boolean isPrecoAlterado() {
            return preco.compareTo(precoVisto) != 0;
        }
    }

    public boolean isConfirmavel() {
        return problemas.isEmpty() && !precoAlterado;
    }
}
