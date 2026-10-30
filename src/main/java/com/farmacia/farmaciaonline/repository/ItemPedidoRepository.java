package com.farmacia.farmaciaonline.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.farmacia.farmaciaonline.model.ItemPedido;

@Repository
public interface ItemPedidoRepository extends JpaRepository<ItemPedido, Long> {

    /** O produto já foi vendido? Então só pode ser inativado (UC12-E4). */
    boolean existsByApresentacaoProdutoId(Long produtoId);
}
