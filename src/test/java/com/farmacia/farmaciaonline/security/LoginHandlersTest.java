package com.farmacia.farmaciaonline.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;

/** Testes dos dois handlers do login, sem subir o Spring. */
class LoginHandlersTest {

    private final LoginSuccessHandler sucesso = new LoginSuccessHandler();
    private final LoginFailureHandler falha = new LoginFailureHandler();

    private static Authentication autenticado(String papel) {
        return new UsernamePasswordAuthenticationToken(
                "alguem", "n/a", List.of(new SimpleGrantedAuthority("ROLE_" + papel)));
    }

    private static MockHttpServletRequest requisicaoComPaginaGuardada(String caminho,
            MockHttpServletResponse resposta) {
        MockHttpServletRequest pedidoOriginal = new MockHttpServletRequest("GET", caminho);
        new HttpSessionRequestCache().saveRequest(pedidoOriginal, resposta);

        // o POST /login chega na mesma sessão em que o pedido original foi guardado
        MockHttpServletRequest login = new MockHttpServletRequest("POST", "/login");
        login.setSession(pedidoOriginal.getSession());
        return login;
    }

    // ---------- pós-login ----------

    @Test
    void adminVaiParaOPainel() throws Exception {
        MockHttpServletResponse resposta = new MockHttpServletResponse();

        sucesso.onAuthenticationSuccess(new MockHttpServletRequest("POST", "/login"), resposta, autenticado("ADMIN"));

        assertThat(resposta.getRedirectedUrl()).isEqualTo("/admin");
    }

    @Test
    void adminIgnoraAPaginaQueTentavaAbrir() throws Exception {
        MockHttpServletResponse resposta = new MockHttpServletResponse();
        MockHttpServletRequest login = requisicaoComPaginaGuardada("/carrinho/pagamento", resposta);

        sucesso.onAuthenticationSuccess(login, resposta, autenticado("ADMIN"));

        assertThat(resposta.getRedirectedUrl()).isEqualTo("/admin");
    }

    @Test
    void clienteSemPaginaAnteriorVaiParaAHome() throws Exception {
        MockHttpServletResponse resposta = new MockHttpServletResponse();

        sucesso.onAuthenticationSuccess(new MockHttpServletRequest("POST", "/login"), resposta, autenticado("CLIENTE"));

        assertThat(resposta.getRedirectedUrl()).isEqualTo("/");
    }

    @Test
    void clienteVoltaParaAPaginaQueTentavaAbrir() throws Exception {
        MockHttpServletResponse resposta = new MockHttpServletResponse();
        MockHttpServletRequest login = requisicaoComPaginaGuardada("/minha-conta", resposta);

        sucesso.onAuthenticationSuccess(login, resposta, autenticado("CLIENTE"));

        assertThat(resposta.getRedirectedUrl()).endsWith("/minha-conta");
    }

    // ---------- login recusado ----------

    private String destinoDaFalha(org.springframework.security.core.AuthenticationException excecao)
            throws Exception {
        MockHttpServletResponse resposta = new MockHttpServletResponse();
        falha.onAuthenticationFailure(new MockHttpServletRequest("POST", "/login"), resposta, excecao);
        return resposta.getRedirectedUrl();
    }

    @Test
    void senhaOuUsuarioErradoVoltaComMotivoCredenciais() throws Exception {
        assertThat(destinoDaFalha(new BadCredentialsException("x"))).isEqualTo("/login?erro=credenciais");
    }

    @Test
    void contaPendenteEmbrulhadaPeloSpringVoltaComMotivoPendente() throws Exception {
        // O DaoAuthenticationProvider embrulha a exceção do UserDetailsService assim:
        var embrulhada = new InternalAuthenticationServiceException("x", new DisabledException("pendente"));

        assertThat(destinoDaFalha(embrulhada)).isEqualTo("/login?erro=pendente");
    }

    @Test
    void contaBloqueadaEmbrulhadaPeloSpringVoltaComMotivoBloqueada() throws Exception {
        var embrulhada = new InternalAuthenticationServiceException("x", new LockedException("bloqueada"));

        assertThat(destinoDaFalha(embrulhada)).isEqualTo("/login?erro=bloqueada");
    }

    @Test
    void falhaDoSistemaNaoViraSenhaIncorreta() throws Exception {
        var banco = new InternalAuthenticationServiceException("x", new IllegalStateException("banco fora do ar"));

        assertThat(destinoDaFalha(banco)).isEqualTo("/login?erro=indisponivel");
    }
}
