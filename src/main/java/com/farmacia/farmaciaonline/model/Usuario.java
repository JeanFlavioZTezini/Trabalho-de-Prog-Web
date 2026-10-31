package com.farmacia.farmaciaonline.model;

import java.time.Instant;
import java.time.LocalDate;

import com.farmacia.farmaciaonline.model.enums.Genero;
import com.farmacia.farmaciaonline.model.enums.Perfil;
import com.farmacia.farmaciaonline.model.enums.StatusUsuario;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import java.util.Locale;

@Entity
@Table(name = "usuario")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
@ToString(exclude = "senhaHash")
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cpf", length = 11, unique = true)
    private String cpf;

    @Column(name = "nome", nullable = false, length = 150)
    private String nome;

    @Column(name = "data_nascimento", nullable = false)
    private LocalDate dataNascimento;

    @Enumerated(EnumType.STRING)
    @Column(name = "genero", length = 10)
    private Genero genero;

    @Column(name = "celular", length = 11)
    private String celular;

    @Column(name = "email", nullable = false, length = 254)
    private String email;

    @Column(name = "senha_hash", length = 100)
    private String senhaHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "perfil", nullable = false, length = 10)
    @Builder.Default
    private Perfil perfil = Perfil.CLIENTE;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    @Builder.Default
    private StatusUsuario status = StatusUsuario.PENDENTE;

    @Column(name = "termos_versao", nullable = false, length = 20)
    private String termosVersao;

    @Column(name = "termos_aceitos_em", nullable = false)
    private Instant termosAceitosEm;

    @Column(name = "tentativas_login_falhas", nullable = false)
    @Builder.Default
    private Integer tentativasLoginFalhas = 0;

    @Column(name = "bloqueado_ate")
    private Instant bloqueadoAte;

    /** Quando o e-mail foi confirmado por código (F-05). Nulo = ainda não confirmado. */
    @Column(name = "email_confirmado_em")
    private Instant emailConfirmadoEm;

    /** Quando o celular foi confirmado por código (F-05). Confirmar o e-mail não preenche este campo. */
    @Column(name = "celular_confirmado_em")
    private Instant celularConfirmadoEm;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    @Column(name = "excluido_em")
    private Instant excluidoEm;

    @PrePersist
    public void prePersist() {
        normalizarEmail();
        if (this.perfil == null) {
            this.perfil = Perfil.CLIENTE;
        }
        if (this.status == null) {
            this.status = StatusUsuario.PENDENTE;
        }
        if (this.tentativasLoginFalhas == null) {
            this.tentativasLoginFalhas = 0;
        }
        if (this.criadoEm == null) {
            this.criadoEm = Instant.now();
        }
        if (this.atualizadoEm == null) {
            this.atualizadoEm = Instant.now();
        }
    }

    @PreUpdate
    public void preUpdate() {
        normalizarEmail();
        this.atualizadoEm = Instant.now();
    }

    public void normalizarEmail() {
        if (this.email != null) {
            this.email = this.email.trim().toLowerCase(Locale.ROOT);
        }
    }
}
