package com.farmacia.farmaciaonline.repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.farmacia.farmaciaonline.model.Apresentacao;

@Repository
public interface ApresentacaoRepository extends JpaRepository<Apresentacao, Long> {

    Optional<Apresentacao> findBySku(String sku);

    Optional<Apresentacao> findByEan(String ean);

    List<Apresentacao> findByProdutoId(Long produtoId);

    boolean existsBySku(String sku);

    boolean existsByEan(String ean);

    /**
     * Reserva estoque com atualização condicional: o banco trava a linha e reavalia o {@code where}, então duas
     * compras da última unidade não passam juntas, e compras com estoque de sobra não falham à toa (como falhariam só
     * com {@code @Version}). Sobe a {@code versao} para a tela de produto do admin, aberta antes, não sobrescrever o
     * estoque. Recusa se o preço mudou depois do que a pessoa confirmou.
     *
     * @return 1 se reservou; 0 se não há estoque, a apresentação foi inativada ou o preço mudou
     */
    @Modifying
    @Query("""
            update Apresentacao a set a.estoque = a.estoque - :quantidade, a.versao = a.versao + 1
            where a.id = :id and a.ativa = true and a.preco = :preco and a.estoque >= :quantidade
            """)
    int reservarEstoque(@Param("id") Long id, @Param("quantidade") int quantidade, @Param("preco") BigDecimal preco);

    /** Devolve unidades reservadas. Quem chama garante que só devolve uma vez (ver {@code EstadoPedidoService}). */
    @Modifying
    @Query("update Apresentacao a set a.estoque = a.estoque + :quantidade, a.versao = a.versao + 1 where a.id = :id")
    int devolverEstoque(@Param("id") Long id, @Param("quantidade") int quantidade);
}
