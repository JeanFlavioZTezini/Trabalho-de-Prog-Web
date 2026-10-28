package com.farmacia.farmaciaonline.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import com.farmacia.farmaciaonline.TestcontainersConfiguration;
import com.farmacia.farmaciaonline.model.Usuario;
import com.farmacia.farmaciaonline.model.enums.Genero;
import com.farmacia.farmaciaonline.model.enums.Perfil;
import com.farmacia.farmaciaonline.model.enums.StatusUsuario;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class UsuarioRepositoryTest {

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Usuario criarUsuarioValido(String cpf, String email) {
        return Usuario.builder()
                .nome("Maria Silva")
                .cpf(cpf)
                .email(email)
                .dataNascimento(LocalDate.of(1990, 5, 15))
                .genero(Genero.FEMININO)
                .celular("11987654321")
                .senhaHash("hash-seguro-123")
                .perfil(Perfil.CLIENTE)
                .status(StatusUsuario.ATIVA)
                .termosVersao("1.0")
                .termosAceitosEm(Instant.now())
                .tentativasLoginFalhas(0)
                .build();
    }

    @Test
    @DisplayName("Deve salvar usuario e buscar por email em maiusculas e por CPF")
    void deveSalvarUsuarioEBuscarPorEmailMaiusculoEPorCpf() {
        Usuario usuario = criarUsuarioValido("12345678901", "maria.silva@exemplo.com");
        usuarioRepository.saveAndFlush(usuario);
        entityManager.clear();

        // Busca por e-mail em maiúsculas (UC10 / busca case-insensitive)
        Optional<Usuario> porEmail = usuarioRepository.findByEmailIgnoreCase("MARIA.SILVA@EXEMPLO.COM");
        assertThat(porEmail).isPresent();
        assertThat(porEmail.get().getCpf()).isEqualTo("12345678901");
        assertThat(porEmail.get().getNome()).isEqualTo("Maria Silva");

        // Busca por CPF
        Optional<Usuario> porCpf = usuarioRepository.findByCpf("12345678901");
        assertThat(porCpf).isPresent();
        assertThat(porCpf.get().getEmail()).isEqualTo("maria.silva@exemplo.com");

        // existsBy
        assertThat(usuarioRepository.existsByEmailIgnoreCase("MARIA.SILVA@EXEMPLO.COM")).isTrue();
        assertThat(usuarioRepository.existsByCpf("12345678901")).isTrue();
        assertThat(usuarioRepository.existsByEmailIgnoreCase("outro@exemplo.com")).isFalse();
        assertThat(usuarioRepository.existsByCpf("99999999999")).isFalse();
    }

    @Test
    @DisplayName("Deve normalizar o email no prePersist removendo espacos e convertendo para minusculo")
    void deveNormalizarEmailNoPrePersist() {
        Usuario usuario = criarUsuarioValido("98765432100", "   Joao.Silva@Exemplo.COM   ");
        Usuario salvo = usuarioRepository.saveAndFlush(usuario);
        entityManager.clear();

        Usuario encontrado = usuarioRepository.findById(salvo.getId()).orElseThrow();
        assertThat(encontrado.getEmail()).isEqualTo("joao.silva@exemplo.com");
    }

    @Test
    @DisplayName("Permite usuario com cpf, genero, celular e senhaHash nulos (ex: conta social)")
    void permiteCamposNulosParaContaSocial() {
        // Para CPF nulo no banco V1, a constraint ck_usuario_cpf_obrig exige perfil ADMIN ou status EXCLUIDA
        Usuario adminSocial = Usuario.builder()
                .nome("Admin Social")
                .email("admin.social@exemplo.com")
                .dataNascimento(LocalDate.of(1985, 1, 1))
                .perfil(Perfil.ADMIN)
                .senhaHash("admin-hash")
                .status(StatusUsuario.ATIVA)
                .termosVersao("1.0")
                .termosAceitosEm(Instant.now())
                .cpf(null)
                .genero(null)
                .celular(null)
                .build();

        Usuario salvo = usuarioRepository.saveAndFlush(adminSocial);
        entityManager.clear();

        Usuario recuperado = usuarioRepository.findById(salvo.getId()).orElseThrow();
        assertThat(recuperado.getCpf()).isNull();
        assertThat(recuperado.getGenero()).isNull();
        assertThat(recuperado.getCelular()).isNull();
    }

    @Test
    @DisplayName("Rejeita segundo usuario com o mesmo e-mail mesmo com maiusculas/minusculas diferentes (RN02)")
    void rejeitaEmailDuplicadoIgnorandoCaixa() {
        usuarioRepository.saveAndFlush(criarUsuarioValido("12345678901", "maria@exemplo.com"));

        Usuario outro = criarUsuarioValido("98765432100", "MARIA@Exemplo.com");

        assertThatThrownBy(() -> usuarioRepository.saveAndFlush(outro))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Rejeita segundo usuario com o mesmo CPF (RN01)")
    void rejeitaCpfDuplicado() {
        usuarioRepository.saveAndFlush(criarUsuarioValido("12345678901", "um@exemplo.com"));

        Usuario outro = criarUsuarioValido("12345678901", "dois@exemplo.com");

        assertThatThrownBy(() -> usuarioRepository.saveAndFlush(outro))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}