package com.farmacia.farmaciaonline.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import com.farmacia.farmaciaonline.TestcontainersConfiguration;
import com.farmacia.farmaciaonline.mensagem.demo.DemoEnvioAdapter;
import com.farmacia.farmaciaonline.model.IdentidadeSocial;
import com.farmacia.farmaciaonline.model.Usuario;
import com.farmacia.farmaciaonline.model.enums.CanalCodigo;
import com.farmacia.farmaciaonline.model.enums.Perfil;
import com.farmacia.farmaciaonline.model.enums.ProvedorSocial;
import com.farmacia.farmaciaonline.model.enums.StatusUsuario;
import com.farmacia.farmaciaonline.repository.IdentidadeSocialRepository;
import com.farmacia.farmaciaonline.repository.UsuarioRepository;
import com.farmacia.farmaciaonline.security.UsuarioAutenticado;
import com.farmacia.farmaciaonline.service.RecuperacaoSenhaService.Solicitacao;

import jakarta.persistence.EntityManager;

/**
 * Recuperação de senha contra o PostgreSQL real e o seed (cliente@farma.com e admin@farma.com têm o e-mail confirmado
 * pela V3; pendente@farma.com não tem contato confirmado). Cada teste é desfeito no fim (@Transactional).
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class RecuperacaoSenhaServiceIntegracaoTest {

    private static final Pattern SEIS_DIGITOS = Pattern.compile("\\b(\\d{6})\\b");

    @Autowired
    private RecuperacaoSenhaService servico;

    @Autowired
    private UsuarioRepository usuarios;

    @Autowired
    private IdentidadeSocialRepository identidades;

    @Autowired
    private DemoEnvioAdapter caixa;

    @Autowired
    private LimiteDeSolicitacoes limite;

    @Autowired
    private PasswordEncoder encoder;

    @Autowired
    private SessionRegistry registro;

    @Autowired
    private EntityManager em;

    @BeforeEach
    void esquecerPedidosAnteriores() {
        limite.limpar();
    }

    private Usuario conta(String email) {
        return usuarios.findByEmailIgnoreCase(email).orElseThrow();
    }

    private long mensagensPara(String destino) {
        return caixa.mensagens().stream().filter(m -> m.destino().equals(destino)).count();
    }

    private String codigoEnviadoPara(String destino) {
        String texto = caixa.mensagens().stream().filter(m -> m.destino().equals(destino)).findFirst().orElseThrow().texto();
        Matcher encontrado = SEIS_DIGITOS.matcher(texto);
        assertThat(encontrado.find()).isTrue();
        return encontrado.group(1);
    }

    @Test
    @DisplayName("conta ativa com e-mail confirmado recebe o código no e-mail")
    void contaElegivelRecebeOCodigo() {
        long antes = mensagensPara("cliente@farma.com");

        Solicitacao resultado = servico.solicitar("cliente@farma.com", CanalCodigo.EMAIL);

        assertThat(resultado.esperaSegundos()).isZero();
        assertThat(resultado.contaId()).isEqualTo(conta("cliente@farma.com").getId());
        assertThat(mensagensPara("cliente@farma.com")).isEqualTo(antes + 1);
    }

    @Test
    @DisplayName("conta inexistente, pendente, sem contato confirmado ou excluída: mesma resposta e nenhum envio")
    void contasNaoElegiveisNaoRecebemNada() {
        Usuario semContato = conta("cliente@farma.com");
        semContato.setEmailConfirmadoEm(null);
        semContato.setCelularConfirmadoEm(null);
        usuarios.saveAndFlush(Usuario.builder().nome("Conta Excluída").email("excluida@exemplo.com")
                .dataNascimento(LocalDate.of(1990, 1, 1)).senhaHash(encoder.encode("Senha123")).perfil(Perfil.CLIENTE)
                .status(StatusUsuario.EXCLUIDA).excluidoEm(Instant.now()).emailConfirmadoEm(Instant.now())
                .termosVersao("1.0").termosAceitosEm(Instant.now()).build());
        em.flush();
        long totalAntes = caixa.mensagens().size();

        for (String identificador : new String[] {"ninguem@exemplo.com", "pendente@farma.com", "529.982.247-25",
                "excluida@exemplo.com", "", null}) {
            Solicitacao resultado = servico.solicitar(identificador, CanalCodigo.EMAIL);
            assertThat(resultado.esperaSegundos()).as("identificador %s", identificador).isZero();
            assertThat(resultado.contaId()).as("identificador %s", identificador).isNull();
        }
        assertThat(caixa.mensagens()).hasSize((int) totalAntes);
    }

    @Test
    @DisplayName("o limite de pedidos vale por identificador, exista a conta ou não")
    void limiteValeAteParaIdentificadorInexistente() {
        assertThat(servico.solicitar("ninguem@exemplo.com", CanalCodigo.EMAIL).esperaSegundos()).isZero();
        assertThat(servico.solicitar("NINGUEM@exemplo.com", CanalCodigo.EMAIL).esperaSegundos()).isBetween(55L, 60L);

        assertThat(servico.solicitar("529.982.247-25", CanalCodigo.EMAIL).esperaSegundos()).isZero();
        assertThat(servico.solicitar("52998224725", CanalCodigo.EMAIL).esperaSegundos()).isBetween(55L, 60L);
    }

    @Test
    @DisplayName("quem prefere SMS sem celular confirmado recebe pelo e-mail confirmado")
    void preferenciaSemContatoConfirmadoUsaOOutro() {
        // a caixa demo fica na memória entre os testes: compara antes e depois, nunca com zero
        long antesEmail = mensagensPara("cliente@farma.com");
        long antesSms = mensagensPara("11987654321");

        servico.solicitar("cliente@farma.com", CanalCodigo.SMS);

        assertThat(mensagensPara("cliente@farma.com")).isEqualTo(antesEmail + 1);
        assertThat(mensagensPara("11987654321")).isEqualTo(antesSms);
    }

    @Test
    @DisplayName("com só o celular confirmado, o código vai por SMS")
    void soCelularConfirmadoRecebePorSms() {
        Usuario cliente = conta("cliente@farma.com");
        cliente.setEmailConfirmadoEm(null);
        cliente.setCelularConfirmadoEm(Instant.now());
        em.flush();
        long antes = mensagensPara(cliente.getCelular());

        servico.solicitar("cliente@farma.com", CanalCodigo.EMAIL);

        assertThat(mensagensPara(cliente.getCelular())).isEqualTo(antes + 1);
    }

    @Test
    @DisplayName("código certo troca a senha (BCrypt) e não pode ser usado de novo")
    void codigoCertoTrocaASenhaUmaVezSo() {
        Long id = servico.solicitar("cliente@farma.com", CanalCodigo.EMAIL).contaId();
        String codigo = codigoEnviadoPara("cliente@farma.com");

        assertThat(servico.validarCodigo(id, codigo)).isTrue();
        servico.redefinir(id, "NovaSenha1");

        Usuario cliente = conta("cliente@farma.com");
        assertThat(cliente.getSenhaHash()).startsWith("$2");
        assertThat(encoder.matches("NovaSenha1", cliente.getSenhaHash())).isTrue();
        assertThat(encoder.matches("Farma@123", cliente.getSenhaHash())).isFalse();
        assertThat(servico.validarCodigo(id, codigo)).isFalse();
    }

    @Test
    @DisplayName("reconhece a senha atual; conta sem senha local nunca coincide")
    void reconheceASenhaAtual() {
        Long id = conta("cliente@farma.com").getId();
        Usuario social = usuarios.saveAndFlush(Usuario.builder().cpf("16899535009").nome("Conta Social")
                .email("social@exemplo.com").dataNascimento(LocalDate.of(1990, 1, 1)).senhaHash(null)
                .perfil(Perfil.CLIENTE).status(StatusUsuario.ATIVA).emailConfirmadoEm(Instant.now())
                .termosVersao("1.0").termosAceitosEm(Instant.now()).build());

        assertThat(servico.mesmaSenhaAtual(id, "Farma@123")).isTrue();
        assertThat(servico.mesmaSenhaAtual(id, "NovaSenha1")).isFalse();
        assertThat(servico.mesmaSenhaAtual(social.getId(), "Farma@123")).isFalse();
        assertThat(servico.mesmaSenhaAtual(null, "Farma@123")).isFalse();
        // o serviço também barra, mesmo que alguém chame o redefinir sem passar pela tela
        assertThatThrownBy(() -> servico.redefinir(id, "Farma@123")).isInstanceOf(IllegalArgumentException.class);
        assertThat(encoder.matches("Farma@123", conta("cliente@farma.com").getSenhaHash())).isTrue();
    }

    @Test
    @DisplayName("código errado, ou sem conta elegível, não valida")
    void codigoErradoNaoValida() {
        Long id = servico.solicitar("cliente@farma.com", CanalCodigo.EMAIL).contaId();
        String codigo = codigoEnviadoPara("cliente@farma.com");

        assertThat(servico.validarCodigo(id, codigo.equals("000000") ? "111111" : "000000")).isFalse();
        assertThat(servico.validarCodigo(null, codigo)).isFalse();
    }

    @Test
    @DisplayName("o administrador também recupera pelo e-mail confirmado")
    void administradorRecupera() {
        Long id = servico.solicitar("admin@farma.com", CanalCodigo.EMAIL).contaId();

        assertThat(id).isEqualTo(conta("admin@farma.com").getId());
        assertThat(servico.validarCodigo(id, codigoEnviadoPara("admin@farma.com"))).isTrue();
        servico.redefinir(id, "NovaSenha1");
        assertThat(encoder.matches("NovaSenha1", conta("admin@farma.com").getSenhaHash())).isTrue();
    }

    @Test
    @DisplayName("conta só com login social define uma senha local e mantém o vínculo")
    void contaSocialDefineSenhaSemPerderOVinculo() {
        Usuario social = usuarios.saveAndFlush(Usuario.builder().cpf("16899535009").nome("Conta Social")
                .email("social@exemplo.com").dataNascimento(LocalDate.of(1990, 1, 1)).senhaHash(null)
                .perfil(Perfil.CLIENTE).status(StatusUsuario.ATIVA).emailConfirmadoEm(Instant.now())
                .termosVersao("1.0").termosAceitosEm(Instant.now()).build());
        IdentidadeSocial vinculo = identidades.saveAndFlush(IdentidadeSocial.builder().usuario(social)
                .provedor(ProvedorSocial.GOOGLE).provedorUsuarioId("google-123").build());

        Long id = servico.solicitar("social@exemplo.com", CanalCodigo.EMAIL).contaId();
        assertThat(servico.validarCodigo(id, codigoEnviadoPara("social@exemplo.com"))).isTrue();
        servico.redefinir(id, "NovaSenha1");

        assertThat(encoder.matches("NovaSenha1", conta("social@exemplo.com").getSenhaHash())).isTrue();
        assertThat(identidades.existsById(vinculo.getId())).isTrue();
    }

    @Test
    @DisplayName("trocar a senha encerra as sessões abertas da conta, e só dela")
    void trocarASenhaEncerraAsSessoes() {
        Usuario cliente = conta("cliente@farma.com");
        Usuario admin = conta("admin@farma.com");
        registro.registerNewSession("teste-sessao-cliente", UsuarioAutenticado.de(cliente));
        registro.registerNewSession("teste-sessao-admin", UsuarioAutenticado.de(admin));
        try {
            Long id = servico.solicitar("cliente@farma.com", CanalCodigo.EMAIL).contaId();
            servico.validarCodigo(id, codigoEnviadoPara("cliente@farma.com"));
            servico.redefinir(id, "NovaSenha1");

            assertThat(registro.getSessionInformation("teste-sessao-cliente").isExpired()).isTrue();
            assertThat(registro.getSessionInformation("teste-sessao-admin").isExpired()).isFalse();
        } finally {
            registro.removeSessionInformation("teste-sessao-cliente");
            registro.removeSessionInformation("teste-sessao-admin");
        }
    }
}
