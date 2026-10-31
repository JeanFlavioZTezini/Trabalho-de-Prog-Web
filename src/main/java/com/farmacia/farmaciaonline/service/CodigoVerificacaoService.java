package com.farmacia.farmaciaonline.service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.farmacia.farmaciaonline.mensagem.EnvioDeMensagem;
import com.farmacia.farmaciaonline.model.CodigoVerificacao;
import com.farmacia.farmaciaonline.model.Usuario;
import com.farmacia.farmaciaonline.model.enums.CanalCodigo;
import com.farmacia.farmaciaonline.model.enums.FinalidadeCodigo;
import com.farmacia.farmaciaonline.repository.CodigoVerificacaoRepository;

/**
 * Códigos de verificação de 6 números (UC04, e também troca de contato e recuperação de senha, pela finalidade).
 * O código só existe em texto no momento do envio: o banco guarda o hash (BCrypt) e nada é gravado em log.
 * <p>
 * Recusas (código errado, expirado...) voltam como resultado, não como exceção: assim a transação termina com commit
 * e a tentativa errada fica gravada.
 */
@Service
public class CodigoVerificacaoService {

    private static final SecureRandom ALEATORIO = new SecureRandom();
    private static final Duration JANELA_DO_LIMITE = Duration.ofHours(1);

    public enum SituacaoEnvio { ENVIADO, AGUARDE, BLOQUEADO }

    /** {@code esperaSegundos}: quanto falta para poder pedir outro código (0 quando enviado). */
    public record ResultadoEnvio(SituacaoEnvio situacao, long esperaSegundos) {
    }

    public enum SituacaoValidacao { VALIDO, INCORRETO, EXPIRADO, JA_USADO, BLOQUEADO, INEXISTENTE }

    /** {@code canal} e {@code destino} dizem qual contato o código confirmou (só quando há código). */
    public record ResultadoValidacao(SituacaoValidacao situacao, int tentativasRestantes, CanalCodigo canal,
            String destino) {

        public boolean valido() {
            return situacao == SituacaoValidacao.VALIDO;
        }
    }

    private final CodigoVerificacaoRepository codigos;
    private final PasswordEncoder encoder;
    private final EnvioDeMensagem envio;
    private final Duration validade;
    private final int tentativasMaximas;
    private final Duration intervaloReenvio;
    private final int enviosPorHora;
    private final Duration bloqueioEnvio;

    public CodigoVerificacaoService(CodigoVerificacaoRepository codigos, PasswordEncoder encoder,
            EnvioDeMensagem envio,
            @Value("${app.codigos.validade:10m}") Duration validade,
            @Value("${app.codigos.tentativas-maximas:5}") int tentativasMaximas,
            @Value("${app.codigos.intervalo-reenvio:60s}") Duration intervaloReenvio,
            @Value("${app.codigos.envios-por-hora:5}") int enviosPorHora,
            @Value("${app.codigos.bloqueio-envio:15m}") Duration bloqueioEnvio) {
        this.codigos = codigos;
        this.encoder = encoder;
        this.envio = envio;
        this.validade = validade;
        this.tentativasMaximas = tentativasMaximas;
        this.intervaloReenvio = intervaloReenvio;
        this.enviosPorHora = enviosPorHora;
        this.bloqueioEnvio = bloqueioEnvio;
    }

    public Duration getValidade() {
        return validade;
    }

    public int getTentativasMaximas() {
        return tentativasMaximas;
    }

