package com.farmacia.farmaciaonline.service;

import java.util.Locale;
import java.util.Optional;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.farmacia.farmaciaonline.model.Usuario;
import com.farmacia.farmaciaonline.model.enums.CanalCodigo;
import com.farmacia.farmaciaonline.model.enums.FinalidadeCodigo;
import com.farmacia.farmaciaonline.model.enums.StatusUsuario;
import com.farmacia.farmaciaonline.repository.UsuarioRepository;
import com.farmacia.farmaciaonline.security.RevogacaoDeSessoes;

/**
 * Recuperação de senha sem login (UC14). O código (finalidade RECUPERAR_SENHA) só vai para um contato já confirmado de
 * uma conta ATIVA, cliente ou administrador. Para quem pede, a resposta é sempre a mesma: não dá para saber se a conta
 * existe, se foi excluída ou se não tem contato confirmado.
 */
@Service
public class RecuperacaoSenhaService {

    /** {@code contaId} só é preenchido quando a conta é elegível; a tela não mostra essa diferença. */
    public record Solicitacao(long esperaSegundos, Long contaId) {
    }

    private final UsuarioRepository usuarios;
    private final CodigoVerificacaoService codigos;
    private final LimiteDeSolicitacoes limite;
    private final PasswordEncoder encoder;
    private final RevogacaoDeSessoes revogacao;

    public RecuperacaoSenhaService(UsuarioRepository usuarios, CodigoVerificacaoService codigos,
            LimiteDeSolicitacoes limite, PasswordEncoder encoder, RevogacaoDeSessoes revogacao) {
        this.usuarios = usuarios;
        this.codigos = codigos;
        this.limite = limite;
        this.encoder = encoder;
        this.revogacao = revogacao;
    }

    /**
     * Pede um código. O limite vale por identificador digitado, exista a conta ou não. Se a conta for elegível, o código
     * vai para o contato confirmado do canal preferido ou, se esse não estiver confirmado, para o outro. Se a conta já
     * recebeu um código há pouco (por outro identificador), nada novo é enviado e o anterior continua valendo.
     */
    @Transactional
    public Solicitacao solicitar(String identificador, CanalCodigo preferido) {
        if (identificador == null || identificador.isBlank()) {
            return new Solicitacao(0, null); // nada digitado: não conta no limite nem procura conta
        }
        long espera = limite.registrar(chave(identificador));
        if (espera > 0) {
            return new Solicitacao(espera, null);
        }
        Optional<Usuario> conta = buscar(identificador).filter(u -> u.getStatus() == StatusUsuario.ATIVA);
        CanalCodigo canal = conta.map(u -> canalConfirmado(u, preferido)).orElse(null);
        if (canal == null) {
            encoder.encode("recuperacao"); // mesmo custo do envio (hash do código): o tempo de resposta não denuncia a conta
            return new Solicitacao(0, null);
        }
        Usuario usuario = conta.get();
        codigos.enviar(usuario, FinalidadeCodigo.RECUPERAR_SENHA, canal,
                canal == CanalCodigo.EMAIL ? usuario.getEmail() : usuario.getCelular());
        return new Solicitacao(0, usuario.getId());
    }

    /** Confere o código de recuperação; o código certo fica marcado como usado. */
    @Transactional
    public boolean validarCodigo(Long contaId, String codigo) {
        return contaId != null && codigos.validar(contaId, FinalidadeCodigo.RECUPERAR_SENHA, codigo).valido();
    }

    /** A senha digitada é a que a conta já usa? Conta sem senha local nunca coincide. */
    @Transactional(readOnly = true)
    public boolean mesmaSenhaAtual(Long contaId, String senha) {
        if (contaId == null || senha == null) {
            return false;
        }
        String hashAtual = usuarios.findById(contaId).map(Usuario::getSenhaHash).orElse(null);
        return hashAtual != null && encoder.matches(senha, hashAtual);
    }

    /**
     * Grava a nova senha (BCrypt) e encerra todas as sessões abertas da conta. Vínculo com Google ou Facebook não é
     * tocado, e conta sem senha local passa a ter uma.
     *
     * @throws IllegalStateException se a conta não está mais ativa
     * @throws IllegalArgumentException se a nova senha é igual à atual
     */
    @Transactional
    public void redefinir(Long contaId, String novaSenha) {
        Usuario usuario = usuarios.findById(contaId).filter(u -> u.getStatus() == StatusUsuario.ATIVA)
                .orElseThrow(() -> new IllegalStateException("A conta não está ativa."));
        if (usuario.getSenhaHash() != null && encoder.matches(novaSenha, usuario.getSenhaHash())) {
            throw new IllegalArgumentException("A nova senha é igual à atual.");
        }
        usuario.setSenhaHash(encoder.encode(novaSenha));
        // quem recuperou a senha não deve continuar preso no bloqueio do login (F-18)
        usuario.setTentativasLoginFalhas(0);
        usuario.setBloqueadoAte(null);
        revogacao.encerrarSessoesDe(usuario.getId());
    }

    private Optional<Usuario> buscar(String identificador) {
        if (identificador == null || identificador.isBlank()) {
            return Optional.empty();
        }
        String valor = identificador.trim();
        return valor.contains("@") ? usuarios.findByEmailIgnoreCase(valor) : usuarios.findByCpf(valor.replaceAll("\\D", ""));
    }

    private static CanalCodigo canalConfirmado(Usuario usuario, CanalCodigo preferido) {
        boolean email = usuario.getEmailConfirmadoEm() != null && usuario.getEmail() != null;
        boolean celular = usuario.getCelularConfirmadoEm() != null && usuario.getCelular() != null;
        if (preferido == CanalCodigo.SMS) {
            return celular ? CanalCodigo.SMS : email ? CanalCodigo.EMAIL : null;
        }
        return email ? CanalCodigo.EMAIL : celular ? CanalCodigo.SMS : null;
    }

    /** E-mail em minúsculas ou CPF só com dígitos: "Maria@x.com" e "maria@x.com" contam como o mesmo pedido. */
    private static String chave(String identificador) {
        if (identificador == null) {
            return "";
        }
        String valor = identificador.trim();
        return valor.contains("@") ? valor.toLowerCase(Locale.ROOT) : valor.replaceAll("\\D", "");
    }
}
