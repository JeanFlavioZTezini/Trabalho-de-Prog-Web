package com.farmacia.farmaciaonline.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.farmacia.farmaciaonline.model.Apresentacao;
import com.farmacia.farmaciaonline.model.Pedido;
import com.farmacia.farmaciaonline.model.Usuario;
import com.farmacia.farmaciaonline.model.enums.StatusPedido;
import com.farmacia.farmaciaonline.model.enums.StatusUsuario;
import com.farmacia.farmaciaonline.security.CarrinhoCookie;

import jakarta.servlet.http.Cookie;

/**
 * F-19: os cenários de aceite da entrega encadeados pelas telas, como o navegador faria (login real pelo formulário,
 * sessão e cookie de verdade, banco de verdade). Os testes de cada issue cobrem as regras; aqui o assunto é as peças
 * funcionarem juntas.
 */
class PercursoCompletoIntegracaoTest extends CheckoutTesteBase {

    private static final Pattern SEIS_DIGITOS = Pattern.compile("\\b(\\d{6})\\b");
    private static final String SENHA = "Senha123";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder encoder;

    @Test
    @DisplayName("cenário 2: cadastro, código na caixa demo, confirmação, login e compra concluída")
    void cadastroAteCompra() throws Exception {
        String cpf = cpfValido();
        String email = "percurso" + cpf + "@teste.com";
        Apresentacao a = novaApresentacao("12.00", 4);

        MockHttpSession cadastro = (MockHttpSession) mvc.perform(post("/cadastro").with(csrf())
                        .param("nome", "Pessoa Percurso").param("cpf", cpf).param("dataNascimento", "1992-08-10")
                        .param("genero", "FEMININO").param("celular", "(67) 99999-7777").param("email", email)
                        .param("senha", SENHA).param("confirmarSenha", SENHA).param("aceiteTermos", "true"))
                .andExpect(redirectedUrl("/confirmar-conta"))
                .andReturn().getRequest().getSession();
        assertThat(usuarioRepository.findByEmailIgnoreCase(email).orElseThrow().getStatus())
                .isEqualTo(StatusUsuario.PENDENTE);

        mvc.perform(post("/confirmar-conta/enviar").with(csrf()).session(cadastro).param("canal", "EMAIL"))
                .andExpect(redirectedUrl("/confirmar-conta"));
        mvc.perform(post("/confirmar-conta").with(csrf()).session(cadastro).param("codigo", codigoEnviadoPara(email)))
                .andExpect(redirectedUrl("/login?confirmada"));

        MockHttpSession sessao = entrar(email, SENHA, null);
        mvc.perform(post("/carrinho/itens").session(sessao).with(csrf())
                        .param("apresentacaoId", a.getId().toString()).param("quantidade", "2"))
                .andExpect(status().is3xxRedirection());

        Long id = confirmar(sessao, "tipoEntrega=RETIRADA&metodo=CARTAO_CREDITO&lojaId=1",
                "numeroCartao", CARTAO_APROVADO, "validadeCartao", "12/40", "cvv", "123", "nomeImpresso", "PESSOA PERCURSO");

        Pedido pedido = pedidoRepository.findById(id).orElseThrow();
        assertThat(pedido.getStatus()).isEqualTo(StatusPedido.PAGAMENTO_APROVADO);
        assertThat(pedido.getTotal()).isEqualByComparingTo("24.00");
        assertThat(estoqueDe(a)).isEqualTo(2);
        assertThat(pagina(sessao, "/pedidos")).contains("/pedidos/" + id);
    }

