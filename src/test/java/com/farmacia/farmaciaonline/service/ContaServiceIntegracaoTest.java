package com.farmacia.farmaciaonline.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import com.farmacia.farmaciaonline.TestcontainersConfiguration;
import com.farmacia.farmaciaonline.model.ContaForm;
import com.farmacia.farmaciaonline.model.SenhaForm;
import com.farmacia.farmaciaonline.model.Usuario;
import com.farmacia.farmaciaonline.model.enums.Genero;
import com.farmacia.farmaciaonline.model.enums.Perfil;
import com.farmacia.farmaciaonline.model.enums.StatusUsuario;
import com.farmacia.farmaciaonline.repository.UsuarioRepository;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class ContaServiceIntegracaoTest {

    private static final String SENHA_DO_SEED = "Farma@123";

    @Autowired
    private ContaService service;

    @Autowired
    private UsuarioRepository usuarios;

    @Autowired
    private PasswordEncoder encoder;

    private Usuario cliente;

    @BeforeEach
    void cliente() {
        cliente = usuarios.findByEmailIgnoreCase("cliente@farma.com").orElseThrow();
    }

    private static ContaForm dados(String nome, LocalDate nascimento, Genero genero) {
        ContaForm form = new ContaForm();
        form.setNome(nome);
        form.setDataNascimento(nascimento);
        form.setGenero(genero);
        return form;
    }

    private static SenhaForm senhas(String atual, String nova, String confirmacao) {
        SenhaForm form = new SenhaForm();
        form.setSenhaAtual(atual);
        form.setNovaSenha(nova);
        form.setConfirmarNovaSenha(confirmacao);
        return form;
    }

    @Test
    @DisplayName("atualiza nome, nascimento e gênero e normaliza os espaços do nome")
    void atualizaDados() {
        service.atualizarDados(cliente.getId(), dados("  Joana    Souza ", LocalDate.of(1988, 7, 2), Genero.MASCULINO));

        Usuario salvo = usuarios.findById(cliente.getId()).orElseThrow();
        assertThat(salvo.getNome()).isEqualTo("Joana Souza");
        assertThat(salvo.getDataNascimento()).isEqualTo(LocalDate.of(1988, 7, 2));
        assertThat(salvo.getGenero()).isEqualTo(Genero.MASCULINO);
    }

    @Test
    @DisplayName("CPF, e-mail, perfil e status não mudam ao atualizar os dados")
    void camposProtegidosNaoMudam() {
        String cpf = cliente.getCpf();
        String email = cliente.getEmail();

        service.atualizarDados(cliente.getId(), dados("Joana Souza", LocalDate.of(1988, 7, 2), Genero.FEMININO));

        Usuario salvo = usuarios.findById(cliente.getId()).orElseThrow();
        assertThat(salvo.getCpf()).isEqualTo(cpf);
        assertThat(salvo.getEmail()).isEqualTo(email);
        assertThat(salvo.getPerfil()).isEqualTo(Perfil.CLIENTE);
        assertThat(salvo.getStatus()).isEqualTo(StatusUsuario.ATIVA);
    }

    @Test
    @DisplayName("menor de 18 anos, data futura e data antes de 1900 são recusados")
    void nascimentoInvalido() {
        LocalDate dezessete = LocalDate.now().minusYears(17);
        LocalDate futura = LocalDate.now().plusDays(1);
        LocalDate antiga = LocalDate.of(1899, 12, 31);

        for (LocalDate data : new LocalDate[] {dezessete, futura, antiga}) {
            assertThatThrownBy(() -> service.atualizarDados(cliente.getId(), dados("Joana Souza", data, Genero.FEMININO)))
                    .isInstanceOf(ContaInvalidaException.class)
                    .satisfies(e -> assertThat(((ContaInvalidaException) e).getErros()).containsKey("dataNascimento"));
        }
        assertThat(usuarios.findById(cliente.getId()).orElseThrow().getNome()).isEqualTo(cliente.getNome());
    }

    @Test
    @DisplayName("quem tem 18 anos hoje pode manter a conta")
    void exatamenteDezoitoAnos() {
        service.atualizarDados(cliente.getId(), dados("Joana Souza", LocalDate.now().minusYears(18), Genero.FEMININO));

        assertThat(usuarios.findById(cliente.getId()).orElseThrow().getNome()).isEqualTo("Joana Souza");
    }

    @Test
    @DisplayName("conta pendente não usa a área da conta")
    void contaPendenteEhRecusada() {
        Usuario pendente = usuarios.findByEmailIgnoreCase("pendente@farma.com").orElseThrow();

        assertThatThrownBy(() -> service.dados(pendente.getId())).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.atualizarDados(pendente.getId(), dados("Outro Nome", LocalDate.of(1990, 1, 1), Genero.FEMININO)))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.alterarSenha(pendente.getId(), senhas(SENHA_DO_SEED, "NovaSenha1", "NovaSenha1")))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("troca a senha com BCrypt quando a atual está certa e a nova confere")
    void trocaDeSenha() {
        service.alterarSenha(cliente.getId(), senhas(SENHA_DO_SEED, "NovaSenha1", "NovaSenha1"));

        String hash = usuarios.findById(cliente.getId()).orElseThrow().getSenhaHash();
        assertThat(hash).startsWith("$2");
        assertThat(encoder.matches("NovaSenha1", hash)).isTrue();
        assertThat(encoder.matches(SENHA_DO_SEED, hash)).isFalse();
    }

    @Test
    @DisplayName("senha atual errada não troca nada")
    void senhaAtualErrada() {
        String hashAntes = cliente.getSenhaHash();

        assertThatThrownBy(() -> service.alterarSenha(cliente.getId(), senhas("Errada123", "NovaSenha1", "NovaSenha1")))
                .isInstanceOf(ContaInvalidaException.class)
                .satisfies(e -> assertThat(((ContaInvalidaException) e).getErros()).containsKey("senhaAtual"));
        assertThat(usuarios.findById(cliente.getId()).orElseThrow().getSenhaHash()).isEqualTo(hashAntes);
    }

    @Test
    @DisplayName("confirmação diferente, nova igual à atual e senha longa demais são recusadas")
    void outrasRecusasDaSenha() {
        assertThatThrownBy(() -> service.alterarSenha(cliente.getId(), senhas(SENHA_DO_SEED, "NovaSenha1", "NovaSenha2")))
                .satisfies(e -> assertThat(((ContaInvalidaException) e).getErros()).containsKey("confirmarNovaSenha"));
        assertThatThrownBy(() -> service.alterarSenha(cliente.getId(), senhas(SENHA_DO_SEED, SENHA_DO_SEED, SENHA_DO_SEED)))
                .satisfies(e -> assertThat(((ContaInvalidaException) e).getErros()).containsKey("novaSenha"));
        String longa = "a1" + "x".repeat(80);
        assertThatThrownBy(() -> service.alterarSenha(cliente.getId(), senhas(SENHA_DO_SEED, longa, longa)))
                .satisfies(e -> assertThat(((ContaInvalidaException) e).getErros()).containsKey("novaSenha"));
    }

    @Test
    void dadosMostramCpfFormatadoESemCpfParaAdmin() {
        Usuario admin = usuarios.findByEmailIgnoreCase("admin@farma.com").orElseThrow();

        assertThat(service.dados(cliente.getId()).getCpfFormatado()).isEqualTo("529.982.247-25");
        assertThat(service.dados(admin.getId()).getCpfFormatado()).isNull();
    }
}
