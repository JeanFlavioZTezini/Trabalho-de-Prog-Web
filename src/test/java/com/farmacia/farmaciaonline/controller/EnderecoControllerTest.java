package com.farmacia.farmaciaonline.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import com.farmacia.farmaciaonline.cep.CepIndisponivelException;
import com.farmacia.farmaciaonline.cep.ConsultaCep;
import com.farmacia.farmaciaonline.cep.EnderecoCep;
import com.farmacia.farmaciaonline.config.SecurityConfig;
import com.farmacia.farmaciaonline.model.Endereco;
import com.farmacia.farmaciaonline.model.EnderecoForm;
import com.farmacia.farmaciaonline.model.Usuario;
import com.farmacia.farmaciaonline.model.enums.Perfil;
import com.farmacia.farmaciaonline.model.enums.StatusUsuario;
import com.farmacia.farmaciaonline.repository.CategoriaRepository;
import com.farmacia.farmaciaonline.security.UsuarioAutenticado;
import com.farmacia.farmaciaonline.service.EnderecoService;

@WebMvcTest(EnderecoController.class)
@Import(SecurityConfig.class)
class EnderecoControllerTest {

    private static final long CLIENTE = 2L;

    @MockBean
    private EnderecoService service;

    @MockBean
    private ConsultaCep consultaCep;

    @MockBean
    private CategoriaRepository categoriaRepository;

    @Autowired
    private MockMvc mvc;

    private static RequestPostProcessor cliente(long id) {
        Usuario usuario = Usuario.builder()
                .id(id)
                .nome("Maria da Silva")
                .email("cliente@farma.com")
                .dataNascimento(LocalDate.of(1995, 5, 20))
                .senhaHash("hash")
                .perfil(Perfil.CLIENTE)
                .status(StatusUsuario.ATIVA)
                .termosVersao("1.0")
                .termosAceitosEm(Instant.now())
                .build();
        return user(UsuarioAutenticado.de(usuario));
    }

    private static MockHttpServletRequestBuilder comDadosValidos(MockHttpServletRequestBuilder b) {
        return b.param("destinatario", "Maria da Silva")
                .param("cep", "01310-100")
                .param("logradouro", "Avenida Paulista")
                .param("numero", "1000")
                .param("bairro", "Bela Vista")
                .param("cidade", "São Paulo")
                .param("uf", "SP");
    }

