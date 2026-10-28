package com.farmacia.farmaciaonline.security;

import java.io.IOException;

import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.DefaultRedirectStrategy;
import org.springframework.security.web.RedirectStrategy;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Login recusado: volta para {@code /login?erro=<motivo>}, e o LoginController escolhe a mensagem.
 *
 * Por que desembrulhar a exceção: o DaoAuthenticationProvider embrulha qualquer exceção lançada
 * pelo UserDetailsService (como LockedException) numa InternalAuthenticationServiceException.
 * Sem olhar a causa, conta bloqueada e conta pendente apareceriam como "erro interno".
 */
public class LoginFailureHandler implements AuthenticationFailureHandler {

    public static final String MOTIVO_CREDENCIAIS = "credenciais";
    public static final String MOTIVO_PENDENTE = "pendente";
    public static final String MOTIVO_BLOQUEADA = "bloqueada";
    public static final String MOTIVO_INDISPONIVEL = "indisponivel";

    private final RedirectStrategy redirectStrategy = new DefaultRedirectStrategy();

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException exception) throws IOException {
        redirectStrategy.sendRedirect(request, response, "/login?erro=" + motivo(exception));
    }

    static String motivo(AuthenticationException exception) {
        Throwable causa = exception;
        if (exception instanceof InternalAuthenticationServiceException && exception.getCause() != null) {
            causa = exception.getCause();
        }

        if (causa instanceof LockedException) {
            return MOTIVO_BLOQUEADA;
        }
        if (causa instanceof DisabledException) {
            return MOTIVO_PENDENTE;
        }
        if (exception instanceof InternalAuthenticationServiceException) {
            return MOTIVO_INDISPONIVEL; // falha do sistema (ex.: banco fora do ar), não erro da pessoa
        }
        return MOTIVO_CREDENCIAIS;
    }
}
