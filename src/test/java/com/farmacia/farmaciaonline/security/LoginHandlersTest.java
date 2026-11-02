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

        assertThat(resposta.getRedirectedUrl()).isNotNull();
        assertThat(new java.net.URI(resposta.getRedirectedUrl()).getPath()).isEqualTo("/minha-conta");
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

    // ---------- F-18: retorno depois do cadastro ----------

    private static MockHttpServletRequest loginComRetorno(Object retorno) {
        MockHttpServletRequest login = new MockHttpServletRequest("POST", "/login");
        login.getSession().setAttribute(com.farmacia.farmaciaonline.controller.CadastroController.SESSAO_RETORNO, retorno);
        return login;
    }

    @Test
    void clienteQueVeioDoCadastroNoCheckoutVoltaParaOCheckout() throws Exception {
        MockHttpServletResponse resposta = new MockHttpServletResponse();
        MockHttpServletRequest login = loginComRetorno("checkout");

        sucesso.onAuthenticationSuccess(login, resposta, autenticado("CLIENTE"));

        assertThat(resposta.getRedirectedUrl()).isEqualTo("/checkout");
        assertThat(login.getSession().getAttribute("retornoCadastro")).isNull();
    }

    @Test
    void clienteQueVeioDoCadastroNoCarrinhoVoltaParaOCarrinho() throws Exception {
        MockHttpServletResponse resposta = new MockHttpServletResponse();

        sucesso.onAuthenticationSuccess(loginComRetorno("carrinho"), resposta, autenticado("CLIENTE"));

        assertThat(resposta.getRedirectedUrl()).isEqualTo("/carrinho");
    }

    @Test
    void retornoDesconhecidoNaSessaoEIgnoradoEApagado() throws Exception {
        MockHttpServletResponse resposta = new MockHttpServletResponse();
        MockHttpServletRequest login = loginComRetorno("https://site-malicioso.example");

        sucesso.onAuthenticationSuccess(login, resposta, autenticado("CLIENTE"));

        assertThat(resposta.getRedirectedUrl()).isEqualTo("/");
        assertThat(login.getSession().getAttribute("retornoCadastro")).isNull();
    }

    @Test
    void adminContinuaNoPainelMesmoComRetornoNaSessao() throws Exception {
        MockHttpServletResponse resposta = new MockHttpServletResponse();

        sucesso.onAuthenticationSuccess(loginComRetorno("checkout"), resposta, autenticado("ADMIN"));

        assertThat(resposta.getRedirectedUrl()).isEqualTo("/admin");
    }

    // ---------- F-18: só senha incorreta conta como tentativa ----------

    @Test
    void soSenhaIncorretaRegistraFalha() throws Exception {
        var servico = org.mockito.Mockito.mock(LoginTentativasService.class);
        var handler = new LoginFailureHandler(servico);
        MockHttpServletRequest pedido = new MockHttpServletRequest("POST", "/login");
        pedido.setParameter("identificador", "cliente@farma.com");

        handler.onAuthenticationFailure(pedido, new MockHttpServletResponse(), new BadCredentialsException("x"));
        handler.onAuthenticationFailure(pedido, new MockHttpServletResponse(),
                new InternalAuthenticationServiceException("x", new LockedException("bloqueada")));
        handler.onAuthenticationFailure(pedido, new MockHttpServletResponse(),
                new InternalAuthenticationServiceException("x", new DisabledException("pendente")));
        handler.onAuthenticationFailure(pedido, new MockHttpServletResponse(),
                new InternalAuthenticationServiceException("x", new IllegalStateException("banco")));

        org.mockito.Mockito.verify(servico, org.mockito.Mockito.times(1)).registrarFalha("cliente@farma.com");
    }

    @Test
    void erroAoGravarATentativaNaoDerrubaOLogin() throws Exception {
        var servico = org.mockito.Mockito.mock(LoginTentativasService.class);
        org.mockito.Mockito.doThrow(new org.springframework.dao.DataAccessResourceFailureException("banco"))
                .when(servico).registrarFalha(org.mockito.ArgumentMatchers.any());
        MockHttpServletResponse resposta = new MockHttpServletResponse();

        new LoginFailureHandler(servico).onAuthenticationFailure(new MockHttpServletRequest("POST", "/login"),
                resposta, new BadCredentialsException("x"));

        assertThat(resposta.getRedirectedUrl()).isEqualTo("/login?erro=credenciais");
    }
}