    private String html(MockHttpServletRequestBuilder requisicao) throws Exception {
        return mvc.perform(requisicao).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private static void assertLayoutComUmH1(String html) {
        assertThat(html).contains("lang=\"pt-BR\"");
        assertThat(StringUtils.countOccurrencesOf(html, "<header")).isEqualTo(1);
        assertThat(StringUtils.countOccurrencesOf(html, "<footer")).isEqualTo(1);
        assertThat(StringUtils.countOccurrencesOf(html, "<main id=\"conteudo\"")).isEqualTo(1);
        assertThat(StringUtils.countOccurrencesOf(html, "<h1")).isEqualTo(1);
    }

    private static ResponseStatusException naoEncontrado() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND);
    }

    @Test
    void visitanteEhMandadoParaOLoginEmTodasAsRotas() throws Exception {
        for (String url : List.of("/enderecos", "/enderecos/novo", "/enderecos/1/editar", "/enderecos/1/excluir")) {
            mvc.perform(get(url)).andExpect(redirectedUrlPattern("**/login*"));
        }
        for (String url : List.of("/enderecos", "/enderecos/1", "/enderecos/cep", "/enderecos/1/cep",
                "/enderecos/1/excluir", "/enderecos/1/selecionar")) {
            mvc.perform(post(url).with(csrf())).andExpect(redirectedUrlPattern("**/login*"));
        }
    }

    @Test
    @WithMockUser(roles = "CLIENTE")
    void sessaoSemOPrincipalDaAplicacaoRecebe403() throws Exception {
        mvc.perform(get("/enderecos")).andExpect(status().isForbidden());
        mvc.perform(post("/enderecos/1/excluir").with(csrf())).andExpect(status().isForbidden());
    }

    @Test
    void postSemTokenCsrfEhRecusado() throws Exception {
        mvc.perform(post("/enderecos/1/excluir").with(cliente(CLIENTE))).andExpect(status().isForbidden());
    }

    @Test
    void listaMostraOsEnderecosDoUsuarioDaSessao() throws Exception {
        Endereco e = new Endereco();
        e.setDestinatario("Maria da Silva");
        e.setCep("01310100");
        e.setLogradouro("Avenida Paulista");
        e.setNumero("1000");
        e.setBairro("Bela Vista");
        e.setCidade("São Paulo");
        e.setUf("SP");
        when(service.listar(CLIENTE)).thenReturn(List.of(e));

        String html = html(get("/enderecos").with(cliente(CLIENTE)));

        assertLayoutComUmH1(html);
        assertThat(html).contains("Meus endereços").contains("Avenida Paulista, 1000").contains("01310-100");
        assertThat(html).doesNotContain("Usar este endereço").contains("class=\"tabela-rolavel\"");
    }

    @Test
    void listaVaziaAvisa() throws Exception {
        when(service.listar(CLIENTE)).thenReturn(List.of());

        assertThat(html(get("/enderecos").with(cliente(CLIENTE)))).contains("nenhum endereço");
    }

    @Test
    void listaNoModoCheckoutOfereceUsarEnderecoEPreservaORetorno() throws Exception {
        Endereco e = new Endereco();
        e.setDestinatario("Maria da Silva");
        e.setCep("01310100");
        e.setLogradouro("Avenida Paulista");
        e.setNumero("1000");
        e.setBairro("Bela Vista");
        e.setCidade("São Paulo");
        e.setUf("SP");
        when(service.listar(CLIENTE)).thenReturn(List.of(e));

        String html = html(get("/enderecos").param("retorno", "checkout").with(cliente(CLIENTE)));

        assertThat(html).contains("Usar este endereço").contains("retorno=checkout").contains("Voltar ao checkout");
    }

    @Test
    void formularioNovoTemUmH1ECamposRotulados() throws Exception {
        String html = html(get("/enderecos/novo").with(cliente(CLIENTE)));

        assertLayoutComUmH1(html);
        for (String campo : List.of("destinatario", "cep", "logradouro", "numero", "complemento", "bairro",
                "cidade", "uf", "referencia")) {
            assertThat(html).contains("for=\"" + campo + "\"").contains("id=\"" + campo + "\"");
        }
        assertThat(html).contains("action=\"/enderecos\"").contains("name=\"_csrf\"");
        assertThat(html).contains("src=\"/js/cep.js\"");
    }

    @Test
    void enterNoFormularioSalvaEmVezDeBuscarCep() throws Exception {
        String html = html(get("/enderecos/novo").with(cliente(CLIENTE)));

        // o primeiro botão de envio (o padrão do Enter) é o de salvar, antes de qualquer "formaction"
        assertThat(html.indexOf("Salvar endereço</button>")).isNotNegative().isLessThan(html.indexOf("formaction="));
    }

    @Test
    void paginaDeExclusaoPedeConfirmacao() throws Exception {
        Endereco e = new Endereco();
        e.setId(5L);
        e.setDestinatario("Maria da Silva");
        e.setCep("01310100");
        e.setLogradouro("Avenida Paulista");
        e.setNumero("1000");
        e.setBairro("Bela Vista");
        e.setCidade("São Paulo");
        e.setUf("SP");
        when(service.buscar(CLIENTE, 5L)).thenReturn(e);

        String html = html(get("/enderecos/5/excluir").with(cliente(CLIENTE)));

        assertLayoutComUmH1(html);
        assertThat(html).contains("Tem certeza").contains("action=\"/enderecos/5/excluir\"");
        verify(service, never()).excluir(any(), any());
    }

    @Test
    void enderecoDeOutroClienteRespondeNotFoundEmTodasAsRotas() throws Exception {
        when(service.formDe(CLIENTE, 99L)).thenThrow(naoEncontrado());
        when(service.buscar(CLIENTE, 99L)).thenThrow(naoEncontrado());
        doThrow(naoEncontrado()).when(service).excluir(CLIENTE, 99L);

        mvc.perform(get("/enderecos/99/editar").with(cliente(CLIENTE))).andExpect(status().isNotFound());
        mvc.perform(get("/enderecos/99/excluir").with(cliente(CLIENTE))).andExpect(status().isNotFound());
        mvc.perform(comDadosValidos(post("/enderecos/99")).with(cliente(CLIENTE)).with(csrf()))
                .andExpect(status().isNotFound());
        mvc.perform(post("/enderecos/99/excluir").with(cliente(CLIENTE)).with(csrf()))
                .andExpect(status().isNotFound());
        mvc.perform(post("/enderecos/99/selecionar").param("retorno", "checkout").with(cliente(CLIENTE)).with(csrf()))
                .andExpect(status().isNotFound());
        mvc.perform(post("/enderecos/99/cep").param("cep", "01310100").with(cliente(CLIENTE)).with(csrf()))
                .andExpect(status().isNotFound());

        verify(service, never()).salvar(any(), eq(99L), any());
    }

    @Test
    void donoVemSempreDaSessaoENuncaDaRequisicao() throws Exception {
        mvc.perform(post("/enderecos/99/excluir")
                .param("usuarioId", "1").param("usuario.id", "1").param("id", "1")
                .with(cliente(CLIENTE)).with(csrf()))
                .andExpect(status().is3xxRedirection());

        verify(service).excluir(CLIENTE, 99L);
        verify(service, never()).excluir(eq(1L), any());
    }

    @Test
    void postDeCriacaoIgnoraIdEUsuarioEnviadosNoCorpo() throws Exception {
        when(service.salvar(eq(CLIENTE), isNull(), any(EnderecoForm.class))).thenReturn(7L);

        mvc.perform(comDadosValidos(post("/enderecos")).param("usuarioId", "1").param("id", "1")
                .with(cliente(CLIENTE)).with(csrf()))
                .andExpect(redirectedUrl("/enderecos"));

        verify(service).salvar(eq(CLIENTE), isNull(), any(EnderecoForm.class));
        verify(service, never()).salvar(eq(1L), any(), any());
    }

    @Test
    void salvarNoModoCheckoutVoltaAoCheckoutComOEnderecoNovo() throws Exception {
        when(service.salvar(eq(CLIENTE), isNull(), any(EnderecoForm.class))).thenReturn(7L);

        mvc.perform(comDadosValidos(post("/enderecos")).param("retorno", "checkout")
                .with(cliente(CLIENTE)).with(csrf()))
                .andExpect(redirectedUrl("/checkout?enderecoId=7"));
    }

    @Test
    void retornoDiferenteDeCheckoutEhIgnorado() throws Exception {
        when(service.salvar(eq(CLIENTE), isNull(), any(EnderecoForm.class))).thenReturn(7L);

        for (String retorno : List.of("https://evil.example", "//evil.example", "/admin", "CHECKOUT", "")) {
            mvc.perform(comDadosValidos(post("/enderecos")).param("retorno", retorno)
                    .with(cliente(CLIENTE)).with(csrf()))
                    .andExpect(redirectedUrl("/enderecos"));
        }
    }

    @Test
    void selecionarEnderecoProprioVoltaAoCheckout() throws Exception {
        mvc.perform(post("/enderecos/5/selecionar").param("retorno", "checkout")
                .with(cliente(CLIENTE)).with(csrf()))
                .andExpect(redirectedUrl("/checkout?enderecoId=5"));

        verify(service).buscar(CLIENTE, 5L);
    }

    @Test
    void selecionarSemRetornoDeCheckoutVoltaParaALista() throws Exception {
        mvc.perform(post("/enderecos/5/selecionar").param("retorno", "https://evil.example")
                .with(cliente(CLIENTE)).with(csrf()))
                .andExpect(redirectedUrl("/enderecos"));
    }

    @Test
    void excluirNoModoCheckoutVoltaParaAListaDoCheckout() throws Exception {
        mvc.perform(post("/enderecos/5/excluir").param("retorno", "checkout")
                .with(cliente(CLIENTE)).with(csrf()))
                .andExpect(redirectedUrl("/enderecos?retorno=checkout"));

        verify(service).excluir(CLIENTE, 5L);
    }

    @Test
    void cancelarNoModoCheckoutApontaParaOCheckout() throws Exception {
        String html = html(get("/enderecos/novo").param("retorno", "checkout").with(cliente(CLIENTE)));

        assertThat(html).contains("href=\"/checkout\"").contains("name=\"retorno\" value=\"checkout\"");
    }

    @Test
    void formularioInvalidoVoltaComMensagensENaoGrava() throws Exception {
        String html = html(post("/enderecos").param("cep", "123").param("uf", "XX")
                .with(cliente(CLIENTE)).with(csrf()));

        assertLayoutComUmH1(html);
        assertThat(html).contains("Informe o destinat").contains("CEP inv").contains("UF inv")
                .contains("aria-invalid=\"true\"");
        verify(service, never()).salvar(any(), any(), any());
    }

    @Test
    void cepLocalizadoPreencheOEnderecoEMantemOQueJaFoiDigitado() throws Exception {
        when(consultaCep.consultar("01310100")).thenReturn(java.util.Optional.of(
                new EnderecoCep("01310100", "Avenida Paulista", "Bela Vista", "São Paulo", "SP")));

        String html = html(post("/enderecos/cep").param("cep", "01310-100").param("numero", "1000")
                .param("destinatario", "Maria").with(cliente(CLIENTE)).with(csrf()));

        assertThat(html).contains("value=\"Avenida Paulista\"").contains("value=\"Bela Vista\"")
                .contains("value=\"1000\"").contains("value=\"Maria\"")
                .contains("Endereço preenchido pelo CEP");
        assertThat(html).containsPattern("<option value=\"SP\"[^>]*selected");
    }

    @Test
    void cepNaoLocalizadoPedePreenchimentoManualEMantemOFormulario() throws Exception {
        String html = html(post("/enderecos/cep").param("cep", "12345-678").param("destinatario", "Maria")
                .with(cliente(CLIENTE)).with(csrf()));

        assertThat(html).contains("CEP não localizado").contains("role=\"alert\"")
                .contains("value=\"Maria\"").contains("Salvar endereço");
    }

    @Test
    void falhaNoServicoDeCepPedePreenchimentoManual() throws Exception {
        when(consultaCep.consultar("99999999")).thenThrow(new CepIndisponivelException("fora do ar"));

        String html = html(post("/enderecos/cep").param("cep", "99999-999").param("destinatario", "Maria")
                .with(cliente(CLIENTE)).with(csrf()));

        assertThat(html).contains("Não foi possível consultar o CEP").contains("value=\"Maria\"");
    }

    @Test
    void cepIncompletoNemChamaOServico() throws Exception {
        String html = html(post("/enderecos/cep").param("cep", "0131").with(cliente(CLIENTE)).with(csrf()));

        assertThat(html).contains("Informe um CEP com 8 números");
        verify(consultaCep, never()).consultar(any());
    }

    @Test
    void buscarCepNaEdicaoMantemOFormularioDeEdicao() throws Exception {
        String html = html(post("/enderecos/5/cep").param("cep", "12345678").with(cliente(CLIENTE)).with(csrf()));

        assertThat(html).contains("action=\"/enderecos/5\"").contains("Editar endereço");
        verify(service).buscar(CLIENTE, 5L);
    }
}
