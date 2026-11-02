package com.farmacia.farmaciaonline.security;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.farmacia.farmaciaonline.model.Usuario;
import com.farmacia.farmaciaonline.model.enums.StatusUsuario;
import com.farmacia.farmaciaonline.repository.UsuarioRepository;

/**
 * Política de proteção contra tentativa repetida de senha: cinco falhas consecutivas
 * bloqueiam a conta por quinze minutos. Só contas ativas existentes são contabilizadas.
 */
@Service
public class LoginTentativasService {

    public static final int MAXIMO_FALHAS = 5;
    public static final Duration DURACAO_BLOQUEIO = Duration.ofMinutes(15);

    private final UsuarioRepository usuarioRepository;

    public LoginTentativasService(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    @Transactional
    public void registrarFalha(String identificador) {
        buscarParaAtualizar(identificador).filter(u -> u.getStatus() == StatusUsuario.ATIVA).ifPresent(usuario -> {
            Instant agora = Instant.now();
            // Uma janela de bloqueio vencida inicia uma nova sequência de tentativas.
            if (usuario.getBloqueadoAte() != null && !usuario.getBloqueadoAte().isAfter(agora)) {
                usuario.setBloqueadoAte(null);
                usuario.setTentativasLoginFalhas(0);
            }
            int falhas = usuario.getTentativasLoginFalhas() == null ? 0 : usuario.getTentativasLoginFalhas();
            falhas++;
            usuario.setTentativasLoginFalhas(falhas);
            if (falhas >= MAXIMO_FALHAS) {
                usuario.setBloqueadoAte(agora.plus(DURACAO_BLOQUEIO));
            }
            usuarioRepository.save(usuario);
        });
    }

    @Transactional
    public void limparFalhas(Long usuarioId) {
        if (usuarioId == null) return;
        usuarioRepository.findById(usuarioId).ifPresent(usuario -> {
            usuario.setTentativasLoginFalhas(0);
            usuario.setBloqueadoAte(null);
            usuarioRepository.save(usuario);
        });
    }

    private Optional<Usuario> buscarParaAtualizar(String identificador) {
        if (identificador == null || identificador.isBlank()) return Optional.empty();
        String valor = identificador.trim();
        String cpf = "";
        String email = valor;
        if (!valor.contains("@")) {
            String digitos = valor.replaceAll("\\D", "");
            if (digitos.length() == 11) {
                cpf = digitos;
                email = "";
            }
        }
        return usuarioRepository.buscarParaAtualizarTentativasLogin(email, cpf);
    }
}
