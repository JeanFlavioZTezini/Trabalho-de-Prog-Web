package com.farmacia.farmaciaonline.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.farmacia.farmaciaonline.TestcontainersConfiguration;
import com.farmacia.farmaciaonline.model.SenhaForm;
import com.farmacia.farmaciaonline.model.Usuario;
import com.farmacia.farmaciaonline.model.enums.Perfil;
import com.farmacia.farmaciaonline.model.enums.StatusUsuario;
import com.farmacia.farmaciaonline.repository.UsuarioRepository;
import com.farmacia.farmaciaonline.security.LimiteDeTentativasEmMemoria;

/**
 * Senha conferida fora do login também tem limite de tentativas: troca de senha, troca de contato e retomada da
 * confirmação. Fora da transação do teste (NOT_SUPPORTED), para provar que a falha fica gravada mesmo com a operação
 * recusada; cada teste cria a própria conta e apaga no fim (o seed não fica bloqueado para os outros testes).
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class TentativasDeSenhaIntegracaoTest {

    private static final String SENHA = "Senha123";
    private static final String BLOQUEADA = "Muitas tentativas com a senha errada. Tente de novo em alguns minutos.";

    @Autowired private ContaService contaService;
    @Autowired private ContatoService contatoService;
    @Autowired private ConfirmacaoContaService confirmacaoService;
    @Autowired private UsuarioRepository usuarios;
    @Autowired private PasswordEncoder encoder;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private LimiteDeTentativasEmMemoria limiteEmMemoria;

    private Long criada;

    private Usuario novaConta(StatusUsuario status) {
        long n = ThreadLocalRandom.current().nextLong(1_000_000_000L, 9_999_999_999L);
        Usuario usuario = usuarios.saveAndFlush(Usuario.builder()
                .nome("Tentativa Teste")
                .email("tentativa" + n + "@teste.com")
                .cpf("9" + n)
                .dataNascimento(LocalDate.of(1990, 1, 1))
                .senhaHash(encoder.encode(SENHA))
                .perfil(Perfil.CLIENTE)
                .status(status)
                .termosVersao("1.0")
                .termosAceitosEm(Instant.now())
                .build());
        criada = usuario.getId();
        return usuario;
    }

    @AfterEach
    void limpar() {
        if (criada != null) {
            jdbc.update("DELETE FROM codigo_verificacao WHERE usuario_id = ?", criada);
            jdbc.update("DELETE FROM usuario WHERE id = ?", criada);
        }
        limiteEmMemoria.limparTudo();
    }

    private static SenhaForm trocaDeSenha(String atual) {
        SenhaForm form = new SenhaForm();
        form.setSenhaAtual(atual);
        form.setNovaSenha("OutraSenha456");
        form.setConfirmarNovaSenha("OutraSenha456");
        return form;
    }

    private Usuario recarregar(Long id) {
        return usuarios.findById(id).orElseThrow();
    }

    @Test
    @DisplayName("troca de senha: 5 senhas erradas bloqueiam e a 6ª tentativa, mesmo com a senha certa, é recusada")
    void trocaDeSenhaBloqueiaNaSextaTentativa() {
        Long id = novaConta(StatusUsuario.ATIVA).getId();

        for (int i = 1; i <= 5; i++) {
            assertThatThrownBy(() -> contaService.alterarSenha(id, trocaDeSenha("errada1")))
                    .isInstanceOf(ContaInvalidaException.class);
        }
        Usuario depois = recarregar(id);
        assertThat(depois.getTentativasLoginFalhas()).isEqualTo(5);
        assertThat(depois.getBloqueadoAte()).isAfter(Instant.now());

        assertThatThrownBy(() -> contaService.alterarSenha(id, trocaDeSenha(SENHA)))
                .isInstanceOfSatisfying(ContaInvalidaException.class,
                        e -> assertThat(e.getErros()).containsEntry("senhaAtual", BLOQUEADA));
        assertThat(encoder.matches(SENHA, recarregar(id).getSenhaHash())).as("a senha não mudou").isTrue();
    }

    @Test
    @DisplayName("troca de senha: a senha certa antes do bloqueio zera o contador")
    void senhaCertaZeraOContador() {
        Long id = novaConta(StatusUsuario.ATIVA).getId();
        for (int i = 1; i <= 2; i++) {
            assertThatThrownBy(() -> contaService.alterarSenha(id, trocaDeSenha("errada1")))
                    .isInstanceOf(ContaInvalidaException.class);
        }
        assertThat(recarregar(id).getTentativasLoginFalhas()).isEqualTo(2);

        contaService.alterarSenha(id, trocaDeSenha(SENHA));

        assertThat(recarregar(id).getTentativasLoginFalhas()).isZero();
    }

    @Test
    @DisplayName("troca de contato: 5 senhas erradas bloqueiam e a 6ª tentativa, mesmo com a senha certa, é recusada")
    void trocaDeContatoBloqueiaNaSextaTentativa() {
        Long id = novaConta(StatusUsuario.ATIVA).getId();

        for (int i = 1; i <= 5; i++) {
            assertThatThrownBy(() -> contatoService.solicitar(id, ContatoService.Tipo.EMAIL, "novo@teste.com", "errada1"))
                    .isInstanceOf(ContaInvalidaException.class);
        }
        assertThat(recarregar(id).getBloqueadoAte()).isAfter(Instant.now());

        assertThatThrownBy(() -> contatoService.solicitar(id, ContatoService.Tipo.EMAIL, "novo@teste.com", SENHA))
                .isInstanceOfSatisfying(ContaInvalidaException.class,
                        e -> assertThat(e.getErros()).containsEntry("senhaAtual", BLOQUEADA));
    }

    @Test
    @DisplayName("retomada da confirmação: 5 senhas erradas bloqueiam e a 6ª, mesmo certa, não devolve a conta")
    void retomadaBloqueiaNaSextaTentativa() {
        Usuario pendente = novaConta(StatusUsuario.PENDENTE);

        for (int i = 1; i <= 5; i++) {
            assertThat(confirmacaoService.retomar(pendente.getEmail(), "errada1")).isEmpty();
        }

        assertThat(confirmacaoService.retomar(pendente.getEmail(), SENHA)).isEmpty();
        // O CPF é outro identificador digitado: ele tem o próprio contador e ainda funciona.
        assertThat(confirmacaoService.retomar(pendente.getCpf(), SENHA)).isPresent();
    }

    @Test
    @DisplayName("retomada da confirmação: identificador inexistente também é bloqueado, igual à conta que existe")
    void retomadaBloqueiaIdentificadorInexistente() {
        String inexistente = "ninguem-" + ThreadLocalRandom.current().nextInt(1_000_000) + "@teste.com";

        for (int i = 1; i <= 5; i++) {
            assertThat(confirmacaoService.retomar(inexistente, "errada1")).isEmpty();
        }

        assertThat(limiteEmMemoria.bloqueado(inexistente)).isTrue();
    }

    @Test
    @DisplayName("retomada da confirmação: a senha certa antes do bloqueio zera o contador")
    void retomadaComSenhaCertaZeraOContador() {
        Usuario pendente = novaConta(StatusUsuario.PENDENTE);
        for (int i = 1; i <= 4; i++) {
            assertThat(confirmacaoService.retomar(pendente.getEmail(), "errada1")).isEmpty();
        }
        assertThat(confirmacaoService.retomar(pendente.getEmail(), SENHA)).isPresent();

        // Zerado: mais 4 erros ainda não bloqueiam.
        for (int i = 1; i <= 4; i++) {
            assertThat(confirmacaoService.retomar(pendente.getEmail(), "errada1")).isEmpty();
        }
        assertThat(limiteEmMemoria.bloqueado(pendente.getEmail())).isFalse();
    }
}
