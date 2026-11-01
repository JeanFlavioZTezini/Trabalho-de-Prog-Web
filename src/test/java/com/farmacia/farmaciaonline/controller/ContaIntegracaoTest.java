package com.farmacia.farmaciaonline.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import com.farmacia.farmaciaonline.TestcontainersConfiguration;
import com.farmacia.farmaciaonline.model.Usuario;
import com.farmacia.farmaciaonline.model.enums.Perfil;
import com.farmacia.farmaciaonline.model.enums.StatusUsuario;
import com.farmacia.farmaciaonline.repository.UsuarioRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class ContaIntegracaoTest {

    private static final String EMAIL = "cliente@farma.com";
    private static final String SENHA = "Farma@123";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UsuarioRepository usuarios;

    @Autowired
    private PasswordEncoder encoder;

    private MockHttpSession entrar(String senha) throws Exception {
        MvcResult resultado = mvc.perform(post("/login").with(csrf())
                        .param("identificador", EMAIL).param("senha", senha))
                .andExpect(redirectedUrl("/"))
                .andReturn();
        return (MockHttpSession) resultado.getRequest().getSession(false);
    }

    private String pagina(String rota, MockHttpSession sessao) throws Exception {
        return mvc.perform(get(rota).session(sessao)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private Usuario cliente() {
        return usuarios.findByEmailIgnoreCase(EMAIL).orElseThrow();
    }

    @Test
    @DisplayName("visitante não abre a conta")
    void visitanteNaoAbre() throws Exception {
        mvc.perform(get("/conta")).andExpect(redirectedUrlPattern("**/login*"));
    }

    @Test
    @DisplayName("a conta mostra os dados e os contatos do próprio usuário")
    void mostraOsDadosDaConta() throws Exception {
        String conteudo = pagina("/conta", entrar(SENHA));

        assertThat(conteudo).contains("529.982.247-25", EMAIL, "ainda não está disponível");
    }

    @Test
    @DisplayName("editar os dados atualiza o banco e o nome do cabeçalho; CPF, e-mail, perfil e status forjados são ignorados")
    void editarAtualizaDadosEIgnoraCamposForjados() throws Exception {
        MockHttpSession sessao = entrar(SENHA);

        mvc.perform(post("/conta/editar").session(sessao).with(csrf())
                        .param("nome", "  Joana   Souza ")
                        .param("dataNascimento", "1990-03-15")
                        .param("genero", "FEMININO")
                        .param("cpf", "11144477735")
                        .param("email", "invasor@x.com")
                        .param("perfil", "ADMIN")
                        .param("status", "PENDENTE")
                        .param("id", "1"))
                .andExpect(redirectedUrl("/conta"));

        Usuario salvo = cliente();
        assertThat(salvo.getNome()).isEqualTo("Joana Souza");
        assertThat(salvo.getDataNascimento()).isEqualTo(LocalDate.of(1990, 3, 15));
        assertThat(salvo.getCpf()).isEqualTo("52998224725");
        assertThat(salvo.getEmail()).isEqualTo(EMAIL);
        assertThat(salvo.getPerfil()).isEqualTo(Perfil.CLIENTE);
        assertThat(salvo.getStatus()).isEqualTo(StatusUsuario.ATIVA);

        String conta = pagina("/conta", sessao);
        assertThat(conta).contains("Dados atualizados com sucesso.");
        assertThat(conta).contains("<strong>Joana</strong>");
    }

    @Test
    @DisplayName("menor de 18 anos volta ao formulário com erro e nada é salvo")
    void menorDeIdade() throws Exception {
        MockHttpSession sessao = entrar(SENHA);
        String nomeAntes = cliente().getNome();

        String conteudo = mvc.perform(post("/conta/editar").session(sessao).with(csrf())
                        .param("nome", "Joana Souza")
                        .param("dataNascimento", LocalDate.now().minusYears(17).toString())
                        .param("genero", "FEMININO"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(conteudo).contains("18 anos");
        assertThat(cliente().getNome()).isEqualTo(nomeAntes);
    }

    @Test
    @DisplayName("cancelar é só um link para a conta: abrir o formulário sem enviar não altera nada")
    void cancelarNaoSalva() throws Exception {
        MockHttpSession sessao = entrar(SENHA);
        String nomeAntes = cliente().getNome();

        String formulario = pagina("/conta/editar", sessao);

        assertThat(formulario).contains("Cancelar").contains("href=\"/conta\"");
        assertThat(cliente().getNome()).isEqualTo(nomeAntes);
    }

    @Test
    @DisplayName("trocar a senha: grava BCrypt, renova o id da sessão, mantém a sessão válida e a nova senha passa a valer")
    void trocarSenha() throws Exception {
        MockHttpSession sessao = entrar(SENHA);
        String idAntes = sessao.getId();

        mvc.perform(post("/conta/senha").session(sessao).with(csrf())
                        .param("senhaAtual", SENHA)
                        .param("novaSenha", "NovaSenha1")
                        .param("confirmarNovaSenha", "NovaSenha1"))
                .andExpect(redirectedUrl("/conta"));

        String hash = cliente().getSenhaHash();
        assertThat(hash).startsWith("$2");
        assertThat(encoder.matches("NovaSenha1", hash)).isTrue();
        assertThat(sessao.getId()).isNotEqualTo(idAntes);
        assertThat(pagina("/conta", sessao)).contains("Senha alterada com sucesso.");

        entrar("NovaSenha1");
        mvc.perform(post("/login").with(csrf()).param("identificador", EMAIL).param("senha", SENHA))
                .andExpect(redirectedUrl("/login?erro=credenciais"));
    }

    @Test
    @DisplayName("trocar a senha encerra as outras sessões da conta e mantém a atual")
    void trocarSenhaEncerraOutrasSessoes() throws Exception {
        MockHttpSession atual = entrar(SENHA);
        MockHttpSession outroNavegador = entrar(SENHA);

        mvc.perform(post("/conta/senha").session(atual).with(csrf())
                        .param("senhaAtual", SENHA)
                        .param("novaSenha", "NovaSenha1")
                        .param("confirmarNovaSenha", "NovaSenha1"))
                .andExpect(redirectedUrl("/conta"));

        mvc.perform(get("/conta").session(outroNavegador)).andExpect(redirectedUrl("/login?expirada"));
        mvc.perform(get("/conta").session(atual)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("senha atual errada não encerra nenhuma sessão")
    void senhaErradaNaoEncerraSessoes() throws Exception {
        MockHttpSession atual = entrar(SENHA);
        MockHttpSession outroNavegador = entrar(SENHA);

        mvc.perform(post("/conta/senha").session(atual).with(csrf())
                        .param("senhaAtual", "Errada123")
                        .param("novaSenha", "NovaSenha1")
                        .param("confirmarNovaSenha", "NovaSenha1"))
                .andExpect(status().isOk());

        mvc.perform(get("/conta").session(outroNavegador)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("senha atual errada não troca a senha")
    void senhaAtualErrada() throws Exception {
        MockHttpSession sessao = entrar(SENHA);
        String hashAntes = cliente().getSenhaHash();

        String conteudo = mvc.perform(post("/conta/senha").session(sessao).with(csrf())
                        .param("senhaAtual", "Errada123")
                        .param("novaSenha", "NovaSenha1")
                        .param("confirmarNovaSenha", "NovaSenha1"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(conteudo).contains("Senha atual incorreta.");
        assertThat(cliente().getSenhaHash()).isEqualTo(hashAntes);
        entrar(SENHA);
    }

    @Test
    @DisplayName("confirmação diferente e senha fora da política não trocam a senha")
    void outrasRecusas() throws Exception {
        MockHttpSession sessao = entrar(SENHA);
        String hashAntes = cliente().getSenhaHash();

        String diferente = mvc.perform(post("/conta/senha").session(sessao).with(csrf())
                        .param("senhaAtual", SENHA).param("novaSenha", "NovaSenha1").param("confirmarNovaSenha", "NovaSenha2"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String fraca = mvc.perform(post("/conta/senha").session(sessao).with(csrf())
                        .param("senhaAtual", SENHA).param("novaSenha", "semnumero").param("confirmarNovaSenha", "semnumero"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(diferente).contains("As senhas não conferem.");
        assertThat(fraca).contains("A senha deve ter 8 caracteres ou mais");
        assertThat(cliente().getSenhaHash()).isEqualTo(hashAntes);
    }
}
