package com.farmacia.farmaciaonline.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import com.farmacia.farmaciaonline.model.Usuario;
import com.farmacia.farmaciaonline.model.enums.Perfil;
import com.farmacia.farmaciaonline.model.enums.StatusUsuario;
import com.farmacia.farmaciaonline.repository.UsuarioRepository;

/** Teste unitário: o repository é um Mockito, não precisa de banco nem de Docker. */
@ExtendWith(MockitoExtension.class)
class UsuarioDetailsServiceTest {

    private static final String CPF = "52998224725";

    @Mock
    private UsuarioRepository usuarioRepository;

    @InjectMocks
    private UsuarioDetailsService service;

    private Usuario cliente;

    @BeforeEach
    void criarCliente() {
        cliente = Usuario.builder()
                .id(2L)
                .nome("Maria da Silva")
                .cpf(CPF)
                .email("cliente@farma.com")
                .dataNascimento(LocalDate.of(1995, 5, 20))
                .senhaHash("$2a$10$hash")
                .perfil(Perfil.CLIENTE)
                .status(StatusUsuario.ATIVA)
                .termosVersao("1.0")
                .termosAceitosEm(Instant.now())
                .build();
    }

    @Test
    @DisplayName("Entra por e-mail e recebe a autoridade ROLE_CLIENTE")
    void carregaPorEmail() {
        when(usuarioRepository.findByEmailIgnoreCase("cliente@farma.com")).thenReturn(Optional.of(cliente));

        UserDetails detalhes = service.loadUserByUsername("cliente@farma.com");

        assertThat(detalhes).isInstanceOf(UsuarioAutenticado.class);
        UsuarioAutenticado principal = (UsuarioAutenticado) detalhes;
        assertThat(principal.getId()).isEqualTo(2L);
        assertThat(principal.getNome()).isEqualTo("Maria da Silva");
        assertThat(principal.getPrimeiroNome()).isEqualTo("Maria");
        assertThat(principal.getPerfil()).isEqualTo(Perfil.CLIENTE);
        assertThat(principal.getUsername()).isEqualTo("cliente@farma.com");
        assertThat(principal.getPassword()).isEqualTo("$2a$10$hash");
        assertThat(principal.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_CLIENTE");
        verify(usuarioRepository, never()).findByCpf("cliente@farma.com");
    }

    @Test
    @DisplayName("E-mail com espaços nas pontas e letras maiúsculas é aceito")
    void emailComEspacosEMaiusculas() {
        when(usuarioRepository.findByEmailIgnoreCase("Cliente@Farma.com")).thenReturn(Optional.of(cliente));

        UserDetails detalhes = service.loadUserByUsername("  Cliente@Farma.com ");

        assertThat(detalhes.getUsername()).isEqualTo("cliente@farma.com");
    }

    @Test
    @DisplayName("Entra por CPF com máscara: só os 11 dígitos vão para a busca")
    void carregaPorCpfComMascara() {
        when(usuarioRepository.findByCpf(CPF)).thenReturn(Optional.of(cliente));

        UserDetails detalhes = service.loadUserByUsername("529.982.247-25");

        assertThat(detalhes.getUsername()).isEqualTo("cliente@farma.com");
        verify(usuarioRepository, never()).findByEmailIgnoreCase("529.982.247-25");
    }

    @Test
    @DisplayName("Entra por CPF sem máscara")
    void carregaPorCpfSemMascara() {
        when(usuarioRepository.findByCpf(CPF)).thenReturn(Optional.of(cliente));

        UserDetails detalhes = service.loadUserByUsername(CPF);

        assertThat(detalhes.getUsername()).isEqualTo("cliente@farma.com");
    }

    @Test
    @DisplayName("E-mail que contém 11 dígitos continua sendo e-mail, não CPF")
    void emailComOnzeDigitosNaoViraCpf() {
        when(usuarioRepository.findByEmailIgnoreCase("12345678901@exemplo.com")).thenReturn(Optional.of(cliente));

        service.loadUserByUsername("12345678901@exemplo.com");

        verify(usuarioRepository, never()).findByCpf("12345678901");
    }

    @Test
    @DisplayName("Admin recebe ROLE_ADMIN")
    void adminRecebeRoleAdmin() {
        Usuario admin = Usuario.builder()
                .id(1L)
                .nome("Administrador Farma++")
                .email("admin@farma.com")
                .dataNascimento(LocalDate.of(1990, 1, 15))
                .senhaHash("$2a$10$hash")
                .perfil(Perfil.ADMIN)
                .status(StatusUsuario.ATIVA)
                .termosVersao("1.0")
                .termosAceitosEm(Instant.now())
                .build();
        when(usuarioRepository.findByEmailIgnoreCase("admin@farma.com")).thenReturn(Optional.of(admin));

        UserDetails detalhes = service.loadUserByUsername("admin@farma.com");

        assertThat(detalhes.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_ADMIN");
    }

    @Test
    @DisplayName("Conta PENDENTE não autentica (DisabledException)")
    void contaPendenteNaoAutentica() {
        cliente.setStatus(StatusUsuario.PENDENTE);
        when(usuarioRepository.findByEmailIgnoreCase("cliente@farma.com")).thenReturn(Optional.of(cliente));

        assertThatThrownBy(() -> service.loadUserByUsername("cliente@farma.com"))
                .isInstanceOf(DisabledException.class);
    }

    @Test
    @DisplayName("Conta EXCLUIDA não autentica e é tratada como inexistente")
    void contaExcluidaNaoAutentica() {
        cliente.setStatus(StatusUsuario.EXCLUIDA);
        when(usuarioRepository.findByCpf(CPF)).thenReturn(Optional.of(cliente));

        assertThatThrownBy(() -> service.loadUserByUsername("529.982.247-25"))
                .isInstanceOf(UsernameNotFoundException.class);
    }

    @Test
    @DisplayName("bloqueadoAte no futuro lança LockedException")
    void contaBloqueadaLancaLockedException() {
        cliente.setBloqueadoAte(Instant.now().plus(15, ChronoUnit.MINUTES));
        when(usuarioRepository.findByEmailIgnoreCase("cliente@farma.com")).thenReturn(Optional.of(cliente));

        assertThatThrownBy(() -> service.loadUserByUsername("cliente@farma.com"))
                .isInstanceOf(LockedException.class);
    }

    @Test
    @DisplayName("bloqueadoAte que já passou não impede o login")
    void bloqueioVencidoNaoImpedeLogin() {
        cliente.setBloqueadoAte(Instant.now().minus(1, ChronoUnit.MINUTES));
        when(usuarioRepository.findByEmailIgnoreCase("cliente@farma.com")).thenReturn(Optional.of(cliente));

        assertThat(service.loadUserByUsername("cliente@farma.com").getUsername()).isEqualTo("cliente@farma.com");
    }

    @Test
    @DisplayName("Usuário inexistente lança UsernameNotFoundException")
    void usuarioInexistente() {
        when(usuarioRepository.findByEmailIgnoreCase("ninguem@farma.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.loadUserByUsername("ninguem@farma.com"))
                .isInstanceOf(UsernameNotFoundException.class);
    }

    @Test
    @DisplayName("Campo vazio ou nulo não chega ao banco")
    void campoVazioOuNulo() {
        assertThatThrownBy(() -> service.loadUserByUsername("   ")).isInstanceOf(UsernameNotFoundException.class);
        assertThatThrownBy(() -> service.loadUserByUsername(null)).isInstanceOf(UsernameNotFoundException.class);
    }

    @Test
    @DisplayName("Conta sem senha (login social) vira hash vazio, que nunca confere")
    void contaSemSenha() {
        cliente.setSenhaHash(null);
        when(usuarioRepository.findByEmailIgnoreCase("cliente@farma.com")).thenReturn(Optional.of(cliente));

        assertThat(service.loadUserByUsername("cliente@farma.com").getPassword()).isEmpty();
    }
}
