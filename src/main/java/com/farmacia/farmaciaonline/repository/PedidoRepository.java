package com.farmacia.farmaciaonline.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.farmacia.farmaciaonline.model.Pedido;
import com.farmacia.farmaciaonline.model.enums.StatusPedido;

@Repository
public interface PedidoRepository extends JpaRepository<Pedido, Long> {

    /** Pedidos do usuário, do mais recente para o mais antigo (UC06). */
    Page<Pedido> findByUsuarioIdOrderByCriadoEmDesc(Long usuarioId, Pageable pageable);

    /** Igual ao anterior, com desempate pelo número: dois pedidos no mesmo instante não trocam de lugar entre páginas. */
    Page<Pedido> findByUsuarioIdOrderByCriadoEmDescIdDesc(Long usuarioId, Pageable pageable);

    /**
     * Lista administrativa (UC17 + F-23): número exato opcional; status, período (fim exclusivo) e cliente (LIKE em
     * minúsculas com escape "!" sobre nome e e-mail, ou só dígitos sobre o CPF) sempre informados, com valores
     * "todos" quando a tela não filtra. A relação é to-one, segura para paginação.
     */
    @Query(value = """
            select p from Pedido p join fetch p.usuario u
            where (:numero is null or p.id = :numero)
              and p.status in :statuses
              and p.criadoEm >= :inicio and p.criadoEm < :fim
              and (lower(u.nome) like :padraoTexto escape '!'
                   or lower(u.email) like :padraoTexto escape '!'
                   or u.cpf like :padraoCpf)
            """,
            countQuery = """
            select count(p) from Pedido p join p.usuario u
            where (:numero is null or p.id = :numero)
              and p.status in :statuses
              and p.criadoEm >= :inicio and p.criadoEm < :fim
              and (lower(u.nome) like :padraoTexto escape '!'
                   or lower(u.email) like :padraoTexto escape '!'
                   or u.cpf like :padraoCpf)
            """)
    Page<Pedido> buscarParaAdministracao(@Param("numero") Long numero,
            @Param("statuses") Collection<StatusPedido> statuses, @Param("inicio") Instant inicio,
            @Param("fim") Instant fim, @Param("padraoTexto") String padraoTexto, @Param("padraoCpf") String padraoCpf,
            Pageable pageable);

    /** "Meus pedidos" com status e período (F-23): do mais recente para o mais antigo, desempate pelo número. */
    @Query("""
            select p from Pedido p
            where p.usuario.id = :usuarioId
              and p.status in :statuses
              and p.criadoEm >= :inicio and p.criadoEm < :fim
            order by p.criadoEm desc, p.id desc
            """)
    Page<Pedido> buscarDoCliente(@Param("usuarioId") Long usuarioId,
            @Param("statuses") Collection<StatusPedido> statuses, @Param("inicio") Instant inicio,
            @Param("fim") Instant fim, Pageable pageable);

    /** Garante que o usuário só enxerga os próprios pedidos (UC06): de outro usuário, vem vazio. */
    Optional<Pedido> findByIdAndUsuarioId(Long id, Long usuarioId);

    Optional<Pedido> findByUsuarioIdAndChaveIdempotencia(Long usuarioId, UUID chave);

    /**
     * Trava a linha até o fim da transação, sem carregar a entidade. Não dá para travar com
     * {@code em.refresh(pedido, PESSIMISTIC_WRITE)}: em entidade com {@code @Version}, o Hibernate compara a versão lida
     * antes da espera, e quem esperou a trava falharia com {@code OptimisticLockException} em vez de ver o estado novo.
     */
    @Query(value = "select id from pedido where id = :id for update", nativeQuery = true)
    Optional<Long> travarLinha(@Param("id") Long id);

    /** Pedidos com a reserva de estoque vencida. */
    @Query("""
            select p.id from Pedido p
            where p.status = com.farmacia.farmaciaonline.model.enums.StatusPedido.AGUARDANDO_PAGAMENTO
              and p.expiraEm < :agora
            """)
    List<Long> idsComReservaVencida(@Param("agora") Instant agora);

    /** Traz o que o texto do resumo usa, para montá-lo sem transação aberta durante o envio. */
    @Query("select p from Pedido p join fetch p.usuario left join fetch p.itens where p.id = :id")
    Optional<Pedido> buscarParaResumo(@Param("id") Long id);

    /**
     * Reserva o envio do resumo: só uma requisição recebe 1, então um duplo clique não manda duas mensagens.
     * Não mexe em {@code versao}: o envio não é mudança de status do pedido.
     */
    @Transactional
    @Modifying
    @Query("update Pedido p set p.resumoEnviadoEm = :agora where p.id = :id and p.resumoEnviadoEm is null")
    int marcarResumoEnviado(@Param("id") Long id, @Param("agora") Instant agora);

    /** O envio falhou: volta a "não enviado" para a tela oferecer o reenvio. */
    @Transactional
    @Modifying
    @Query("update Pedido p set p.resumoEnviadoEm = null where p.id = :id")
    int desmarcarResumoEnviado(@Param("id") Long id);
}
