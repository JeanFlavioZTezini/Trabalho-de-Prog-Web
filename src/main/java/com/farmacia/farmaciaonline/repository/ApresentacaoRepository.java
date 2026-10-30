package com.farmacia.farmaciaonline.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.farmacia.farmaciaonline.model.Apresentacao;

@Repository
public interface ApresentacaoRepository extends JpaRepository<Apresentacao, Long> {

    Optional<Apresentacao> findBySku(String sku);

    Optional<Apresentacao> findByEan(String ean);

    List<Apresentacao> findByProdutoId(Long produtoId);

    boolean existsBySku(String sku);

    boolean existsByEan(String ean);
}
