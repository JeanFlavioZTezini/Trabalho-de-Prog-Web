package com.farmacia.farmaciaonline.service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.farmacia.farmaciaonline.model.Usuario;
import com.farmacia.farmaciaonline.model.enums.CanalCodigo;
import com.farmacia.farmaciaonline.model.enums.FinalidadeCodigo;
import com.farmacia.farmaciaonline.model.enums.StatusUsuario;
import com.farmacia.farmaciaonline.repository.UsuarioRepository;
import com.farmacia.farmaciaonline.security.LoginTentativasService;
import com.farmacia.farmaciaonline.service.CodigoVerificacaoService.ResultadoEnvio;
import com.farmacia.farmaciaonline.service.CodigoVerificacaoService.ResultadoValidacao;

/**
 * Troca de e-mail ou celular com confirmação do novo contato (UC07 / F-21). O contato atual só muda depois que o
 * código enviado ao <em>novo</em> destino é confirmado; o destino confirmado vem do registro do código, nunca de
 * campo do formulário. Confirmar um contato não confirma o outro.
 */
@Service
public class ContatoService {

    /** Tipo de contato e a finalidade/canal do código que o confirma. */
    public enum Tipo {
        EMAIL("email", FinalidadeCodigo.ALTERAR_EMAIL, CanalCodigo.EMAIL),
        CELULAR("celular", FinalidadeCodigo.ALTERAR_CELULAR, CanalCodigo.SMS);

        private final String caminho;
        private final FinalidadeCodigo finalidade;
        private final CanalCodigo canal;

        Tipo(String caminho, FinalidadeCodigo finalidade, CanalCodigo canal) {
            this.caminho = caminho;
            this.finalidade = finalidade;
            this.canal = canal;
        }

        public String getCaminho() {
            return caminho;
        }

        /** Tipo da URL ({@code email} ou {@code celular}); qualquer outro valor devolve {@code null}. */
        public static Tipo doCaminho(String caminho) {
            for (Tipo tipo : values()) {
                if (tipo.caminho.equals(caminho)) {
                    return tipo;
                }
            }
            return null;
        }
    }

    /** {@code emUso}: o e-mail foi tomado por outra conta entre o pedido e a confirmação (nada é alterado). */
    public record ResultadoConfirmacao(ResultadoValidacao validacao, boolean emUso, Usuario conta) {

        public boolean confirmado() {
            return conta != null;
        }
    }

    // Mesmas regras do cadastro (CadastroForm).
    private static final Pattern FORMATO_EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern FORMATO_CELULAR = Pattern.compile("^\\(?[1-9][1-9]\\)?\\s?9\\d{4}-?\\d{4}$");
    static final String MENSAGEM_EMAIL_USADO = "Não foi possível usar este e-mail. Informe outro.";

    private final UsuarioRepository usuarios;
    private final PasswordEncoder encoder;
    private final CodigoVerificacaoService codigos;
    private final LoginTentativasService tentativas;

    public ContatoService(UsuarioRepository usuarios, PasswordEncoder encoder, CodigoVerificacaoService codigos,
            LoginTentativasService tentativas) {
        this.usuarios = usuarios;
        this.encoder = encoder;
        this.codigos = codigos;
        this.tentativas = tentativas;
    }

