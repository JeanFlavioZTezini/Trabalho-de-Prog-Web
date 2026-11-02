package com.farmacia.farmaciaonline.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import com.farmacia.farmaciaonline.TestcontainersConfiguration;
import com.farmacia.farmaciaonline.model.Usuario;
import com.farmacia.farmaciaonline.model.enums.Genero;
import com.farmacia.farmaciaonline.model.enums.Perfil;
import com.farmacia.farmaciaonline.model.enums.StatusUsuario;
import com.farmacia.farmaciaonline.repository.UsuarioRepository;

import jakarta.persistence.EntityManager;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class ContaExclusaoIntegracaoTest {

    private static final String SENHA = "Farma@123";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UsuarioRepository usuarios;

    @Autowired
    private PasswordEncoder encoder;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManager em;

    private Usuario cliente;
    private String email;
    private String cpf;

    @BeforeEach
    void criarCliente() {
        cpf = cpfValido();
        email = "exclusao" + cpf + "@teste.com";
        cliente = usuarios.saveAndFlush(Usuario.builder()
                .nome("Cliente Exclusão")
                .cpf(cpf)
                .email(email)
                .celular("67999998888")
                .genero(Genero.FEMININO)
                .dataNascimento(LocalDate.of(1990, 5, 10))
                .senhaHash(encoder.encode(SENHA))
                .perfil(Perfil.CLIENTE)
                .status(StatusUsuario.ATIVA)
                .termosVersao("1.0")
                .termosAceitosEm(Instant.now())
                .emailConfirmadoEm(Instant.now())
                .celularConfirmadoEm(Instant.now())
                .build());
    }

    // ---------- acesso ----------

    @Test
    @DisplayName("visitante vai para o login no GET e no POST")
    void visitante() throws Exception {
        mvc.perform(get("/conta/excluir")).andExpect(redirectedUrlPattern("**/login*"));
        mvc.perform(post("/conta/excluir").with(csrf()).params(form(Map.of())))
                .andExpect(redirectedUrlPattern("**/login*"));
        assertThat(statusDe(cliente.getId())).isEqualTo("ATIVA");
    }

    @Test
    @DisplayName("administrador recebe 403 e a conta dele não muda")
    void administrador() throws Exception {
        MockHttpSession admin = entrar("admin@farma.com", SENHA);

        mvc.perform(get("/conta/excluir").session(admin)).andExpect(status().isForbidden());
        mvc.perform(post("/conta/excluir").session(admin).with(csrf()).params(form(Map.of())))
                .andExpect(status().isForbidden());

        assertThat(jdbc.queryForObject("select status from usuario where email = 'admin@farma.com'", String.class))
                .isEqualTo("ATIVA");
    }

    @Test
    @DisplayName("sem CSRF o POST é recusado")
    void semCsrf() throws Exception {
        MockHttpSession sessao = entrar(email, SENHA);

        mvc.perform(post("/conta/excluir").session(sessao).params(form(Map.of())))
                .andExpect(status().isForbidden());
        assertThat(statusDe(cliente.getId())).isEqualTo("ATIVA");
    }

    @Test
    @DisplayName("a tela tem um único h1, rótulos ligados aos campos e explica o que é apagado; Minha conta tem o link")
    void tela() throws Exception {
        MockHttpSession sessao = entrar(email, SENHA);

        String html = pagina("/conta/excluir", sessao);
        assertThat(html.split("<h1", -1)).hasSize(2);
        assertThat(html).contains("for=\"senhaAtual\"", "autocomplete=\"current-password\"", "for=\"confirmacao\"",
                "aria-describedby=\"confirmacao-ajuda\"", "Pedidos já feitos ficam guardados");
        assertThat(pagina("/conta", sessao)).contains("href=\"/conta/excluir\"");
    }

    // ---------- caminho feliz ----------

    @Test
    @DisplayName("excluir anonimiza a conta, apaga os dados acessórios e mantém pedidos, pagamento e histórico")
    void excluiEAnonimiza() throws Exception {
        Long id = cliente.getId();
        Long cartao = inserirDadosAcessorios(id);
        Long entregue = inserirPedido(id, "ENTREGUE");
        Long cancelado = inserirPedido(id, "CANCELADO");
        jdbc.update("insert into pagamento (pedido_id, metodo, status, valor, cartao_bandeira, cartao_ultimos4, cartao_salvo_id) "
                + "values (?, 'CARTAO_CREDITO', 'APROVADO', 25.00, 'VISA', '1111', ?)", entregue, cartao);
        MockHttpSession sessao = entrar(email, SENHA);

        mvc.perform(post("/conta/excluir").session(sessao).with(csrf()).params(form(Map.of())))
                .andExpect(redirectedUrl("/login"))
                .andExpect(flash().attribute("contaExcluida", true));
        em.flush();
        em.clear();

        Map<String, Object> linha = jdbc.queryForMap("select * from usuario where id = ?", id);
        assertThat(linha.get("status")).isEqualTo("EXCLUIDA");
        assertThat(linha.get("excluido_em")).isNotNull();
        assertThat(linha.get("nome")).isEqualTo("Conta excluída");
        assertThat(linha.get("email")).isEqualTo("excluida-" + id + "@excluida.invalid");
        assertThat(linha).containsEntry("cpf", null).containsEntry("celular", null).containsEntry("genero", null)
                .containsEntry("senha_hash", null).containsEntry("email_confirmado_em", null)
                .containsEntry("celular_confirmado_em", null);
        assertThat(jdbc.queryForObject("select count(*) from usuario where cpf = ? or lower(email) = ?", Integer.class,
                cpf, email)).isZero();

        for (String tabela : new String[] { "endereco", "cartao_salvo", "carrinho", "identidade_social",
                "codigo_verificacao" }) {
            assertThat(contar(tabela, id)).as(tabela).isZero();
        }
        assertThat(contar("pedido", id)).isEqualTo(2);
        Map<String, Object> pedido = jdbc.queryForMap("select status, total, retirada_nome from pedido where id = ?", entregue);
        assertThat(pedido.get("status")).isEqualTo("ENTREGUE");
        assertThat((BigDecimal) pedido.get("total")).isEqualByComparingTo("25.00");
        assertThat(pedido.get("retirada_nome")).isEqualTo("Loja Teste");
        assertThat(jdbc.queryForObject("select count(*) from item_pedido where pedido_id in (?, ?)", Integer.class,
                entregue, cancelado)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from historico_status_pedido where pedido_id = ?", Integer.class,
                entregue)).isEqualTo(1);
        Map<String, Object> pagamento = jdbc.queryForMap("select cartao_ultimos4, cartao_salvo_id from pagamento where pedido_id = ?",
                entregue);
        assertThat(pagamento.get("cartao_ultimos4")).isEqualTo("1111");
        assertThat(pagamento.get("cartao_salvo_id")).isNull();

        Map<String, Object> log = jdbc.queryForMap(
                "select usuario_id, alvo_tipo, alvo_id, detalhes from log_auditoria where acao = 'CONTA_EXCLUIR' and alvo_id = ?", id);
        assertThat(log.get("usuario_id")).isEqualTo(id);
        assertThat(log.get("detalhes")).isNull();
    }

    @Test
    @DisplayName("depois de excluir: login negado com a mensagem genérica e a sessão atual deixa de valer")
    void loginNegadoDepois() throws Exception {
        MockHttpSession sessao = entrar(email, SENHA);

        mvc.perform(post("/conta/excluir").session(sessao).with(csrf()).params(form(Map.of())))
                .andExpect(redirectedUrl("/login"));

        mvc.perform(post("/login").with(csrf()).param("identificador", email).param("senha", SENHA))
                .andExpect(redirectedUrl("/login?erro=credenciais"));
        mvc.perform(post("/login").with(csrf()).param("identificador", cpf).param("senha", SENHA))
                .andExpect(redirectedUrl("/login?erro=credenciais"));
        mvc.perform(get("/conta").session(sessao)).andExpect(redirectedUrlPattern("**/login*"));
    }

    @Test
    @DisplayName("excluir encerra as sessões abertas em outros navegadores")
    void encerraOutrasSessoes() throws Exception {
        MockHttpSession atual = entrar(email, SENHA);
        MockHttpSession outroNavegador = entrar(email, SENHA);

        mvc.perform(post("/conta/excluir").session(atual).with(csrf()).params(form(Map.of())))
                .andExpect(redirectedUrl("/login"));

        mvc.perform(get("/conta").session(outroNavegador)).andExpect(redirectedUrl("/login?expirada"));
    }

    @Test
    @DisplayName("o login mostra o aviso de conta excluída")
    void avisoNoLogin() throws Exception {
        String html = mvc.perform(get("/login").flashAttr("contaExcluida", true))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(html).contains("Sua conta foi excluída.");
    }

    @Test
    @DisplayName("e-mail e CPF da conta excluída podem ser usados numa conta nova")
    void emailECpfReutilizaveis() throws Exception {
        MockHttpSession sessao = entrar(email, SENHA);
        mvc.perform(post("/conta/excluir").session(sessao).with(csrf()).params(form(Map.of())))
                .andExpect(redirectedUrl("/login"));
        em.flush();

        Usuario nova = usuarios.saveAndFlush(Usuario.builder()
                .nome("Conta Nova")
                .cpf(cpf)
                .email(email)
                .dataNascimento(LocalDate.of(1991, 1, 1))
                .senhaHash(encoder.encode(SENHA))
                .status(StatusUsuario.ATIVA)
                .termosVersao("1.0")
                .termosAceitosEm(Instant.now())
                .build());

        assertThat(nova.getId()).isNotEqualTo(cliente.getId());
    }

    @Test
    @DisplayName("id e status forjados no formulário são ignorados: só a conta da sessão é excluída")
    void camposForjadosIgnorados() throws Exception {
        Long outro = usuarios.findByEmailIgnoreCase("cliente@farma.com").orElseThrow().getId();
        MockHttpSession sessao = entrar(email, SENHA);

        mvc.perform(post("/conta/excluir").session(sessao).with(csrf())
                        .params(form(Map.of("id", outro.toString(), "usuarioId", outro.toString(), "status", "ATIVA"))))
                .andExpect(redirectedUrl("/login"));
        em.flush();

        assertThat(statusDe(cliente.getId())).isEqualTo("EXCLUIDA");
        assertThat(statusDe(outro)).isEqualTo("ATIVA");
    }

    // ---------- recusas ----------

    @Test
    @DisplayName("pedido em andamento bloqueia a exclusão, para cada status em andamento")
    void pedidoEmAndamentoBloqueia() throws Exception {
        MockHttpSession sessao = entrar(email, SENHA);
        inserirDadosAcessorios(cliente.getId());

        for (String andamento : new String[] { "AGUARDANDO_PAGAMENTO", "PAGAMENTO_APROVADO", "EM_SEPARACAO", "ENVIADO",
                "PRONTO_PARA_RETIRADA" }) {
            Long pedido = inserirPedido(cliente.getId(), andamento);

            String tela = pagina("/conta/excluir", sessao);
            assertThat(tela).as(andamento).contains("Você tem pedido em andamento").doesNotContain("id=\"senhaAtual\"");

            String html = mvc.perform(post("/conta/excluir").session(sessao).with(csrf()).params(form(Map.of())))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
            assertThat(html).as(andamento).contains("role=\"alert\"", "entregues ou cancelados");
            em.flush();
            assertThat(statusDe(cliente.getId())).as(andamento).isEqualTo("ATIVA");
            assertThat(contar("endereco", cliente.getId())).as(andamento).isEqualTo(1);

            jdbc.update("update pedido set status = 'CANCELADO' where id = ?", pedido);
        }

        mvc.perform(post("/conta/excluir").session(sessao).with(csrf()).params(form(Map.of())))
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @DisplayName("senha errada recusa, mostra o erro no campo e conta a tentativa")
    void senhaErrada() throws Exception {
        MockHttpSession sessao = entrar(email, SENHA);

        String html = mvc.perform(post("/conta/excluir").session(sessao).with(csrf())
                        .params(form(Map.of("senhaAtual", "Errada123"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        em.flush();

        assertThat(html).contains("id=\"senhaAtual-erro\"", "aria-describedby=\"senhaAtual-erro\"");
        assertThat(statusDe(cliente.getId())).isEqualTo("ATIVA");
        assertThat(jdbc.queryForObject("select tentativas_login_falhas from usuario where id = ?", Integer.class,
                cliente.getId())).isEqualTo(1);
    }

    @Test
    @DisplayName("sem digitar EXCLUIR exatamente (minúsculas ou vazio) a conta não é excluída")
    void semPalavraDeConfirmacao() throws Exception {
        MockHttpSession sessao = entrar(email, SENHA);

        for (String digitado : new String[] { "excluir", "", "SIM" }) {
            String html = mvc.perform(post("/conta/excluir").session(sessao).with(csrf())
                            .params(form(Map.of("confirmacao", digitado))))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
            assertThat(html).as(digitado).contains("id=\"confirmacao-erro\"",
                    "aria-describedby=\"confirmacao-ajuda confirmacao-erro\"");
        }
        em.flush();
        assertThat(statusDe(cliente.getId())).isEqualTo("ATIVA");
    }

    @Test
    @DisplayName("conta sem senha (só login social) é orientada a definir senha antes")
    void contaSemSenha() throws Exception {
        MockHttpSession sessao = entrar(email, SENHA);
        jdbc.update("update usuario set senha_hash = null where id = ?", cliente.getId());
        em.clear();

        assertThat(pagina("/conta/excluir", sessao)).contains("href=\"/recuperar-senha\"").doesNotContain("id=\"senhaAtual\"");
        String html = mvc.perform(post("/conta/excluir").session(sessao).with(csrf()).params(form(Map.of())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(html).contains("Defina uma senha antes de excluir");
        assertThat(statusDe(cliente.getId())).isEqualTo("ATIVA");
    }

    @Test
    @DisplayName("conta bloqueada por tentativas não é excluída nem com a senha certa")
    void contaBloqueada() throws Exception {
        MockHttpSession sessao = entrar(email, SENHA);
        jdbc.update("update usuario set bloqueado_ate = ? where id = ?",
                Timestamp.from(Instant.now().plus(10, ChronoUnit.MINUTES)), cliente.getId());
        em.clear();

        String html = mvc.perform(post("/conta/excluir").session(sessao).with(csrf()).params(form(Map.of())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(html).contains("Muitas tentativas");
        assertThat(statusDe(cliente.getId())).isEqualTo("ATIVA");
    }

    // ---------- apoio ----------

    /** Monta o formulário válido e troca só as chaves pedidas (armadilha do .param que acrescenta valor). */
    private static MultiValueMap<String, String> form(Map<String, String> trocas) {
        Map<String, String> campos = new HashMap<>(Map.of("senhaAtual", SENHA, "confirmacao", "EXCLUIR"));
        campos.putAll(trocas);
        LinkedMultiValueMap<String, String> mapa = new LinkedMultiValueMap<>();
        campos.forEach(mapa::add);
        return mapa;
    }

    private MockHttpSession entrar(String identificador, String senha) throws Exception {
        MockHttpServletRequestBuilder login = post("/login").with(csrf())
                .param("identificador", identificador).param("senha", senha);
        return (MockHttpSession) mvc.perform(login).andExpect(status().is3xxRedirection())
                .andReturn().getRequest().getSession(false);
    }

    private String pagina(String rota, MockHttpSession sessao) throws Exception {
        return mvc.perform(get(rota).session(sessao)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private String statusDe(Long id) {
        return jdbc.queryForObject("select status from usuario where id = ?", String.class, id);
    }

    private int contar(String tabela, Long usuarioId) {
        return jdbc.queryForObject("select count(*) from " + tabela + " where usuario_id = ?", Integer.class, usuarioId);
    }

    /** Endereço, cartão salvo, carrinho com item, vínculo social e código. Devolve o id do cartão. */
    private Long inserirDadosAcessorios(Long id) {
        jdbc.update("insert into endereco (usuario_id, destinatario, cep, logradouro, numero, bairro, cidade, uf) "
                + "values (?, 'Cliente', '79002000', 'Rua A', '10', 'Centro', 'Campo Grande', 'MS')", id);
        Long cartao = jdbc.queryForObject("insert into cartao_salvo (usuario_id, token_gateway, bandeira, ultimos4, "
                + "validade_mes, validade_ano, nome_impresso) values (?, ?, 'VISA', '1111', 12, 2030, 'CLIENTE') returning id",
                Long.class, id, "tok-" + id);
        Long carrinho = jdbc.queryForObject("insert into carrinho (usuario_id) values (?) returning id", Long.class, id);
        jdbc.update("insert into item_carrinho (carrinho_id, apresentacao_id, quantidade, preco_visto) "
                + "values (?, (select min(id) from apresentacao), 1, 10.00)", carrinho);
        jdbc.update("insert into identidade_social (usuario_id, provedor, provedor_usuario_id) values (?, 'GOOGLE', ?)",
                id, "sub-" + id);
        jdbc.update("insert into codigo_verificacao (usuario_id, finalidade, canal, destino, codigo_hash, expira_em) "
                + "values (?, 'CADASTRO', 'EMAIL', ?, 'hash', now() + interval '10 minutes')", id, email);
        return cartao;
    }

    private Long inserirPedido(Long id, String status) {
        Long pedido = jdbc.queryForObject("insert into pedido (usuario_id, status, tipo_entrega, loja_retirada_id, "
                + "retirada_nome, subtotal, frete, total) values (?, ?, 'RETIRADA', (select min(id) from loja), "
                + "'Loja Teste', 25.00, 0, 25.00) returning id", Long.class, id, status);
        jdbc.update("insert into item_pedido (pedido_id, apresentacao_id, nome_produto, descricao_apresentacao, sku, "
                + "preco_unitario, quantidade) values (?, (select min(id) from apresentacao), 'Produto', 'Caixa', 'SKU-1', 25.00, 1)",
                pedido);
        jdbc.update("insert into historico_status_pedido (pedido_id, status) values (?, ?)", pedido, status);
        return pedido;
    }

    /** CPF com dígitos verificadores válidos, aleatório para não colidir com o seed nem com outros testes. */
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
