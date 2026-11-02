package com.farmacia.farmaciaonline.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.farmacia.farmaciaonline.model.Endereco;

@Repository
public interface EnderecoRepository extends JpaRepository<Endereco, Long> {

    /** Endereços do usuário, do mais recente para o mais antigo. */
    List<Endereco> findByUsuarioIdOrderByCriadoEmDesc(Long usuarioId);

    Optional<Endereco> findByIdAndUsuarioId(Long id, Long usuarioId);

    /** F-25: o principal primeiro, depois do mais recente para o mais antigo (o checkout marca o primeiro da lista). */
    List<Endereco> findByUsuarioIdOrderByPrincipalDescCriadoEmDescIdDesc(Long usuarioId);

    boolean existsByUsuarioId(Long usuarioId);

    Optional<Endereco> findFirstByUsuarioIdOrderByCriadoEmDescIdDesc(Long usuarioId);

    /** Tira a marca de principal antes de marcar outro (o índice único parcial não aceita dois ao mesmo tempo). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Endereco e set e.principal = false where e.usuario.id = :usuarioId and e.principal = true")
    int desmarcarPrincipal(@Param("usuarioId") Long usuarioId);
}