    /**
     * Confere a senha atual e o novo contato e envia o código ao novo destino. Não altera a conta.
     *
     * @throws ContaInvalidaException com os erros por campo ({@code novoContato}, {@code senhaAtual})
     */
    @Transactional(noRollbackFor = ContaInvalidaException.class)
    public ResultadoEnvio solicitar(Long usuarioId, Tipo tipo, String novoContato, String senhaAtual) {
        Usuario usuario = contaAtiva(usuarioId);
        // Mesmo limite do login: conta bloqueada é recusada antes de conferir; a falha fica gravada mesmo com a recusa.
        if (ContaService.bloqueada(usuario)) {
            throw new ContaInvalidaException(Map.of("senhaAtual", ContaService.MENSAGEM_BLOQUEADA));
        }
        Map<String, String> erros = new LinkedHashMap<>();

        String hash = usuario.getSenhaHash();
        if (hash == null || hash.isBlank()) {
            erros.put("senhaAtual", "Esta conta não tem senha cadastrada.");
        } else if (!encoder.matches(senhaAtual == null ? "" : senhaAtual, hash)) {
            tentativas.registrarFalha(usuario.getEmail());
            erros.put("senhaAtual", "Senha atual incorreta.");
        } else {
            tentativas.limparFalhas(usuarioId);
        }

        String novo = normalizar(tipo, novoContato);
        String erroDoContato = erroDoContato(usuario, tipo, novoContato, novo);
        if (erroDoContato != null) {
            erros.put("novoContato", erroDoContato);
        }
        if (!erros.isEmpty()) {
            throw new ContaInvalidaException(erros);
        }
        return codigos.enviar(usuario, tipo.finalidade, tipo.canal, novo);
    }

    /**
     * Confere o código e, só com o código certo, grava o contato que foi para o destino do código. Recusas voltam como
     * resultado (e não exceção) para a tentativa errada ficar gravada.
     */
    @Transactional
    public ResultadoConfirmacao confirmar(Long usuarioId, Tipo tipo, String codigo) {
        Usuario usuario = contaAtiva(usuarioId);
        ResultadoValidacao resultado = codigos.validar(usuario.getId(), tipo.finalidade, codigo);
        if (!resultado.valido()) {
            return new ResultadoConfirmacao(resultado, false, null);
        }

        String destino = resultado.destino();
        if (tipo == Tipo.EMAIL) {
            if (usuarios.existsByEmailIgnoreCase(destino) && !destino.equalsIgnoreCase(usuario.getEmail())) {
                return new ResultadoConfirmacao(resultado, true, null);
            }
            usuario.setEmail(destino);
            usuario.setEmailConfirmadoEm(Instant.now());
        } else {
            usuario.setCelular(destino);
            usuario.setCelularConfirmadoEm(Instant.now());
        }
        return new ResultadoConfirmacao(resultado, false, usuarios.saveAndFlush(usuario));
    }

    private String erroDoContato(Usuario usuario, Tipo tipo, String digitado, String novo) {
        if (digitado == null || digitado.isBlank()) {
            return "Informe o novo contato.";
        }
        if (tipo == Tipo.EMAIL) {
            if (!FORMATO_EMAIL.matcher(novo).matches()) {
                return "E-mail inválido: use o formato nome@dominio.com.";
            }
            if (novo.equalsIgnoreCase(usuario.getEmail())) {
                return "Este já é o seu e-mail atual.";
            }
            if (usuarios.existsByEmailIgnoreCase(novo)) {
                return MENSAGEM_EMAIL_USADO;
            }
            return null;
        }
        if (!FORMATO_CELULAR.matcher(digitado.trim()).matches()) {
            return "Celular inválido: use o DDD e 9 dígitos, por exemplo (67) 99999-9999.";
        }
        if (novo.equals(usuario.getCelular())) {
            return "Este já é o seu celular atual.";
        }
        return null;
    }

    private static String normalizar(Tipo tipo, String contato) {
        if (contato == null) {
            return "";
        }
        return tipo == Tipo.EMAIL ? contato.trim().toLowerCase(Locale.ROOT) : contato.replaceAll("\\D", "");
    }

    private Usuario contaAtiva(Long usuarioId) {
        Usuario usuario = usuarios.findById(usuarioId)
                .orElseThrow(() -> new AccessDeniedException("Conta não encontrada."));
        if (usuario.getStatus() != StatusUsuario.ATIVA) {
            throw new AccessDeniedException("Conta não está ativa.");
        }
        return usuario;
    }
}
