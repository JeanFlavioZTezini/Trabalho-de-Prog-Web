package com.farmacia.farmaciaonline.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import com.farmacia.farmaciaonline.TestcontainersConfiguration;
import com.farmacia.farmaciaonline.model.CadastroForm;
import com.farmacia.farmaciaonline.model.Usuario;
import com.farmacia.farmaciaonline.model.enums.Genero;
import com.farmacia.farmaciaonline.model.enums.Perfil;
import com.farmacia.farmaciaonline.model.enums.StatusUsuario;
import com.farmacia.farmaciaonline.repository.UsuarioRepository;

/**
 * Regras do cadastro contra o PostgreSQL real (V1 + seed do V2): cada teste é desfeito no fim (@Transactional).
 * O seed tem o cliente@farma.com (CPF 529.982.247-25) e o admin@farma.com.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class CadastroServiceIntegracaoTest {

    @Autowired
    private CadastroService service;

    @Autowired
    private UsuarioRepository usuarios;

    @Autowired
    private PasswordEncoder encoder;

    private static CadastroForm formValido() {
        CadastroForm form = new CadastroForm();
        form.setNome("  Ana   Paula  Souza ");
        form.setCpf("390.533.447-05");
        form.setDataNascimento(LocalDate.of(1990, 3, 15));
        form.setGenero(Genero.FEMININO);
        form.setCelular("(67) 99999-8888");
        form.setEmail("Ana.Souza@Exemplo.COM");
        form.setSenha("Senha123");
        form.setConfirmarSenha("Senha123");
        form.setAceiteTermos(true);
        return form;
    }

    @Test
    @DisplayName("grava conta PENDENTE de CLIENTE, com BCrypt, dados normalizados e a versão dos termos")
    void gravaContaPendenteDeCliente() {
        Usuario conta = service.cadastrar(formValido());

        Usuario salva = usuarios.findById(conta.getId()).orElseThrow();
        assertThat(salva.getStatus()).isEqualTo(StatusUsuario.PENDENTE);
        assertThat(salva.getPerfil()).isEqualTo(Perfil.CLIENTE);
        assertThat(salva.getNome()).isEqualTo("Ana Paula Souza");
        assertThat(salva.getCpf()).isEqualTo("39053344705");
        assertThat(salva.getCelular()).isEqualTo("67999998888");
        assertThat(salva.getEmail()).isEqualTo("ana.souza@exemplo.com");
        assertThat(salva.getGenero()).isEqualTo(Genero.FEMININO);
        assertThat(salva.getDataNascimento()).isEqualTo(LocalDate.of(1990, 3, 15));
        assertThat(salva.getTermosVersao()).isEqualTo("1.0");
        assertThat(salva.getTermosAceitosEm()).isBetween(Instant.now().minus(1, ChronoUnit.MINUTES), Instant.now());
        assertThat(salva.getTentativasLoginFalhas()).isZero();
        assertThat(salva.getSenhaHash()).startsWith("$2").isNotEqualTo("Senha123");
        assertThat(encoder.matches("Senha123", salva.getSenhaHash())).isTrue();
    }

    @Test
    @DisplayName("e-mail repetido é recusado sem diferenciar maiúsculas")
    void emailRepetidoIgnorandoCaixa() {
        CadastroForm form = formValido();
        form.setEmail("CLIENTE@Farma.com");

        assertThat(service.verificar(form)).containsOnlyKeys("email");
        assertThatThrownBy(() -> service.cadastrar(form)).isInstanceOfSatisfying(CadastroInvalidoException.class,
                e -> assertThat(e.isDuplicidade()).isTrue());
    }

    @Test
    @DisplayName("CPF repetido é recusado, com ou sem pontuação")
    void cpfRepetido() {
        CadastroForm comPontos = formValido();
        comPontos.setCpf("529.982.247-25");
        CadastroForm soNumeros = formValido();
        soNumeros.setCpf("52998224725");

        assertThat(service.verificar(comPontos)).containsOnlyKeys("cpf");
        assertThat(service.verificar(soNumeros)).containsOnlyKeys("cpf");
    }

    @Test
    @DisplayName("menor de 18 anos é recusado; quem faz 18 anos hoje passa")
    void idadeMinima() {
        LocalDate hoje = LocalDate.now();
        CadastroForm menor = formValido();
        menor.setDataNascimento(hoje.minusYears(18).plusDays(1));
        CadastroForm dezoito = formValido();
        dezoito.setDataNascimento(hoje.minusYears(18));

        assertThat(service.verificar(menor)).containsEntry("dataNascimento", "É preciso ter 18 anos ou mais para criar uma conta.");
        assertThat(service.verificar(dezoito)).isEmpty();
        assertThatThrownBy(() -> service.cadastrar(menor)).isInstanceOf(CadastroInvalidoException.class);
    }

    @Test
    @DisplayName("data no futuro ou antes de 1900 não é uma data de nascimento válida")
    void dataImpossivel() {
        CadastroForm futura = formValido();
        futura.setDataNascimento(LocalDate.now().plusDays(1));
        CadastroForm antiga = formValido();
        antiga.setDataNascimento(LocalDate.of(1899, 12, 31));

        assertThat(service.verificar(futura)).containsEntry("dataNascimento", "Informe uma data de nascimento válida.");
        assertThat(service.verificar(antiga)).containsEntry("dataNascimento", "Informe uma data de nascimento válida.");
    }

    @Test
    @DisplayName("senha e confirmação diferentes são recusadas")
    void senhaDivergente() {
        CadastroForm form = formValido();
        form.setConfirmarSenha("Senha124");

        assertThat(service.verificar(form)).containsOnlyKeys("confirmarSenha");
        assertThatThrownBy(() -> service.cadastrar(form)).isInstanceOf(CadastroInvalidoException.class);
        assertThat(usuarios.existsByCpf("39053344705")).isFalse();
    }

    @Test
    @DisplayName("senha acima de 72 bytes é recusada (limite do BCrypt); 72 passa")
    void senhaLongaDemais() {
        CadastroForm limite = formValido();
        limite.setSenha("a1" + "b".repeat(70));
        limite.setConfirmarSenha(limite.getSenha());
        CadastroForm longa = formValido();
        longa.setSenha("a1" + "b".repeat(71));
        longa.setConfirmarSenha(longa.getSenha());
        CadastroForm comAcentos = formValido();
        comAcentos.setSenha("ação1".repeat(15));
        comAcentos.setConfirmarSenha(comAcentos.getSenha());

        assertThat(service.verificar(limite)).isEmpty();
        assertThat(service.verificar(longa)).containsOnlyKeys("senha");
        assertThat(service.verificar(comAcentos)).containsOnlyKeys("senha");
        assertThat(service.cadastrar(limite).getId()).isNotNull();
    }

    @Test
    @DisplayName("duplicidade que só o banco enxerga (cadastros ao mesmo tempo) vira erro de campo, não erro 500")
    void corridaDeCadastroVirouErroDeCampo() {
        // A verificação "não achou ninguém"; o índice único do banco pega o segundo cadastro na gravação.
        UsuarioRepository semVerificar = mock(UsuarioRepository.class, delegatesTo(usuarios));
        doReturn(false).when(semVerificar).existsByCpf(anyString());
        doReturn(false).when(semVerificar).existsByEmailIgnoreCase(anyString());
        CadastroService servico = new CadastroService(semVerificar, encoder, "1.0");

        CadastroForm emailDoSeed = formValido();
        emailDoSeed.setEmail("cliente@farma.com");

        assertThatThrownBy(() -> servico.cadastrar(emailDoSeed)).isInstanceOfSatisfying(CadastroInvalidoException.class,
                e -> assertThat(e.getErros()).containsOnlyKeys("email"));
    }

    @Test
    @DisplayName("a mesma corrida com o CPF aponta o campo cpf")
    void corridaDeCadastroComCpf() {
        UsuarioRepository semVerificar = mock(UsuarioRepository.class, delegatesTo(usuarios));
        doReturn(false).when(semVerificar).existsByCpf(anyString());
        doReturn(false).when(semVerificar).existsByEmailIgnoreCase(anyString());
        CadastroService servico = new CadastroService(semVerificar, encoder, "1.0");

        CadastroForm cpfDoSeed = formValido();
        cpfDoSeed.setCpf("529.982.247-25");

        assertThatThrownBy(() -> servico.cadastrar(cpfDoSeed)).isInstanceOfSatisfying(CadastroInvalidoException.class,
                e -> assertThat(e.getErros()).containsOnlyKeys("cpf"));
    }

    @Test
    @DisplayName("formulário válido não tem nenhuma pendência")
    void formularioValidoNaoTemPendencia() {
        assertThat(service.verificar(formValido())).isEqualTo(Map.of());
    }
}
