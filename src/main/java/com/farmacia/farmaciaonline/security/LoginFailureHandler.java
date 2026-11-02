package com.farmacia.farmaciaonline.security;

import java.io.IOException;

import org.springframework.dao.DataAccessException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.DefaultRedirectStrategy;
import org.springframework.security.web.RedirectStrategy;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Redireciona com motivo genérico e contabiliza somente credenciais realmente incorretas. */
public class LoginFailureHandler implements AuthenticationFailureHandler {

    public static final String MOTIVO_CREDENCIAIS = "credenciais";
    public static final String MOTIVO_PENDENTE = "pendente";
    public static final String MOTIVO_BLOQUEADA = "bloqueada";
    public static final String MOTIVO_INDISPONIVEL = "indisponivel";

    private static final Logger log = LoggerFactory.getLogger(LoginFailureHandler.class);
    private final RedirectStrategy redirectStrategy = new DefaultRedirectStrategy();
    private final LoginTentativasService tentativasService;

    /** Mantido para os testes unitários dos redirecionamentos. */
    public LoginFailureHandler() {
        this(null);
    }

    public LoginFailureHandler(LoginTentativasService tentativasService) {
        this.tentativasService = tentativasService;
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException exception) throws IOException {
        String motivo = motivo(exception);
        if (exception instanceof BadCredentialsException && tentativasService != null) {
            try {
                tentativasService.registrarFalha(request.getParameter("identificador"));
            } catch (DataAccessException erro) {
                // A falha de persistência não deve ser convertida em uma tentativa de senha incorreta.
                log.warn("Não foi possível registrar a tentativa de login", erro);
            }
        }
        redirectStrategy.sendRedirect(request, response, "/login?erro=" + motivo);
    }

    static String motivo(AuthenticationException exception) {
        Throwable causa = exception;
        if (exception instanceof InternalAuthenticationServiceException && exception.getCause() != null) {
            causa = exception.getCause();
        }
        if (causa instanceof LockedException) return MOTIVO_BLOQUEADA;
        if (causa instanceof DisabledException) return MOTIVO_PENDENTE;
        if (exception instanceof InternalAuthenticationServiceException) return MOTIVO_INDISPONIVEL;
        if (exception instanceof BadCredentialsException) return MOTIVO_CREDENCIAIS;
        return MOTIVO_CREDENCIAIS;
    }
}