    /**
     * Gera e envia um código novo, invalidando os anteriores da mesma finalidade. Recusa se o último envio foi há menos
     * de {@code intervalo-reenvio} ou se já houve {@code envios-por-hora} envios na última hora e o último foi há menos
     * de {@code bloqueio-envio}.
     */
    @Transactional
    public ResultadoEnvio enviar(Usuario usuario, FinalidadeCodigo finalidade, CanalCodigo canal, String destino) {
        Instant agora = Instant.now();
        Optional<CodigoVerificacao> ultimo = codigos.findFirstByUsuarioIdAndFinalidadeOrderByCriadoEmDesc(usuario.getId(),
                finalidade);
        if (ultimo.isPresent()) {
            Instant liberaReenvio = ultimo.get().getCriadoEm().plus(intervaloReenvio);
            if (agora.isBefore(liberaReenvio)) {
                return new ResultadoEnvio(SituacaoEnvio.AGUARDE, segundosAte(agora, liberaReenvio));
            }
            long naUltimaHora = codigos.countByUsuarioIdAndFinalidadeAndCriadoEmAfter(usuario.getId(), finalidade,
                    agora.minus(JANELA_DO_LIMITE));
            Instant liberaBloqueio = ultimo.get().getCriadoEm().plus(bloqueioEnvio);
            if (naUltimaHora >= enviosPorHora && agora.isBefore(liberaBloqueio)) {
                return new ResultadoEnvio(SituacaoEnvio.BLOQUEADO, segundosAte(agora, liberaBloqueio));
            }
        }

        codigos.findByUsuarioIdAndFinalidadeAndUsadoEmIsNullAndInvalidadoEmIsNull(usuario.getId(), finalidade)
                .forEach(anterior -> anterior.setInvalidadoEm(agora));

        String codigo = String.format("%06d", ALEATORIO.nextInt(1_000_000));
        codigos.save(CodigoVerificacao.builder()
                .usuario(usuario)
                .finalidade(finalidade)
                .canal(canal)
                .destino(destino)
                .codigoHash(encoder.encode(codigo))
                .expiraEm(agora.plus(validade))
                .criadoEm(agora)
                .build());
        envio.enviar(canal, destino, assunto(finalidade), texto(finalidade, codigo));
        return new ResultadoEnvio(SituacaoEnvio.ENVIADO, 0);
    }

    /**
     * Confere o código digitado contra o último código da finalidade. Código de outra finalidade nunca vale.
     * A tentativa errada é gravada (o método termina sem exceção, então a transação faz commit).
     */
    @Transactional
    public ResultadoValidacao validar(Long usuarioId, FinalidadeCodigo finalidade, String digitado) {
        Optional<CodigoVerificacao> atual = codigos.findFirstByUsuarioIdAndFinalidadeOrderByCriadoEmDesc(usuarioId,
                finalidade);
        if (atual.isEmpty() || atual.get().getInvalidadoEm() != null) {
            return new ResultadoValidacao(SituacaoValidacao.INEXISTENTE, 0, null, null);
        }
        CodigoVerificacao codigo = atual.get();
        if (codigo.getUsadoEm() != null) {
            return resultado(SituacaoValidacao.JA_USADO, codigo);
        }
        if (codigo.getTentativas() >= tentativasMaximas) {
            return resultado(SituacaoValidacao.BLOQUEADO, codigo);
        }
        if (!Instant.now().isBefore(codigo.getExpiraEm())) {
            return resultado(SituacaoValidacao.EXPIRADO, codigo);
        }

        String limpo = digitado == null ? "" : digitado.replaceAll("\\s", "");
        if (!limpo.matches("\\d{6}") || !encoder.matches(limpo, codigo.getCodigoHash())) {
            codigo.setTentativas(codigo.getTentativas() + 1);
            return resultado(codigo.getTentativas() >= tentativasMaximas
                    ? SituacaoValidacao.BLOQUEADO : SituacaoValidacao.INCORRETO, codigo);
        }
        codigo.setUsadoEm(Instant.now());
        return resultado(SituacaoValidacao.VALIDO, codigo);
    }

    private ResultadoValidacao resultado(SituacaoValidacao situacao, CodigoVerificacao codigo) {
        return new ResultadoValidacao(situacao, Math.max(0, tentativasMaximas - codigo.getTentativas()),
                codigo.getCanal(), codigo.getDestino());
    }

    private static long segundosAte(Instant agora, Instant momento) {
        return Math.max(1, (Duration.between(agora, momento).toMillis() + 999) / 1000);
    }

    private static String assunto(FinalidadeCodigo finalidade) {
        return switch (finalidade) {
            case CADASTRO -> "Confirme sua conta Farma++";
            case ALTERAR_EMAIL, ALTERAR_CELULAR -> "Confirme seu novo contato no Farma++";
            case RECUPERAR_SENHA -> "Recuperação de senha do Farma++";
            case VINCULO_SOCIAL -> "Confirme o vínculo da sua conta Farma++";
        };
    }

    private String texto(FinalidadeCodigo finalidade, String codigo) {
        return "Seu código do Farma++ é " + codigo + ". Ele vale por " + validade.toMinutes()
                + " minutos e só pode ser usado uma vez. Se não foi você que pediu, ignore esta mensagem.";
    }
}
