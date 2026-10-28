package com.farmacia.farmaciaonline.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Teste unitário puro: sem Spring e sem banco. As apresentações são montadas em memória.
 */
class ProdutoTest {

    /**
     * Os ids são distintos de propósito: o equals/hashCode das entidades é só pelo id, então
     * duas entidades novas (id nulo) seriam "iguais" e um teste com isEqualTo passaria por acaso.
     */
    private static Apresentacao apresentacao(long id, int ordem, boolean ativa, int estoque) {
        Apresentacao apresentacao = new Apresentacao();
        apresentacao.setId(id);
        apresentacao.setOrdem(ordem);
        apresentacao.setAtiva(ativa);
        apresentacao.setEstoque(estoque);
        return apresentacao;
    }

    private static Produto produtoCom(Apresentacao... apresentacoes) {
        Produto produto = new Produto();
        for (Apresentacao apresentacao : apresentacoes) {
            apresentacao.setProduto(produto);
            produto.getApresentacoes().add(apresentacao);
        }
        return produto;
    }

    @Nested
    @DisplayName("getApresentacaoPadrao()")
    class ApresentacaoPadrao {

        @Test
        @DisplayName("devolve a de menor ordem, mesmo com a lista fora de ordem")
        void deveDevolverAdeMenorOrdem() {
            Apresentacao de30 = apresentacao(3, 3, true, 5);
            Apresentacao de10 = apresentacao(1, 1, true, 5);
            Apresentacao de20 = apresentacao(2, 2, true, 5);

            Produto produto = produtoCom(de30, de10, de20);

            assertThat(produto.getApresentacaoPadrao()).isSameAs(de10);
        }

        @Test
        @DisplayName("não considera o estoque: a de menor ordem é a padrão mesmo sem estoque")
        void deveIgnorarEstoque() {
            Apresentacao semEstoque = apresentacao(1, 1, true, 0);
            Apresentacao comEstoque = apresentacao(2, 2, true, 50);

            Produto produto = produtoCom(comEstoque, semEstoque);

            assertThat(produto.getApresentacaoPadrao()).isSameAs(semEstoque);
        }

        @Test
        @DisplayName("não considera se está ativa: a de menor ordem é a padrão mesmo inativa")
        void deveIgnorarSeEstaAtiva() {
            Apresentacao inativa = apresentacao(1, 1, false, 50);
            Apresentacao ativa = apresentacao(2, 2, true, 50);

            Produto produto = produtoCom(ativa, inativa);

            assertThat(produto.getApresentacaoPadrao()).isSameAs(inativa);
        }

        @Test
        @DisplayName("devolve nulo quando o produto não tem apresentações")
        void deveDevolverNuloSemApresentacoes() {
            assertThat(new Produto().getApresentacaoPadrao()).isNull();
        }
    }

    @Nested
    @DisplayName("isIndisponivel()")
    class Indisponivel {

        @Test
        @DisplayName("é indisponível quando não tem nenhuma apresentação")
        void semApresentacoes() {
            assertThat(new Produto().isIndisponivel()).isTrue();
        }

        @Test
        @DisplayName("é indisponível quando todas as apresentações estão sem estoque")
        void todasSemEstoque() {
            Produto produto = produtoCom(
                    apresentacao(1, 1, true, 0),
                    apresentacao(2, 2, true, 0));

            assertThat(produto.isIndisponivel()).isTrue();
        }

        @Test
        @DisplayName("é indisponível quando todas as apresentações estão inativas, mesmo com estoque")
        void todasInativas() {
            Produto produto = produtoCom(
                    apresentacao(1, 1, false, 10),
                    apresentacao(2, 2, false, 10));

            assertThat(produto.isIndisponivel()).isTrue();
        }

        @Test
        @DisplayName("basta uma apresentação disponível, mesmo que não seja a padrão")
        void bastaUmaDisponivel() {
            Apresentacao padraoSemEstoque = apresentacao(1, 1, true, 0);
            Apresentacao outraDisponivel = apresentacao(2, 2, true, 3);

            Produto produto = produtoCom(padraoSemEstoque, outraDisponivel);

            assertThat(produto.getApresentacaoPadrao().isDisponivel()).isFalse();
            assertThat(produto.isIndisponivel()).isFalse();
        }

        @Test
        @DisplayName("não é indisponível quando todas as apresentações estão disponíveis")
        void todasDisponiveis() {
            Produto produto = produtoCom(
                    apresentacao(1, 1, true, 10),
                    apresentacao(2, 2, true, 20));

            assertThat(produto.isIndisponivel()).isFalse();
        }
    }
}
