package com.farmacia.farmaciaonline.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.farmacia.farmaciaonline.model.ItemPedido;

@Repository
public interface ItemPedidoRepository extends JpaRepository<ItemPedido, Long> {

    /** O produto já foi vendido? Então só pode ser inativado (UC12-E4). */
    boolean existsByApresentacaoProdutoId(Long produtoId);

    /** Total de unidades de cada pedido, numa consulta só (a lista de pedidos não carrega os itens um a um). */
    @Query("""
            select i.pedido.id as pedidoId, sum(i.quantidade) as unidades
            from ItemPedido i
            where i.pedido.id in :pedidoIds
            group by i.pedido.id
            """)
    List<UnidadesDoPedido> somarUnidadesPorPedido(@Param("pedidoIds") Collection<Long> pedidoIds);

    interface UnidadesDoPedido {
        Long getPedidoId();

        Long getUnidades();
    }
}