    @Test
    @DisplayName("cenários 1 e 3: visitante adiciona, o login mescla, endereço pela tela, entrega com frete e estoque certo")
    void visitanteAteEntrega() throws Exception {
        Usuario cliente = clienteComSenha();
        Apresentacao a = novaApresentacao("10.50", 5);

        MockHttpServletResponse adicionou = mvc.perform(post("/carrinho/itens").with(csrf())
                        .param("apresentacaoId", a.getId().toString()).param("quantidade", "2"))
                .andExpect(status().is3xxRedirection())
                .andReturn().getResponse();
        Cookie carrinhoDoVisitante = adicionou.getCookie(CarrinhoCookie.NOME);
        assertThat(carrinhoDoVisitante).isNotNull();

        MockHttpSession sessao = entrar(cliente.getEmail(), SENHA, carrinhoDoVisitante);
        assertThat(itensNoCarrinho(cliente)).isEqualTo(1);

        mvc.perform(post("/enderecos").session(sessao).with(csrf())
                        .param("destinatario", "Cliente").param("cep", "79002-000").param("logradouro", "Rua da Entrega")
                        .param("numero", "50").param("bairro", "Centro").param("cidade", "Campo Grande").param("uf", "MS"))
                .andExpect(status().is3xxRedirection());
        Long enderecoId = jdbc.queryForObject("select id from endereco where usuario_id = ?", Long.class, cliente.getId());

        String query = "tipoEntrega=ENTREGA&metodo=PIX&enderecoId=" + enderecoId;
        assertThat(pagina(sessao, "/checkout/resumo?" + query)).contains("R$ 21,00", "R$ 9,90", "R$ 30,90");
        Long id = confirmar(sessao, query);
        assertThat(estoqueDe(a)).isEqualTo(3);

        mvc.perform(post("/checkout/pedidos/" + id + "/demo/aprovar").session(sessao).with(csrf()))
                .andExpect(redirectedUrl("/checkout/pedidos/" + id));

        Pedido pedido = pedidoRepository.findById(id).orElseThrow();
        assertThat(pedido.getStatus()).isEqualTo(StatusPedido.PAGAMENTO_APROVADO);
        assertThat(pedido.getTotal()).isEqualByComparingTo("30.90");
        assertThat(estoqueDe(a)).isEqualTo(3);
        assertThat(itensNoCarrinho(cliente)).isZero();
    }

    @Test
    @DisplayName("cenários 4 e 7: controlado só por retirada; o admin separa, sinaliza pronto e só entrega após a "
            + "conferência; o cliente vê o mesmo status")
    void controladoAteEntregue() throws Exception {
        Usuario cliente = clienteComSenha();
        Apresentacao controlado = novaApresentacao("18.50", 3, true);
        MockHttpSession sessao = entrar(cliente.getEmail(), SENHA, null);
        mvc.perform(post("/carrinho/itens").session(sessao).with(csrf())
                        .param("apresentacaoId", controlado.getId().toString()))
                .andExpect(status().is3xxRedirection());

        assertThat(pagina(sessao, "/checkout")).doesNotContain("value=\"ENTREGA\"");
        Long id = confirmar(sessao, "tipoEntrega=RETIRADA&metodo=PIX&lojaId=1");
        mvc.perform(post("/checkout/pedidos/" + id + "/demo/aprovar").session(sessao).with(csrf()))
                .andExpect(redirectedUrl("/checkout/pedidos/" + id));

        MockHttpSession admin = entrar("admin@farma.com", "Farma@123", null);
        mudarStatus(admin, id, "EM_SEPARACAO");
        mudarStatus(admin, id, "PRONTO_PARA_RETIRADA");
        assertThat(pagina(sessao, "/pedidos/" + id)).contains("Pronto para retirada");

        mudarStatus(admin, id, "ENTREGUE");
        assertThat(statusDe(id)).isEqualTo(StatusPedido.PRONTO_PARA_RETIRADA);

        mvc.perform(post("/admin/pedidos/" + id + "/conferencia-receita").session(admin).with(csrf())
                        .param("versao", versaoDe(id)).param("confirmado", "true"))
                .andExpect(status().is3xxRedirection());
        mudarStatus(admin, id, "ENTREGUE");
        assertThat(statusDe(id)).isEqualTo(StatusPedido.ENTREGUE);

        String visaoDoCliente = pagina(sessao, "/pedidos/" + id);
        assertThat(visaoDoCliente).contains("Entregue", "Em separação", "Pronto para retirada");
        assertThat(pagina(admin, "/admin/pedidos/" + id)).contains("Entregue");
        assertThat(estoqueDe(controlado)).isEqualTo(2);
    }

