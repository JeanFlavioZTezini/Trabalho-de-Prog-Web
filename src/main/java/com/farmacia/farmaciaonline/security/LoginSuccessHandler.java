package com.farmacia.farmaciaonline.security;

import java.io.IOException;

import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;

import com.farmacia.farmaciaonline.controller.CadastroController;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * Para onde ir depois do login:
 * <ul>
 *   <li>ADMIN sempre vai para {@code /admin};</li>
 *   <li>cliente que veio de um cadastro iniciado no carrinho ou no checkout volta para lá (F-18);</li>
 *   <li>cliente volta para a página que tentou abrir antes de ser mandado ao login
 *       (o Spring guarda esse pedido na sessão) ou, se não houver, para {@code /}.</li>
 * </ul>
 */
public class LoginSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {

    static final String PAINEL_ADMIN = "/admin";

    private final RequestCache requestCache = new HttpSessionRequestCache();
    private final LoginTentativasService tentativasService;

    public LoginSuccessHandler() {
        this(null);
    }

    public LoginSuccessHandler(LoginTentativasService tentativasService) {
        this.tentativasService = tentativasService;
        setRequestCache(requestCache);
        setDefaultTargetUrl("/");
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) throws IOException, ServletException {
        if (tentativasService != null && authentication.getPrincipal() instanceof UsuarioAutenticado usuario) {
            tentativasService.limparFalhas(usuario.getId());
        }
        if (ehAdmin(authentication)) {
            // Descarta a página que ele tentara abrir: o admin sempre começa pelo painel.
            requestCache.removeRequest(request, response);
            clearAuthenticationAttributes(request);
            getRedirectStrategy().sendRedirect(request, response, PAINEL_ADMIN);
            return;
        }
        String retorno = retornoDoCadastro(request);
        if (retorno != null) {
            // Veio de um cadastro iniciado no carrinho ou no checkout: volta para lá, em vez de ir para a página guardada.
            requestCache.removeRequest(request, response);
            clearAuthenticationAttributes(request);
            getRedirectStrategy().sendRedirect(request, response, retorno);
            return;
        }
        super.onAuthenticationSuccess(request, response, authentication);
    }

    /** Consome o {@code retornoCadastro} da sessão; só "carrinho" e "checkout" viram caminho (contrato da F-04). */
    private static String retornoDoCadastro(HttpServletRequest request) {
        HttpSession sessao = request.getSession(false);
        if (sessao == null) {
            return null;
        }
        Object valor = sessao.getAttribute(CadastroController.SESSAO_RETORNO);
        sessao.removeAttribute(CadastroController.SESSAO_RETORNO);
        return valor instanceof String texto ? CadastroController.destinoDoRetorno(texto) : null;
    }

    private static boolean ehAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(autoridade -> "ROLE_ADMIN".equals(autoridade.getAuthority()));
    }
}
