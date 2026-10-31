package com.farmacia.farmaciaonline.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.farmacia.farmaciaonline.TestcontainersConfiguration;
import com.farmacia.farmaciaonline.mensagem.demo.DemoEnvioAdapter;
import com.farmacia.farmaciaonline.model.CodigoVerificacao;
import com.farmacia.farmaciaonline.model.Usuario;
import com.farmacia.farmaciaonline.model.enums.CanalCodigo;
import com.farmacia.farmaciaonline.model.enums.FinalidadeCodigo;
import com.farmacia.farmaciaonline.repository.CodigoVerificacaoRepository;
import com.farmacia.farmaciaonline.repository.UsuarioRepository;
import com.farmacia.farmaciaonline.service.CodigoVerificacaoService.ResultadoEnvio;
import com.farmacia.farmaciaonline.service.CodigoVerificacaoService.SituacaoEnvio;
import com.farmacia.farmaciaonline.service.CodigoVerificacaoService.SituacaoValidacao;

import jakarta.persistence.EntityManager;

/**
 * Serviço de códigos contra o PostgreSQL real, com a conta pendente do seed (pendente@farma.com).
 * Cada teste é desfeito no fim (@Transactional), menos o da tentativa gravada, que limpa o que criou.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
@ExtendWith(OutputCaptureExtension.class)
class CodigoVerificacaoServiceIntegracaoTest {

    private static final Pattern SEIS_DIGITOS = Pattern.compile("\\b(\\d{6})\\b");

    @Autowired
    private CodigoVerificacaoService servico;

    @Autowired
    private CodigoVerificacaoRepository codigos;

    @Autowired
    private UsuarioRepository usuarios;

    @Autowired
    private DemoEnvioAdapter caixa;

    @Autowired
    private PasswordEncoder encoder;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManager em;

    private Usuario pendente() {
        return usuarios.findByEmailIgnoreCase("pendente@farma.com").orElseThrow();
    }

    private ResultadoEnvio enviar(Usuario usuario) {
        return servico.enviar(usuario, FinalidadeCodigo.CADASTRO, CanalCodigo.EMAIL, usuario.getEmail());
    }

    private SituacaoValidacao validar(Usuario usuario, String codigo) {
        return servico.validar(usuario.getId(), FinalidadeCodigo.CADASTRO, codigo).situacao();
    }

    private String ultimoCodigo(String destino) {
        String texto = caixa.mensagens().stream().filter(m -> m.destino().equals(destino)).findFirst().orElseThrow().texto();
        Matcher encontrado = SEIS_DIGITOS.matcher(texto);
        assertThat(encontrado.find()).as("código na mensagem: %s", texto).isTrue();
        return encontrado.group(1);
    }

    private CodigoVerificacao ultimoSalvo(Usuario usuario, FinalidadeCodigo finalidade) {
        return codigos.findFirstByUsuarioIdAndFinalidadeOrderByCriadoEmDesc(usuario.getId(), finalidade).orElseThrow();
    }

    private static String outro(String codigo) {
        return codigo.equals("000000") ? "111111" : "000000";
    }

    /** Recua no tempo a criação dos códigos (o JPA não atualiza criado_em: a coluna é updatable=false). */
    private void voltarNoTempo(Usuario usuario, Duration quanto) {
        em.flush();
        jdbc.update("UPDATE codigo_verificacao SET criado_em = criado_em - make_interval(secs => ?) WHERE usuario_id = ?",
                (double) quanto.toSeconds(), usuario.getId());
        em.clear();
    }

    @Test
    @DisplayName("envia um código de 6 números e guarda só o hash, com destino, canal, finalidade e validade de 10 min")
    void enviaCodigoEGuardaSoOHash() {
        Usuario usuario = pendente();

        assertThat(enviar(usuario).situacao()).isEqualTo(SituacaoEnvio.ENVIADO);

        String codigo = ultimoCodigo(usuario.getEmail());
        CodigoVerificacao salvo = ultimoSalvo(usuario, FinalidadeCodigo.CADASTRO);
        assertThat(salvo.getCodigoHash()).startsWith("$2").isNotEqualTo(codigo);
        assertThat(encoder.matches(codigo, salvo.getCodigoHash())).isTrue();
        assertThat(salvo.getCanal()).isEqualTo(CanalCodigo.EMAIL);
        assertThat(salvo.getDestino()).isEqualTo(usuario.getEmail());
        assertThat(salvo.getFinalidade()).isEqualTo(FinalidadeCodigo.CADASTRO);
        assertThat(salvo.getTentativas()).isZero();
        assertThat(salvo.getExpiraEm()).isBetween(Instant.now().plus(9, ChronoUnit.MINUTES),
                Instant.now().plus(10, ChronoUnit.MINUTES).plusSeconds(1));
    }

    @Test
    @DisplayName("o código certo vale uma vez só")
    void codigoCertoValeUmaVezSo() {
        Usuario usuario = pendente();
        enviar(usuario);
        String codigo = ultimoCodigo(usuario.getEmail());

        assertThat(validar(usuario, codigo)).isEqualTo(SituacaoValidacao.VALIDO);
        assertThat(validar(usuario, codigo)).isEqualTo(SituacaoValidacao.JA_USADO);
    }

    @Test
    @DisplayName("cada erro gasta uma tentativa; no 5º o código fica bloqueado, mesmo para o código certo")
    void cincoErrosBloqueiamOCodigo() {
        Usuario usuario = pendente();
        enviar(usuario);
        String codigo = ultimoCodigo(usuario.getEmail());

        for (int erro = 1; erro <= 4; erro++) {
            var resultado = servico.validar(usuario.getId(), FinalidadeCodigo.CADASTRO, outro(codigo));
            assertThat(resultado.situacao()).isEqualTo(SituacaoValidacao.INCORRETO);
            assertThat(resultado.tentativasRestantes()).isEqualTo(5 - erro);
        }
        assertThat(validar(usuario, outro(codigo))).isEqualTo(SituacaoValidacao.BLOQUEADO);
        assertThat(validar(usuario, codigo)).isEqualTo(SituacaoValidacao.BLOQUEADO);
        assertThat(ultimoSalvo(usuario, FinalidadeCodigo.CADASTRO).getTentativas()).isEqualTo(5);
    }

    @Test
    @DisplayName("texto que não é um número de 6 dígitos também gasta tentativa")
    void textoInvalidoGastaTentativa() {
        Usuario usuario = pendente();
        enviar(usuario);

        assertThat(validar(usuario, "abc")).isEqualTo(SituacaoValidacao.INCORRETO);
        assertThat(validar(usuario, null)).isEqualTo(SituacaoValidacao.INCORRETO);
        assertThat(ultimoSalvo(usuario, FinalidadeCodigo.CADASTRO).getTentativas()).isEqualTo(2);
    }

    @Test
    @DisplayName("código expirado não vale e não gasta tentativa")
    void codigoExpirado() {
        Usuario usuario = pendente();
        enviar(usuario);
        String codigo = ultimoCodigo(usuario.getEmail());
        CodigoVerificacao salvo = ultimoSalvo(usuario, FinalidadeCodigo.CADASTRO);
        salvo.setExpiraEm(Instant.now().minusSeconds(1));
        em.flush();

        assertThat(validar(usuario, codigo)).isEqualTo(SituacaoValidacao.EXPIRADO);
        assertThat(ultimoSalvo(usuario, FinalidadeCodigo.CADASTRO).getTentativas()).isZero();
    }

    @Test
    @DisplayName("código de uma finalidade não vale para outra")
    void finalidadeErradaNaoVale() {
        Usuario usuario = pendente();
        enviar(usuario);
        String codigoDoCadastro = ultimoCodigo(usuario.getEmail());

        assertThat(servico.validar(usuario.getId(), FinalidadeCodigo.RECUPERAR_SENHA, codigoDoCadastro).situacao())
                .isEqualTo(SituacaoValidacao.INEXISTENTE);

        servico.enviar(usuario, FinalidadeCodigo.RECUPERAR_SENHA, CanalCodigo.EMAIL, usuario.getEmail());
        String codigoDaRecuperacao = ultimoCodigo(usuario.getEmail());
        if (!codigoDaRecuperacao.equals(codigoDoCadastro)) {
            assertThat(servico.validar(usuario.getId(), FinalidadeCodigo.RECUPERAR_SENHA, codigoDoCadastro).situacao())
                    .isEqualTo(SituacaoValidacao.INCORRETO);
        }
        assertThat(validar(usuario, codigoDoCadastro)).isEqualTo(SituacaoValidacao.VALIDO);
    }

    @Test
    @DisplayName("reenvio só depois de 60 s, e o código novo invalida o anterior")
    void reenvioDepoisDe60sInvalidaOAnterior() {
        Usuario usuario = pendente();
        enviar(usuario);
        String primeiro = ultimoCodigo(usuario.getEmail());

        ResultadoEnvio cedo = enviar(usuario);
        assertThat(cedo.situacao()).isEqualTo(SituacaoEnvio.AGUARDE);
        assertThat(cedo.esperaSegundos()).isBetween(55L, 60L);

        voltarNoTempo(usuario, Duration.ofSeconds(61));
        assertThat(enviar(usuario).situacao()).isEqualTo(SituacaoEnvio.ENVIADO);
        String segundo = ultimoCodigo(usuario.getEmail());
        em.flush();

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM codigo_verificacao WHERE usuario_id = ? AND invalidado_em IS NOT NULL",
                Long.class, usuario.getId())).isEqualTo(1L);
        if (!primeiro.equals(segundo)) {
            assertThat(validar(usuario, primeiro)).isEqualTo(SituacaoValidacao.INCORRETO);
        }
        assertThat(validar(usuario, segundo)).isEqualTo(SituacaoValidacao.VALIDO);
    }

    @Test
    @DisplayName("depois de 5 envios em 1 hora, o próximo só sai 15 min depois do anterior")
    void limiteDeEnviosPorHora() {
        Usuario usuario = pendente();
        for (int envio = 1; envio <= 5; envio++) {
            assertThat(enviar(usuario).situacao()).as("envio %d", envio).isEqualTo(SituacaoEnvio.ENVIADO);
            voltarNoTempo(usuario, Duration.ofSeconds(61));
        }

        ResultadoEnvio bloqueado = enviar(usuario);
        assertThat(bloqueado.situacao()).isEqualTo(SituacaoEnvio.BLOQUEADO);
        assertThat(bloqueado.esperaSegundos()).isBetween(15 * 60L - 62, 15 * 60L - 59);

        voltarNoTempo(usuario, Duration.ofMinutes(15));
        assertThat(enviar(usuario).situacao()).isEqualTo(SituacaoEnvio.ENVIADO);
    }

    @Test
    @DisplayName("o código nunca aparece no log")
    void codigoNuncaApareceNoLog(CapturedOutput saida) {
        Usuario usuario = pendente();
        enviar(usuario);
        String codigo = ultimoCodigo(usuario.getEmail());
        validar(usuario, outro(codigo));
        validar(usuario, codigo);

        assertThat(saida.getAll()).doesNotContain(codigo);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("a tentativa errada fica gravada no banco mesmo com a confirmação recusada")
    void tentativaErradaFicaGravada() {
        Usuario usuario = pendente();
        try {
            enviar(usuario);
            String codigo = ultimoCodigo(usuario.getEmail());

            assertThat(validar(usuario, outro(codigo))).isEqualTo(SituacaoValidacao.INCORRETO);

            // leitura nova, fora da transação do validar: só passa se o incremento teve commit
            assertThat(ultimoSalvo(usuario, FinalidadeCodigo.CADASTRO).getTentativas()).isEqualTo(1);
        } finally {
            jdbc.update("DELETE FROM codigo_verificacao WHERE usuario_id = ?", usuario.getId());
        }
    }
}
