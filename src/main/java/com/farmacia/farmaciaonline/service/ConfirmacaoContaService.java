package com.farmacia.farmaciaonline.service;

import java.time.Instant;
import java.util.Optional;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.farmacia.farmaciaonline.model.Usuario;
import com.farmacia.farmaciaonline.model.enums.CanalCodigo;
import com.farmacia.farmaciaonline.model.enums.FinalidadeCodigo;
import com.farmacia.farmaciaonline.model.enums.StatusUsuario;
import com.farmacia.farmaciaonline.repository.UsuarioRepository;
import com.farmacia.farmaciaonline.service.CodigoVerificacaoService.ResultadoEnvio;
import com.farmacia.farmaciaonline.service.CodigoVerificacaoService.ResultadoValidacao;

/**
 * Confirmação da conta recém-criada (UC04): envia o código ao e-mail ou ao celular cadastrado e, com o código certo,
 * marca aquele contato como confirmado e ativa a conta. Confirmar o e-mail não confirma o celular, e vice-versa.
 */
@Service
public class ConfirmacaoContaService {

    /**
     * Hash BCrypt qualquer, usado quando o e-mail ou CPF não existe: a conferência da senha leva o mesmo tempo nos dois
     * casos, então a resposta não denuncia quais contas existem.
     */
    private static final String HASH_FICTICIO = "$2a$10$U.QJ3Cn.DKAR7tAw4PdtBufnRt8qSQRv3M7hlFSINY92GvcM8fnTm";

    public record Contatos(String emailMascarado, String celularMascarado) {
    }

    private final UsuarioRepository usuarios;
    private final CodigoVerificacaoService codigos;
    private final PasswordEncoder encoder;

    public ConfirmacaoContaService(UsuarioRepository usuarios, CodigoVerificacaoService codigos, PasswordEncoder encoder) {
        this.usuarios = usuarios;
        this.codigos = codigos;
        this.encoder = encoder;
    }

    @Transactional(readOnly = true)
    public Optional<Usuario> pendente(Long usuarioId) {
        return usuarios.findById(usuarioId).filter(u -> u.getStatus() == StatusUsuario.PENDENTE);
    }

    public Contatos contatos(Usuario usuario) {
        return new Contatos(ContatoMascarado.email(usuario.getEmail()), ContatoMascarado.celular(usuario.getCelular()));
    }

    /** @throws IllegalStateException se a conta não está mais pendente ou não tem o contato pedido */
    @Transactional
    public ResultadoEnvio enviarCodigo(Long usuarioId, CanalCodigo canal) {
        Usuario usuario = pendenteOuFalha(usuarioId);
        String destino = canal == CanalCodigo.EMAIL ? usuario.getEmail() : usuario.getCelular();
        if (destino == null || destino.isBlank()) {
            throw new IllegalStateException("A conta não tem esse contato.");
        }
        return codigos.enviar(usuario, FinalidadeCodigo.CADASTRO, canal, destino);
    }

    /** @throws IllegalStateException se a conta não está mais pendente */
    @Transactional
    public ResultadoValidacao confirmar(Long usuarioId, String codigo) {
        Usuario usuario = pendenteOuFalha(usuarioId);
        ResultadoValidacao resultado = codigos.validar(usuario.getId(), FinalidadeCodigo.CADASTRO, codigo);
        if (resultado.valido()) {
            if (resultado.canal() == CanalCodigo.EMAIL) {
                usuario.setEmailConfirmadoEm(Instant.now());
            } else {
                usuario.setCelularConfirmadoEm(Instant.now());
            }
            usuario.setStatus(StatusUsuario.ATIVA);
        }
        return resultado;
    }

    /**
     * Retomada da confirmação pelo login (conta pendente que saiu da página do código). Só devolve a conta se o e-mail
     * ou CPF e a senha conferirem: conhecer só o e-mail ou o id de alguém não basta.
     */
    @Transactional(readOnly = true)
    public Optional<Usuario> retomar(String identificador, String senha) {
        if (identificador == null || identificador.isBlank() || senha == null || senha.isEmpty()) {
            return Optional.empty();
        }
        String valor = identificador.trim();
        Optional<Usuario> conta = valor.contains("@")
                ? usuarios.findByEmailIgnoreCase(valor)
                : usuarios.findByCpf(valor.replaceAll("\\D", ""));
        String hash = conta.map(Usuario::getSenhaHash).orElse(HASH_FICTICIO);
        boolean senhaConfere = encoder.matches(senha, hash == null ? HASH_FICTICIO : hash);
        return senhaConfere ? conta : Optional.empty();
    }

    private Usuario pendenteOuFalha(Long usuarioId) {
        return pendente(usuarioId).orElseThrow(() -> new IllegalStateException("A conta não está pendente."));
    }
}
