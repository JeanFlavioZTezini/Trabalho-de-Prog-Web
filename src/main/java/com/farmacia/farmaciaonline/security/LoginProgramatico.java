package com.farmacia.farmaciaonline.security;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.event.InteractiveAuthenticationSuccessEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;
import org.springframework.stereotype.Component;

import com.farmacia.farmaciaonline.model.Usuario;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Login sem formulário (F-20: retorno do provedor social). Faz o que o form login do Spring faz por nós: troca o id da
 * sessão (evita fixação), grava o contexto na sessão, registra a sessão no {@link SessionRegistry} (para a
 * {@link RevogacaoDeSessoes} achá-la) e publica o evento de login interativo, que mescla o carrinho
 * ({@code MesclagemCarrinhoNoLogin}). O destino segue a mesma regra do {@link LoginSuccessHandler}.
 */
@Component
public class LoginProgramatico {

    private final SessionRegistry sessionRegistry;
    private final ApplicationEventPublisher eventos;
    private final ObjectProvider<LoginTentativasService> tentativas;
    private final SecurityContextRepository contextos = new HttpSessionSecurityContextRepository();
    private final RequestCache requestCache = new HttpSessionRequestCache();

    public LoginProgramatico(SessionRegistry sessionRegistry, ApplicationEventPublisher eventos,
            ObjectProvider<LoginTentativasService> tentativas) {
        this.sessionRegistry = sessionRegistry;
        this.eventos = eventos;
        this.tentativas = tentativas;
    }

    /** Autentica a conta (já conferida pelo chamador) e devolve para onde redirecionar. */
    public String entrar(Usuario usuario, HttpServletRequest request, HttpServletResponse response) {
        request.getSession(true);
        request.changeSessionId();

        UsuarioAutenticado principal = UsuarioAutenticado.de(usuario);
        Authentication autenticacao = UsernamePasswordAuthenticationToken.authenticated(principal, null,
                principal.getAuthorities());
        SecurityContext contexto = SecurityContextHolder.createEmptyContext();
        contexto.setAuthentication(autenticacao);
        SecurityContextHolder.setContext(contexto);
        contextos.saveContext(contexto, request, response);
        sessionRegistry.registerNewSession(request.getSession().getId(), principal);

        eventos.publishEvent(new InteractiveAuthenticationSuccessEvent(autenticacao, LoginProgramatico.class));
        tentativas.ifAvailable(t -> t.limparFalhas(usuario.getId()));
        return destino(request, response);
    }

    private String destino(HttpServletRequest request, HttpServletResponse response) {
        String retorno = LoginSuccessHandler.retornoDoCadastro(request);
        SavedRequest guardada = requestCache.getRequest(request, response);
        requestCache.removeRequest(request, response);
        if (retorno != null) {
            return retorno;
        }
        return guardada != null ? guardada.getRedirectUrl() : "/";
    }
}
