package com.farmacia.farmaciaonline.security;

import java.time.Instant;
import java.util.Optional;

import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.farmacia.farmaciaonline.model.Usuario;
import com.farmacia.farmaciaonline.repository.UsuarioRepository;

/**
 * Carrega o usuário para o login. O campo digitado pode ser e-mail ou CPF.
 *
 * Regra do CPF: tira tudo que não é dígito; se sobrarem 11 dígitos, é CPF
 * ("529.982.247-25" e "52998224725" valem o mesmo). Texto com "@" é sempre e-mail,
 * para um e-mail como "12345678901@exemplo.com" não ser confundido com CPF.
 *
 * Quem não pode entrar:
 * <ul>
 *   <li>conta EXCLUIDA: tratada como inexistente ({@link UsernameNotFoundException}), para não
 *       revelar que o e-mail/CPF já teve cadastro;</li>
 *   <li>conta PENDENTE (e-mail ainda não confirmado): {@link DisabledException};</li>
 *   <li>{@code bloqueadoAte} no futuro: {@link LockedException}.</li>
 * </ul>
 * O {@link LoginFailureHandler} transforma essas exceções na mensagem mostrada em /login.
 */
@Service
public class UsuarioDetailsService implements UserDetailsService {

    private static final int DIGITOS_CPF = 11;

    private final UsuarioRepository usuarioRepository;

    public UsuarioDetailsService(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String identificador) throws UsernameNotFoundException {
        Usuario usuario = buscar(identificador)
                .orElseThrow(() -> new UsernameNotFoundException("Usuário não encontrado"));

        switch (usuario.getStatus()) {
            case EXCLUIDA -> throw new UsernameNotFoundException("Conta excluída");
            case PENDENTE -> throw new DisabledException("Conta pendente de confirmação");
            case ATIVA -> { /* segue para a checagem de bloqueio */ }
        }

        Instant bloqueadoAte = usuario.getBloqueadoAte();
        if (bloqueadoAte != null && bloqueadoAte.isAfter(Instant.now())) {
            throw new LockedException("Conta temporariamente bloqueada");
        }

        return UsuarioAutenticado.de(usuario);
    }

    private Optional<Usuario> buscar(String identificador) {
        if (identificador == null || identificador.isBlank()) {
            return Optional.empty();
        }
        String valor = identificador.trim();

        if (!valor.contains("@")) {
            String digitos = valor.replaceAll("\\D", "");
            if (digitos.length() == DIGITOS_CPF) {
                return usuarioRepository.findByCpf(digitos);
            }
        }
        return usuarioRepository.findByEmailIgnoreCase(valor);
    }
}
