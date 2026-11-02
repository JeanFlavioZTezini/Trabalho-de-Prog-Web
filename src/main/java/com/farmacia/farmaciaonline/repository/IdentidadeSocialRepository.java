package com.farmacia.farmaciaonline.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.farmacia.farmaciaonline.model.IdentidadeSocial;
import com.farmacia.farmaciaonline.model.enums.ProvedorSocial;

@Repository
public interface IdentidadeSocialRepository extends JpaRepository<IdentidadeSocial, Long> {

    Optional<IdentidadeSocial> findByProvedorAndProvedorUsuarioId(ProvedorSocial provedor, String provedorUsuarioId);

    /** F-20: contas sociais vinculadas, para "Minha conta". */
    List<IdentidadeSocial> findByUsuarioIdOrderByCriadoEmAsc(Long usuarioId);

    boolean existsByUsuarioIdAndProvedor(Long usuarioId, ProvedorSocial provedor);
}
