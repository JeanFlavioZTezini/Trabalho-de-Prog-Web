package com.farmacia.farmaciaonline.repository;

import java.util.Collection;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;

import com.farmacia.farmaciaonline.model.Usuario;
import com.farmacia.farmaciaonline.model.enums.StatusUsuario;

@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByEmailIgnoreCase(String email);

    Optional<Usuario> findByCpf(String cpf);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByCpf(String cpf);

    /** Busca a conta sob bloqueio pessimista para não perder incrementos concorrentes de falha de login. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from Usuario u where lower(u.email) = lower(:email) or (:cpf <> '' and u.cpf = :cpf)")
    Optional<Usuario> buscarParaAtualizarTentativasLogin(@Param("email") String email, @Param("cpf") String cpf);

    /**
     * Consulta administrativa de clientes (UC11): só perfil CLIENTE. {@code padraoTexto} é um LIKE em minúsculas
     * (com "%" nas pontas e %, _ e "!" escapados com "!") aplicado a nome e e-mail; {@code padraoCpf} é um LIKE só
     * com dígitos, ou "" quando o termo não tem dígitos suficientes. Nada é nulo para o PostgreSQL não reclamar
     * do tipo do parâmetro.
     */
    @Query(value = """
            select u from Usuario u
            where u.perfil = com.farmacia.farmaciaonline.model.enums.Perfil.CLIENTE
              and u.status in :statuses
              and (lower(u.nome) like :padraoTexto escape '!'
                   or lower(u.email) like :padraoTexto escape '!'
                   or u.cpf like :padraoCpf)
            """, countQuery = """
            select count(u) from Usuario u
            where u.perfil = com.farmacia.farmaciaonline.model.enums.Perfil.CLIENTE
              and u.status in :statuses
              and (lower(u.nome) like :padraoTexto escape '!'
                   or lower(u.email) like :padraoTexto escape '!'
                   or u.cpf like :padraoCpf)
            """)
    Page<Usuario> buscarClientes(@Param("statuses") Collection<StatusUsuario> statuses,
            @Param("padraoTexto") String padraoTexto, @Param("padraoCpf") String padraoCpf, Pageable pageable);
}
