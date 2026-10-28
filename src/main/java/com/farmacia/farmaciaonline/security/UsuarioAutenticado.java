package com.farmacia.farmaciaonline.security;

import java.util.Collection;
import java.util.List;

import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.farmacia.farmaciaonline.model.Usuario;
import com.farmacia.farmaciaonline.model.enums.Perfil;

/**
 * Principal da aplicação: é o objeto que o Spring Security guarda na sessão depois do login.
 *
 * Em vez do {@code User} genérico do Spring, carrega o que as telas precisam (id, nome e perfil),
 * então o cabeçalho mostra o primeiro nome sem consultar o banco a cada página.
 *
 * O "username" do Spring é o e-mail, mesmo quando a pessoa entrou com o CPF.
 * Só existe para contas que podem autenticar: {@link UsuarioDetailsService} recusa
 * PENDENTE, EXCLUIDA e conta bloqueada antes de chegar aqui.
 */
public class UsuarioAutenticado implements UserDetails, CredentialsContainer {

    private static final long serialVersionUID = 1L;

    private final Long id;
    private final String nome;
    private final Perfil perfil;
    private final String email;
    private String senhaHash;

    public UsuarioAutenticado(Long id, String nome, Perfil perfil, String email, String senhaHash) {
        this.id = id;
        this.nome = nome;
        this.perfil = perfil;
        this.email = email;
        // Conta criada por login social pode não ter senha: "" nunca confere com nenhuma senha digitada.
        this.senhaHash = senhaHash == null ? "" : senhaHash;
    }

    public static UsuarioAutenticado de(Usuario usuario) {
        return new UsuarioAutenticado(
                usuario.getId(), usuario.getNome(), usuario.getPerfil(), usuario.getEmail(), usuario.getSenhaHash());
    }

    public Long getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public Perfil getPerfil() {
        return perfil;
    }

    /** Primeiro nome, para o cabeçalho ("Maria da Silva" vira "Maria"). */
    public String getPrimeiroNome() {
        if (nome == null || nome.isBlank()) {
            return "";
        }
        return nome.trim().split("\\s+")[0];
    }

    /** O papel vira {@code ROLE_CLIENTE} ou {@code ROLE_ADMIN}; é o que {@code hasRole('ADMIN')} confere. */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + perfil.name()));
    }

    @Override
    public String getPassword() {
        return senhaHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    /** O Spring chama isto depois do login para o hash da senha não ficar guardado na sessão. */
    @Override
    public void eraseCredentials() {
        this.senhaHash = null;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
