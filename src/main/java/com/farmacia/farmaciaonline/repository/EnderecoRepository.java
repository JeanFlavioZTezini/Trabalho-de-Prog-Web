package com.farmacia.farmaciaonline.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.farmacia.farmaciaonline.model.Endereco;

@Repository
public interface EnderecoRepository extends JpaRepository<Endereco, Long> {

    /** Endereços do usuário, do mais recente para o mais antigo. */
    List<Endereco> findByUsuarioIdOrderByCriadoEmDesc(Long usuarioId);
}
