package com.farmacia.farmaciaonline.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.farmacia.farmaciaonline.model.ImagemProduto;

@Repository
public interface ImagemProdutoRepository extends JpaRepository<ImagemProduto, Long> {

    /** Imagens de um produto, na ordem de exibição da galeria. */
    List<ImagemProduto> findByProdutoIdOrderByOrdemAsc(Long produtoId);
}
