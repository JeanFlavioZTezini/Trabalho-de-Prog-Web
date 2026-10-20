package com.farmacia.farmaciaonline.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import com.farmacia.farmaciaonline.model.CodigoVerificacao;
import com.farmacia.farmaciaonline.model.IdentidadeSocial;
import com.farmacia.farmaciaonline.model.Usuario;
import com.farmacia.farmaciaonline.model.enums.CanalCodigo;
import com.farmacia.farmaciaonline.model.enums.FinalidadeCodigo;
import com.farmacia.farmaciaonline.model.enums.Genero;
import com.farmacia.farmaciaonline.model.enums.Perfil;
import com.farmacia.farmaciaonline.model.enums.ProvedorSocial;
import com.farmacia.farmaciaonline.model.enums.StatusUsuario;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class RepositoriesIntegrationTest {

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private IdentidadeSocialRepository identidadeSocialRepository;

    @Autowired
    private CodigoVerificacaoRepository codigoVerificacaoRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Usuario criarUsuario(String cpf, String email) {
        return Usuario.builder()
                .nome("Usuario Teste")
                .cpf(cpf)
                .email(email)
                .dataNascimento(LocalDate.of(1992, 8, 20))
                .genero(Genero.MASCULINO)
                .celular("11912345678")
                .senhaHash("hash-secreta")
                .perfil(Perfil.CLIENTE)
                .status(StatusUsuario.ATIVA)
                .termosVersao("1.0")
                .termosAceitosEm(Instant.now())
                .tentativasLoginFalhas(0)
                .build();
    }

    @Test
    @DisplayName("IdentidadeSocialRepository deve buscar por Provedor e ProvedorUsuarioId")
    void deveBuscarPorProvedorEProvedorUsuarioId() {
        Usuario usuario = usuarioRepository.saveAndFlush(criarUsuario("11122233344", "social@exemplo.com"));

        IdentidadeSocial identidade = IdentidadeSocial.builder()
                .usuario(usuario)
                .provedor(ProvedorSocial.GOOGLE)
                .provedorUsuarioId("google-sub-123456")
                .build();
        identidadeSocialRepository.saveAndFlush(identidade);
        entityManager.clear();

        Optional<IdentidadeSocial> encontrada = identidadeSocialRepository
                .findByProvedorAndProvedorUsuarioId(ProvedorSocial.GOOGLE, "google-sub-123456");

        assertThat(encontrada).isPresent();
        assertThat(encontrada.get().getUsuario().getId()).isEqualTo(usuario.getId());

        Optional<IdentidadeSocial> inexistente = identidadeSocialRepository
                .findByProvedorAndProvedorUsuarioId(ProvedorSocial.FACEBOOK, "google-sub-123456");
        assertThat(inexistente).isEmpty();
    }

    @Test
    @DisplayName("CodigoVerificacaoRepository deve buscar mais recente e contar reenvios no intervalo")
    void deveBuscarMaisRecenteEContarReenvios() {
        Usuario usuario = usuarioRepository.saveAndFlush(criarUsuario("55566677788", "codigo@exemplo.com"));

        Instant agora = Instant.now();

        CodigoVerificacao codigo1 = CodigoVerificacao.builder()
                .usuario(usuario)
                .finalidade(FinalidadeCodigo.CADASTRO)
                .canal(CanalCodigo.EMAIL)
                .destino("codigo@exemplo.com")
                .codigoHash("hash1")
                .expiraEm(agora.plus(10, ChronoUnit.MINUTES))
                .criadoEm(agora.minus(5, ChronoUnit.MINUTES))
                .build();

        CodigoVerificacao codigo2 = CodigoVerificacao.builder()
                .usuario(usuario)
                .finalidade(FinalidadeCodigo.CADASTRO)
                .canal(CanalCodigo.EMAIL)
                .destino("codigo@exemplo.com")
                .codigoHash("hash2")
                .expiraEm(agora.plus(10, ChronoUnit.MINUTES))
                .criadoEm(agora.minus(1, ChronoUnit.MINUTES))
                .build();

        codigoVerificacaoRepository.saveAndFlush(codigo1);
        codigoVerificacaoRepository.saveAndFlush(codigo2);
        entityManager.clear();

        Optional<CodigoVerificacao> maisRecente = codigoVerificacaoRepository
                .findFirstByUsuarioIdAndFinalidadeOrderByCriadoEmDesc(usuario.getId(), FinalidadeCodigo.CADASTRO);

        assertThat(maisRecente).isPresent();
        assertThat(maisRecente.get().getCodigoHash()).isEqualTo("hash2");

        long totalRecentes = codigoVerificacaoRepository
                .countByUsuarioIdAndFinalidadeAndCriadoEmAfter(usuario.getId(), FinalidadeCodigo.CADASTRO, agora.minus(10, ChronoUnit.MINUTES));
        assertThat(totalRecentes).isEqualTo(2);

        long totalUltimos2Min = codigoVerificacaoRepository
                .countByUsuarioIdAndFinalidadeAndCriadoEmAfter(usuario.getId(), FinalidadeCodigo.CADASTRO, agora.minus(2, ChronoUnit.MINUTES));
        assertThat(totalUltimos2Min).isEqualTo(1);
    }
}
