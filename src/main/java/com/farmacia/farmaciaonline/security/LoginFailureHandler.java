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

/**
 * Login recusado: volta para {@code /login?erro=<motivo>}, e o LoginController escolhe a mensagem. Só senha realmente
 * incorreta conta como tentativa (F-18); conta bloqueada, pendente ou falha do sistema não contam.
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
    /** O que foi digitado em "E-mail ou CPF", para a tela de login voltar preenchida (na sessão, nunca na URL). */
    public static final String SESSAO_IDENTIFICADOR = "loginIdentificador";

    private static final Logger log = LoggerFactory.getLogger(LoginFailureHandler.class);
    private final RedirectStrategy redirectStrategy = new DefaultRedirectStrategy();
    private final LoginTentativasService tentativasService;
    private final LimiteDeTentativasEmMemoria tentativasEmMemoria;

    /** Mantido para os testes unitários dos redirecionamentos. */
    public LoginFailureHandler() {
        this(null, null);
    }

    public LoginFailureHandler(LoginTentativasService tentativasService) {
        this(tentativasService, null);
    }

    public LoginFailureHandler(LoginTentativasService tentativasService, LimiteDeTentativasEmMemoria tentativasEmMemoria) {
        this.tentativasService = tentativasService;
        this.tentativasEmMemoria = tentativasEmMemoria;
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException exception) throws IOException {
        String motivo = motivo(exception);
        String identificador = request.getParameter("identificador");
        if (exception instanceof BadCredentialsException && tentativasEmMemoria != null
                && tentativasEmMemoria.bloqueado(identificador)) {
            // Identificador sem conta ativa que já errou 5 vezes: mesma resposta da conta real bloqueada.
            motivo = MOTIVO_BLOQUEADA;
        } else if (exception instanceof BadCredentialsException && tentativasService != null) {
            try {
                boolean contada = tentativasService.registrarFalhaEContar(identificador);
                if (!contada && tentativasEmMemoria != null) {
                    tentativasEmMemoria.registrarFalha(identificador);
                }
            } catch (DataAccessException erro) {
                // A falha de persistência não deve ser convertida em uma tentativa de senha incorreta.
                log.warn("Não foi possível registrar a tentativa de login", erro);
            }
        }
        if (identificador != null && identificador.length() <= 255) {
            request.getSession().setAttribute(SESSAO_IDENTIFICADOR, identificador);
        }
        redirectStrategy.sendRedirect(request, response, "/login?erro=" + motivo);
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
