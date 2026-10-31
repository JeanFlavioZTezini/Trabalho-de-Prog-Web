package com.farmacia.farmaciaonline.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.farmacia.farmaciaonline.model.CodigoVerificacao;
import com.farmacia.farmaciaonline.model.enums.FinalidadeCodigo;

@Repository
public interface CodigoVerificacaoRepository extends JpaRepository<CodigoVerificacao, Long> {

    Optional<CodigoVerificacao> findFirstByUsuarioIdAndFinalidadeOrderByCriadoEmDesc(Long usuarioId, FinalidadeCodigo finalidade);

    long countByUsuarioIdAndFinalidadeAndCriadoEmAfter(Long usuarioId, FinalidadeCodigo finalidade, Instant criadoEm);

    /** Códigos ainda válidos (nem usados nem invalidados) de uma finalidade: um envio novo invalida todos. */
    List<CodigoVerificacao> findByUsuarioIdAndFinalidadeAndUsadoEmIsNullAndInvalidadoEmIsNull(Long usuarioId,
            FinalidadeCodigo finalidade);
}
