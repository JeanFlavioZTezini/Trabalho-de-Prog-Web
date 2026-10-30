package com.farmacia.farmaciaonline.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Teste unitário puro: sem Spring e sem banco. Os valores são comparados com
 * {@code isEqualTo(new BigDecimal(...))}, que também confere a escala (2 casas).
 */
class PedidoTest {

    private static ItemPedido item(String precoUnitario, int quantidade, boolean exigeReceita) {
        ItemPedido item = new ItemPedido();
        item.setPrecoUnitario(new BigDecimal(precoUnitario));
        item.setQuantidade(quantidade);
        item.setExigeReceita(exigeReceita);
        return item;
    }

    private static Pedido pedidoCom(String frete, ItemPedido... itens) {
        Pedido pedido = new Pedido();
        pedido.setFrete(frete == null ? null : new BigDecimal(frete));
        for (ItemPedido item : itens) {
            item.setPedido(pedido);
            pedido.getItens().add(item);
        }
        return pedido;
    }

    @Nested
    @DisplayName("temItemQueExigeReceita()")
    class ExigeReceita {

        @Test
        @DisplayName("é falso sem itens")
        void semItens() {
            assertThat(new Pedido().temItemQueExigeReceita()).isFalse();
        }

        @Test
        @DisplayName("é falso quando nenhum item exige receita")
        void nenhumExige() {
            Pedido pedido = pedidoCom("0", item("10.00", 1, false), item("5.00", 2, false));

            assertThat(pedido.temItemQueExigeReceita()).isFalse();
        }

        @Test
        @DisplayName("é verdadeiro quando só um item exige receita")
        void umExige() {
            Pedido pedido = pedidoCom("0", item("10.00", 1, false), item("29.90", 1, true));

            assertThat(pedido.temItemQueExigeReceita()).isTrue();
        }

        @Test
        @DisplayName("é verdadeiro quando todos os itens exigem receita")
        void todosExigem() {
            Pedido pedido = pedidoCom("0", item("29.90", 1, true), item("18.50", 1, true));

            assertThat(pedido.temItemQueExigeReceita()).isTrue();
        }
    }

    @Nested
    @DisplayName("recalcularTotais()")
    class RecalcularTotais {

        @Test
        @DisplayName("subtotal é a soma de preço x quantidade e total é subtotal + frete")
        void somaItensMaisFrete() {
            Pedido pedido = pedidoCom("9.90", item("30.99", 2, false), item("12.90", 1, false));

            pedido.recalcularTotais();

            assertThat(pedido.getSubtotal()).isEqualTo(new BigDecimal("74.88"));
            assertThat(pedido.getFrete()).isEqualTo(new BigDecimal("9.90"));
            assertThat(pedido.getTotal()).isEqualTo(new BigDecimal("84.78"));
        }

        @Test
        @DisplayName("frete zero (retirada): total igual ao subtotal, com 2 casas")
        void freteZero() {
            Pedido pedido = pedidoCom("0", item("29.90", 1, true));

            pedido.recalcularTotais();

            assertThat(pedido.getFrete()).isEqualTo(new BigDecimal("0.00"));
            assertThat(pedido.getTotal()).isEqualTo(new BigDecimal("29.90"));
        }

        @Test
        @DisplayName("frete nulo conta como zero")
        void freteNulo() {
            Pedido pedido = pedidoCom(null, item("10.00", 3, false));

            pedido.recalcularTotais();

            assertThat(pedido.getFrete()).isEqualTo(new BigDecimal("0.00"));
            assertThat(pedido.getTotal()).isEqualTo(new BigDecimal("30.00"));
        }

        @Test
        @DisplayName("sem itens: subtotal zero e total igual ao frete")
        void semItens() {
            Pedido pedido = pedidoCom("7.50");

            pedido.recalcularTotais();

            assertThat(pedido.getSubtotal()).isEqualTo(new BigDecimal("0.00"));
            assertThat(pedido.getTotal()).isEqualTo(new BigDecimal("7.50"));
        }

        @Test
        @DisplayName("arredonda cada item e o frete em HALF_UP, e o total continua sendo subtotal + frete")
        void arredondamento() {
            Pedido pedido = pedidoCom("5.005", item("10.005", 1, false), item("0.333", 3, false));

            pedido.recalcularTotais();

            assertThat(pedido.getSubtotal()).isEqualTo(new BigDecimal("11.01"));
            assertThat(pedido.getFrete()).isEqualTo(new BigDecimal("5.01"));
            assertThat(pedido.getTotal()).isEqualTo(new BigDecimal("16.02"));
            assertThat(pedido.getTotal()).isEqualTo(pedido.getSubtotal().add(pedido.getFrete()));
        }

        @Test
        @DisplayName("arredonda por item, e não só a soma: 0,333 + 0,333 dá 0,66")
        void arredondaPorItem() {
            Pedido pedido = pedidoCom("0", item("0.333", 1, false), item("0.333", 1, false));

            pedido.recalcularTotais();

            assertThat(pedido.getSubtotal()).isEqualTo(new BigDecimal("0.66"));
        }

        @Test
        @DisplayName("recalcular de novo depois de mudar os itens atualiza os totais")
        void recalcularDeNovo() {
            Pedido pedido = pedidoCom("10.00", item("20.00", 1, false));
            pedido.recalcularTotais();

            pedido.getItens().add(item("5.00", 2, false));
            pedido.recalcularTotais();

            assertThat(pedido.getSubtotal()).isEqualTo(new BigDecimal("30.00"));
            assertThat(pedido.getTotal()).isEqualTo(new BigDecimal("40.00"));
        }
    }
}
