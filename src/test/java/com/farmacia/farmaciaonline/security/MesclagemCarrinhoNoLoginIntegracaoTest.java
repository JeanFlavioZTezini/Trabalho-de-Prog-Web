package com.farmacia.farmaciaonline.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import com.farmacia.farmaciaonline.TestcontainersConfiguration;
import com.farmacia.farmaciaonline.model.Apresentacao;
import com.farmacia.farmaciaonline.model.Produto;
import com.farmacia.farmaciaonline.repository.ApresentacaoRepository;
import com.farmacia.farmaciaonline.repository.CarrinhoRepository;
import com.farmacia.farmaciaonline.repository.ProdutoRepository;
import com.farmacia.farmaciaonline.repository.UsuarioRepository;
import com.farmacia.farmaciaonline.service.CarrinhoService;
import com.farmacia.farmaciaonline.service.DonoCarrinho;
import com.farmacia.farmaciaonline.service.ResumoCarrinho;

import jakarta.servlet.http.Cookie;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class MesclagemCarrinhoNoLoginIntegracaoTest {
    @Autowired
    private MockMvc mvc;

    @Autowired
    private CarrinhoService carrinhoService;

    @Autowired
    private CarrinhoRepository carrinhoRepository;

    @Autowired
    private ProdutoRepository produtoRepository;

    @Autowired
    private ApresentacaoRepository apresentacaoRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    private DonoCarrinho cliente;

    @BeforeEach
    void cliente() {
        cliente = DonoCarrinho.deUsuario(usuarioRepository.findByEmailIgnoreCase("cliente@farma.com").orElseThrow().getId());
    }

    private Apresentacao apresentacao(int estoque, int limite) {
        Produto produto = new Produto();
        produto.setNome("Produto de login " + UUID.randomUUID());
        produto.setMarca("Marca de teste");
        produtoRepository.saveAndFlush(produto);

        Apresentacao apresentacao = new Apresentacao();
        apresentacao.setProduto(produto);
        apresentacao.setDescricao("10 comprimidos");
        apresentacao.setSku("TESTE-LOGIN-" + UUID.randomUUID().toString().substring(0, 8));
        apresentacao.setEan(String.format("98%012d", Math.abs(UUID.randomUUID().getMostSignificantBits()) % 1_000_000_000_000L));
        apresentacao.setPreco(new BigDecimal("10.00"));
        apresentacao.setEstoque(estoque);
        apresentacao.setLimitePorPedido(limite);
        apresentacaoRepository.saveAndFlush(apresentacao);
        produto.getApresentacoes().add(apresentacao);
        return apresentacao;
    }

    private MvcResult entrarComCookie(UUID token) throws Exception {
        return mvc.perform(post("/login").with(csrf())
                        .cookie(new Cookie(CarrinhoCookie.NOME, token.toString()))
                        .param("identificador", "cliente@farma.com")
                        .param("senha", "Farma@123"))
                .andExpect(redirectedUrl("/"))
                .andExpect(authenticated())
                .andReturn();
    }

    @Test
    @DisplayName("ao entrar, o carrinho do visitante vira parte do da conta e o cookie é apagado")
    void loginMescla() throws Exception {
        Apresentacao apresentacao = apresentacao(50, 10);
        UUID token = UUID.randomUUID();
        carrinhoService.adicionar(cliente, apresentacao.getId(), 3);
        carrinhoService.adicionar(DonoCarrinho.deVisitante(token), apresentacao.getId(), 2);

        mvc.perform(post("/login").with(csrf())
                        .cookie(new Cookie(CarrinhoCookie.NOME, token.toString()))
                        .param("identificador", "cliente@farma.com")
                        .param("senha", "Farma@123"))
                .andExpect(redirectedUrl("/"))
                .andExpect(cookie().maxAge(CarrinhoCookie.NOME, 0));

        ResumoCarrinho resumo = carrinhoService.resumo(cliente);
        assertThat(resumo.getLinhas()).hasSize(1);
        assertThat(resumo.getLinhas().get(0).getQuantidade()).isEqualTo(5);
        assertThat(carrinhoRepository.findByToken(token)).isEmpty();
    }

    @Test
    @DisplayName("os ajustes da mesclagem ficam na sessão para serem mostrados no carrinho")
    void avisosFicamNaSessao() throws Exception {
        Apresentacao apresentacao = apresentacao(4, 10);
        UUID token = UUID.randomUUID();
        carrinhoService.adicionar(cliente, apresentacao.getId(), 3);
        carrinhoService.adicionar(DonoCarrinho.deVisitante(token), apresentacao.getId(), 3);

        MvcResult resultado = entrarComCookie(token);

        Object avisos = resultado.getRequest().getSession(false).getAttribute(MesclagemCarrinhoNoLogin.AVISOS_NA_SESSAO);
        assertThat(avisos).isInstanceOf(List.class);
        assertThat((List<?>) avisos).hasSize(1);
        assertThat(carrinhoService.resumo(cliente).getLinhas().get(0).getQuantidade()).isEqualTo(4);
    }

    @Test
    @DisplayName("entrar de novo com o mesmo cookie não duplica o que já foi mesclado")
    void segundoLoginNaoDuplica() throws Exception {
        Apresentacao apresentacao = apresentacao(50, 10);
        UUID token = UUID.randomUUID();
        carrinhoService.adicionar(DonoCarrinho.deVisitante(token), apresentacao.getId(), 2);

        entrarComCookie(token);
        entrarComCookie(token);

        ResumoCarrinho resumo = carrinhoService.resumo(cliente);
        assertThat(resumo.getLinhas()).hasSize(1);
        assertThat(resumo.getLinhas().get(0).getQuantidade()).isEqualTo(2);
    }

    @Test
    @DisplayName("novo login depois de sair: o carrinho da conta foi preservado e o novo carrinho de visitante se junta a ele")
    void novoLoginAposLogout() throws Exception {
        Apresentacao primeira = apresentacao(50, 10);
        Apresentacao segunda = apresentacao(50, 10);

        UUID primeiroToken = UUID.randomUUID();
        carrinhoService.adicionar(DonoCarrinho.deVisitante(primeiroToken), primeira.getId(), 2);
        entrarComCookie(primeiroToken);

        UUID segundoToken = UUID.randomUUID();
        carrinhoService.adicionar(DonoCarrinho.deVisitante(segundoToken), segunda.getId(), 1);
        entrarComCookie(segundoToken);

        ResumoCarrinho resumo = carrinhoService.resumo(cliente);
        assertThat(resumo.getLinhas()).hasSize(2);
        assertThat(resumo.getTotalUnidades()).isEqualTo(3);
        assertThat(carrinhoRepository.findByToken(primeiroToken)).isEmpty();
        assertThat(carrinhoRepository.findByToken(segundoToken)).isEmpty();
    }

    @Test
    @DisplayName("login sem cookie de carrinho segue normalmente e não mexe no carrinho da conta")
    void loginSemCookie() throws Exception {
        Apresentacao apresentacao = apresentacao(50, 10);
        carrinhoService.adicionar(cliente, apresentacao.getId(), 2);

        mvc.perform(post("/login").with(csrf())
                        .param("identificador", "cliente@farma.com")
                        .param("senha", "Farma@123"))
                .andExpect(redirectedUrl("/"));

        assertThat(carrinhoService.resumo(cliente).getTotalUnidades()).isEqualTo(2);
    }

    @Test
    @DisplayName("depois de mesclar, a sessão guarda o aviso com o total de itens da conta")
    void avisoDeCarrinhoMantidoAposMesclar() throws Exception {
        Apresentacao apresentacao = apresentacao(50, 10);
        UUID token = UUID.randomUUID();
        carrinhoService.adicionar(cliente, apresentacao.getId(), 1);
        carrinhoService.adicionar(DonoCarrinho.deVisitante(token), apresentacao.getId(), 2);

        MvcResult resultado = entrarComCookie(token);

        assertThat(resultado.getRequest().getSession(false).getAttribute(MesclagemCarrinhoNoLogin.AVISO_MANTIDO_NA_SESSAO))
                .isEqualTo("Você tem 3 unidades no seu carrinho.");
    }

    @Test
    @DisplayName("sem cookie, mas com itens na conta, o aviso também aparece (singular para 1 item)")
    void avisoDeCarrinhoMantidoSemCookie() throws Exception {
        carrinhoService.adicionar(cliente, apresentacao(50, 10).getId(), 1);

        MvcResult resultado = mvc.perform(post("/login").with(csrf())
                        .param("identificador", "cliente@farma.com")
                        .param("senha", "Farma@123"))
                .andExpect(redirectedUrl("/"))
                .andReturn();

        assertThat(resultado.getRequest().getSession(false).getAttribute(MesclagemCarrinhoNoLogin.AVISO_MANTIDO_NA_SESSAO))
                .isEqualTo("Você tem 1 unidade no seu carrinho.");
    }

    @Test
    @DisplayName("carrinho vazio no login: nenhum aviso na sessão")
    void semItensSemAviso() throws Exception {
        MvcResult resultado = mvc.perform(post("/login").with(csrf())
                        .param("identificador", "cliente@farma.com")
                        .param("senha", "Farma@123"))
                .andExpect(redirectedUrl("/"))
                .andReturn();

        assertThat(resultado.getRequest().getSession(false).getAttribute(MesclagemCarrinhoNoLogin.AVISO_MANTIDO_NA_SESSAO))
                .isNull();
    }

    @Test
    @DisplayName("administrador que entra com cookie de visitante não ganha carrinho: o carrinho do visitante fica no cookie")
    void administradorNaoMescla() throws Exception {
        Apresentacao apresentacao = apresentacao(50, 10);
        UUID token = UUID.randomUUID();
        carrinhoService.adicionar(DonoCarrinho.deVisitante(token), apresentacao.getId(), 2);
        Long adminId = usuarioRepository.findByEmailIgnoreCase("admin@farma.com").orElseThrow().getId();

        mvc.perform(post("/login").with(csrf())
                        .cookie(new Cookie(CarrinhoCookie.NOME, token.toString()))
                        .param("identificador", "admin@farma.com")
                        .param("senha", "Farma@123"))
                .andExpect(redirectedUrl("/admin"))
                .andExpect(authenticated());

        assertThat(carrinhoService.contarUnidades(DonoCarrinho.deUsuario(adminId))).isZero();
        assertThat(carrinhoRepository.findByToken(token)).isPresent();
    }
}
