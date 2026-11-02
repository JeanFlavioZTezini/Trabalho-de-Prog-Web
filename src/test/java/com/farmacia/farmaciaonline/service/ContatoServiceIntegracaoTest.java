package com.farmacia.farmaciaonline.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;

import com.farmacia.farmaciaonline.TestcontainersConfiguration;
import com.farmacia.farmaciaonline.mensagem.MensagemEnviada;
import com.farmacia.farmaciaonline.mensagem.demo.DemoEnvioAdapter;
import com.farmacia.farmaciaonline.model.Usuario;
import com.farmacia.farmaciaonline.model.enums.CanalCodigo;
import com.farmacia.farmaciaonline.model.enums.FinalidadeCodigo;
import com.farmacia.farmaciaonline.repository.UsuarioRepository;
import com.farmacia.farmaciaonline.service.CodigoVerificacaoService.SituacaoEnvio;
import com.farmacia.farmaciaonline.service.CodigoVerificacaoService.SituacaoValidacao;
import com.farmacia.farmaciaonline.service.ContatoService.ResultadoConfirmacao;
import com.farmacia.farmaciaonline.service.ContatoService.Tipo;

import jakarta.persistence.EntityManager;

/**
 * UC07 / F-21 contra o PostgreSQL real: o contato só muda depois do código enviado ao destino novo. A caixa do
 * DemoEnvioAdapter fica na memória entre testes, então tudo compara antes e depois (ou procura pelo destino).
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class ContatoServiceIntegracaoTest {

    private static final String SENHA = "Farma@123";
    private static final Pattern SEIS_DIGITOS = Pattern.compile("\\b(\\d{6})\\b");

    @Autowired
    private ContatoService service;

    @Autowired
    private CodigoVerificacaoService codigos;

    @Autowired
    private UsuarioRepository usuarios;

    @Autowired
    private DemoEnvioAdapter caixa;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManager em;

    private Usuario cliente;

    @BeforeEach
    void cliente() {
        cliente = usuarios.findByEmailIgnoreCase("cliente@farma.com").orElseThrow();
    }

    /** Código da mensagem mais recente enviada a esse destino. */
    private String codigoPara(String destino) {
        MensagemEnviada mensagem = caixa.mensagens().stream().filter(m -> m.destino().equals(destino)).findFirst()
                .orElseThrow();
        Matcher m = SEIS_DIGITOS.matcher(mensagem.texto());
        assertThat(m.find()).isTrue();
        return m.group(1);
    }

    private long mensagensPara(String destino) {
        return caixa.mensagens().stream().filter(m -> m.destino().equals(destino)).count();
    }

    private Usuario recarregar() {
        em.flush();
        em.clear();
        return usuarios.findById(cliente.getId()).orElseThrow();
    }

    @Test
    @DisplayName("pedir a troca envia o código ao e-mail NOVO e não altera o e-mail atual")
    void solicitarEnviaAoNovoDestinoSemAlterarAConta() {
        String atual = cliente.getEmail();
        long antes = mensagensPara("novo.cliente.f21@teste.com");

        var envio = service.solicitar(cliente.getId(), Tipo.EMAIL, "Novo.Cliente.F21@Teste.com ", SENHA);

        assertThat(envio.situacao()).isEqualTo(SituacaoEnvio.ENVIADO);
        assertThat(mensagensPara("novo.cliente.f21@teste.com")).isEqualTo(antes + 1);
        assertThat(caixa.mensagens().get(0).canal()).isEqualTo(CanalCodigo.EMAIL);
        assertThat(recarregar().getEmail()).isEqualTo(atual);
    }

    @Test
    @DisplayName("confirmar o e-mail troca o e-mail, confirma só ele e não mexe no celular")
    void confirmarTrocaOEmail() {
        var celularAntes = cliente.getCelular();
        var celularConfirmadoAntes = cliente.getCelularConfirmadoEm();
        service.solicitar(cliente.getId(), Tipo.EMAIL, "troca.email.f21@teste.com", SENHA);

        ResultadoConfirmacao resultado = service.confirmar(cliente.getId(), Tipo.EMAIL,
                codigoPara("troca.email.f21@teste.com"));

        assertThat(resultado.confirmado()).isTrue();
        Usuario salvo = recarregar();
        assertThat(salvo.getEmail()).isEqualTo("troca.email.f21@teste.com");
        assertThat(salvo.getEmailConfirmadoEm()).isNotNull();
        assertThat(salvo.getCelular()).isEqualTo(celularAntes);
        assertThat(salvo.getCelularConfirmadoEm()).isEqualTo(celularConfirmadoAntes);
    }

    @Test
    @DisplayName("confirmar o celular troca o celular (só dígitos), confirma só ele e não mexe no e-mail")
    void confirmarTrocaOCelular() {
        var emailAntes = cliente.getEmail();
        var emailConfirmadoAntes = cliente.getEmailConfirmadoEm();
        service.solicitar(cliente.getId(), Tipo.CELULAR, "(67) 98888-7766", SENHA);
        assertThat(caixa.mensagens().get(0).canal()).isEqualTo(CanalCodigo.SMS);
        assertThat(caixa.mensagens().get(0).destino()).isEqualTo("67988887766");

        ResultadoConfirmacao resultado = service.confirmar(cliente.getId(), Tipo.CELULAR, codigoPara("67988887766"));

        assertThat(resultado.confirmado()).isTrue();
        Usuario salvo = recarregar();
        assertThat(salvo.getCelular()).isEqualTo("67988887766");
        assertThat(salvo.getCelularConfirmadoEm()).isNotNull();
        assertThat(salvo.getEmail()).isEqualTo(emailAntes);
        assertThat(salvo.getEmailConfirmadoEm()).isEqualTo(emailConfirmadoAntes);
    }

    @Test
    @DisplayName("senha atual errada é recusada no campo senhaAtual e nenhum código sai")
    void senhaErrada() {
        long antes = caixa.mensagens().size();

        assertThatThrownBy(() -> service.solicitar(cliente.getId(), Tipo.EMAIL, "outro.f21@teste.com", "errada123"))
                .isInstanceOfSatisfying(ContaInvalidaException.class,
                        e -> assertThat(e.getErros()).containsKey("senhaAtual").doesNotContainKey("novoContato"));
        assertThat(caixa.mensagens().size()).isEqualTo(antes);
    }

    @Test
    @DisplayName("e-mail atual, e-mail de outra conta e formatos inválidos são recusados no campo novoContato")
    void contatosRecusados() {
        for (String email : List.of("cliente@farma.com", "CLIENTE@farma.com", "admin@farma.com", "sem-arroba", "")) {
            assertThatThrownBy(() -> service.solicitar(cliente.getId(), Tipo.EMAIL, email, SENHA))
                    .as(email).isInstanceOfSatisfying(ContaInvalidaException.class,
                            e -> assertThat(e.getErros()).containsKey("novoContato"));
        }
        for (String celular : List.of(cliente.getCelular(), "123", "(67) 88888-7766")) {
            assertThatThrownBy(() -> service.solicitar(cliente.getId(), Tipo.CELULAR, celular, SENHA))
                    .as(celular).isInstanceOfSatisfying(ContaInvalidaException.class,
                            e -> assertThat(e.getErros()).containsKey("novoContato"));
        }
    }

    @Test
    @DisplayName("código de outra finalidade não confirma a troca de e-mail")
    void finalidadeErradaNaoVale() {
        codigos.enviar(cliente, FinalidadeCodigo.CADASTRO, CanalCodigo.EMAIL, "cadastro.f21@teste.com");
        String codigoDeCadastro = codigoPara("cadastro.f21@teste.com");
        service.solicitar(cliente.getId(), Tipo.CELULAR, "67977776655", SENHA);
        String codigoDeCelular = codigoPara("67977776655");

        for (String codigo : List.of(codigoDeCadastro, codigoDeCelular)) {
            ResultadoConfirmacao resultado = service.confirmar(cliente.getId(), Tipo.EMAIL, codigo);
            assertThat(resultado.confirmado()).isFalse();
            assertThat(resultado.validacao().situacao()).isEqualTo(SituacaoValidacao.INEXISTENTE);
        }
        assertThat(recarregar().getEmail()).isEqualTo("cliente@farma.com");
    }

    @Test
    @DisplayName("código errado conta tentativa e, na quinta, bloqueia; o e-mail não muda")
    void codigoErradoBloqueia() {
        service.solicitar(cliente.getId(), Tipo.EMAIL, "tentativas.f21@teste.com", SENHA);
        String certo = codigoPara("tentativas.f21@teste.com");
        String errado = certo.equals("000000") ? "111111" : "000000";

        var primeira = service.confirmar(cliente.getId(), Tipo.EMAIL, errado);
        assertThat(primeira.validacao().situacao()).isEqualTo(SituacaoValidacao.INCORRETO);
        assertThat(primeira.validacao().tentativasRestantes()).isEqualTo(4);
        for (int i = 0; i < 3; i++) {
            service.confirmar(cliente.getId(), Tipo.EMAIL, errado);
        }
        var quinta = service.confirmar(cliente.getId(), Tipo.EMAIL, errado);
        assertThat(quinta.validacao().situacao()).isEqualTo(SituacaoValidacao.BLOQUEADO);
        // bloqueado: nem o código certo vale mais
        assertThat(service.confirmar(cliente.getId(), Tipo.EMAIL, certo).confirmado()).isFalse();
        assertThat(recarregar().getEmail()).isEqualTo("cliente@farma.com");
    }

    @Test
    @DisplayName("código expirado não troca o e-mail")
    void codigoExpirado() {
        service.solicitar(cliente.getId(), Tipo.EMAIL, "expira.f21@teste.com", SENHA);
        String codigo = codigoPara("expira.f21@teste.com");
        em.flush();
        jdbc.update("UPDATE codigo_verificacao SET expira_em = now() - interval '1 minute' WHERE usuario_id = ?",
                cliente.getId());
        em.clear();

        ResultadoConfirmacao resultado = service.confirmar(cliente.getId(), Tipo.EMAIL, codigo);

        assertThat(resultado.confirmado()).isFalse();
        assertThat(resultado.validacao().situacao()).isEqualTo(SituacaoValidacao.EXPIRADO);
        assertThat(recarregar().getEmail()).isEqualTo("cliente@farma.com");
    }

    @Test
    @DisplayName("pedir de novo logo em seguida pede para aguardar, e o código novo invalida o anterior")
    void reenvioImediatoAguarda() {
        service.solicitar(cliente.getId(), Tipo.EMAIL, "reenvio.f21@teste.com", SENHA);

        var segundo = service.solicitar(cliente.getId(), Tipo.EMAIL, "reenvio.f21@teste.com", SENHA);

        assertThat(segundo.situacao()).isEqualTo(SituacaoEnvio.AGUARDE);
        assertThat(segundo.esperaSegundos()).isPositive();
    }

    @Test
    @DisplayName("e-mail tomado por outra conta entre o pedido e a confirmação: recusa e mantém o e-mail antigo")
    void disputaDeEmail() {
        service.solicitar(cliente.getId(), Tipo.EMAIL, "disputa.f21@teste.com", SENHA);
        String codigo = codigoPara("disputa.f21@teste.com");
        em.flush();
        jdbc.update("UPDATE usuario SET email = 'disputa.f21@teste.com' WHERE email = 'admin@farma.com'");
        em.clear();

        ResultadoConfirmacao resultado = service.confirmar(cliente.getId(), Tipo.EMAIL, codigo);

        assertThat(resultado.confirmado()).isFalse();
        assertThat(resultado.emUso()).isTrue();
        assertThat(recarregar().getEmail()).isEqualTo("cliente@farma.com");
    }

    @Test
    @DisplayName("conta pendente não troca contato (403)")
    void contaPendenteNaoTroca() {
        Usuario pendente = usuarios.findByEmailIgnoreCase("pendente@farma.com").orElseThrow();

        assertThatThrownBy(() -> service.solicitar(pendente.getId(), Tipo.EMAIL, "p.f21@teste.com", SENHA))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.confirmar(pendente.getId(), Tipo.EMAIL, "123456"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("o tipo da URL só aceita email e celular")
    void tipoDaUrl() {
        assertThat(Tipo.doCaminho("email")).isEqualTo(Tipo.EMAIL);
        assertThat(Tipo.doCaminho("celular")).isEqualTo(Tipo.CELULAR);
        assertThat(Tipo.doCaminho("cpf")).isNull();
        assertThat(Tipo.doCaminho("EMAIL")).isNull();
    }
}
