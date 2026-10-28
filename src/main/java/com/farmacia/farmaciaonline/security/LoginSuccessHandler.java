package com.farmacia.farmaciaonline.security;

import java.io.IOException;

import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Para onde ir depois do login:
 * <ul>
 *   <li>ADMIN sempre vai para {@code /admin};</li>
 *   <li>cliente volta para a página que tentou abrir antes de ser mandado ao login
 *       (o Spring guarda esse pedido na sessão) ou, se não houver, para {@code /}.</li>
 * </ul>
 */
public class LoginSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {

    static final String PAINEL_ADMIN = "/admin";

    private final RequestCache requestCache = new HttpSessionRequestCache();

    public LoginSuccessHandler() {
        setRequestCache(requestCache);
        setDefaultTargetUrl("/");
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) throws IOException, ServletException {
        if (ehAdmin(authentication)) {
            // Descarta a página que ele tentara abrir: o admin sempre começa pelo painel.
            requestCache.removeRequest(request, response);
            clearAuthenticationAttributes(request);
            getRedirectStrategy().sendRedirect(request, response, PAINEL_ADMIN);
            return;
        }
        super.onAuthenticationSuccess(request, response, authentication);
    }

    private static boolean ehAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(autoridade -> "ROLE_ADMIN".equals(autoridade.getAuthority()));
    }
}