    // ---------- apoio ----------

    private Usuario clienteComSenha() {
        Usuario cliente = novoCliente();
        cliente.setSenhaHash(encoder.encode(SENHA));
        return usuarioRepository.saveAndFlush(cliente);
    }

    private MockHttpSession entrar(String identificador, String senha, Cookie carrinho) throws Exception {
        MockHttpServletRequestBuilder login = post("/login").with(csrf())
                .param("identificador", identificador).param("senha", senha);
        if (carrinho != null) {
            login.cookie(carrinho);
        }
        MvcResult resultado = mvc.perform(login).andExpect(status().is3xxRedirection()).andReturn();
        assertThat(resultado.getResponse().getRedirectedUrl()).doesNotContain("erro");
        return (MockHttpSession) resultado.getRequest().getSession(false);
    }

    private String pagina(MockHttpSession sessao, String url) throws Exception {
        return mvc.perform(get(url).session(sessao)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8).replace(' ', ' ');
    }

    /** Abre o resumo como o navegador e envia o formulário que ele trouxe; devolve o número do pedido. */
    private Long confirmar(MockHttpSession sessao, String query, String... camposExtras) throws Exception {
        String html = pagina(sessao, "/checkout/resumo?" + query);
        MockHttpServletRequestBuilder requisicao = post("/checkout/confirmar").session(sessao).with(csrf())
                .param("conferencia", campoOculto(html, "conferencia"))
                .param("chave", campoOculto(html, "chave"));
        for (String par : query.split("&")) {
            requisicao.param(par.split("=")[0], par.split("=")[1]);
        }
        for (int i = 0; i < camposExtras.length; i += 2) {
            requisicao.param(camposExtras[i], camposExtras[i + 1]);
        }
        String destino = mvc.perform(requisicao).andReturn().getResponse().getRedirectedUrl();
        assertThat(destino).startsWith("/checkout/pedidos/");
        return Long.valueOf(destino.substring("/checkout/pedidos/".length()));
    }

    private void mudarStatus(MockHttpSession admin, Long id, String novo) throws Exception {
        mvc.perform(post("/admin/pedidos/" + id + "/status").session(admin).with(csrf())
                        .param("versao", versaoDe(id)).param("novoStatus", novo))
                .andExpect(status().is3xxRedirection());
    }

    private String versaoDe(Long id) {
        return String.valueOf(jdbc.queryForObject("select versao from pedido where id = ?", Long.class, id));
    }

    private StatusPedido statusDe(Long id) {
        return StatusPedido.valueOf(jdbc.queryForObject("select status from pedido where id = ?", String.class, id));
    }

    private static String campoOculto(String html, String nome) {
        Matcher achado = Pattern.compile("name=\"" + nome + "\" value=\"([^\"]+)\"").matcher(html);
        assertThat(achado.find()).as("campo " + nome).isTrue();
        return achado.group(1);
    }

    private String codigoEnviadoPara(String destino) {
        String texto = envio.mensagens().stream().filter(m -> m.destino().equals(destino))
                .reduce((primeira, ultima) -> ultima).orElseThrow().texto();
        Matcher encontrado = SEIS_DIGITOS.matcher(texto);
        assertThat(encontrado.find()).isTrue();
        return encontrado.group(1);
    }

    /** CPF com dígitos verificadores válidos e aleatório: o banco deste contexto não é limpo entre testes. */
    private static String cpfValido() {
        int[] d = new int[11];
        do {
            for (int i = 0; i < 9; i++) {
                d[i] = ThreadLocalRandom.current().nextInt(10);
            }
        } while (d[0] == d[1] && d[1] == d[2]);
        for (int t = 9; t < 11; t++) {
            int soma = 0;
            for (int i = 0; i < t; i++) {
                soma += d[i] * (t + 1 - i);
            }
            int resto = (soma * 10) % 11;
            d[t] = resto == 10 ? 0 : resto;
        }
        StringBuilder cpf = new StringBuilder();
        for (int x : d) {
            cpf.append(x);
        }
        return cpf.toString();
    }
}
