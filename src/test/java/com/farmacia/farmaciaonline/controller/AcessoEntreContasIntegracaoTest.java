package com.farmacia.farmaciaonline.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.farmacia.farmaciaonline.TestcontainersConfiguration;

/**
 * F-19, cenários 8 e 9 com login real e banco: cliente não executa ação administrativa (403 e nada muda) e o logout não
 * apaga o que a conta já tem (endereços e pedidos voltam no próximo login). Os 404 entre clientes já estão em
 * {@code PedidosClienteIntegracaoTest}, {@code EnderecoServiceIntegracaoTest} e {@code CheckoutFluxoWebIntegracaoTest}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class AcessoEntreContasIntegracaoTest {

    private static final String CLIENTE = "cliente@farma.com";
    private static final String SENHA = "Farma@123";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    private MockHttpSession entrar() throws Exception {
        return (MockHttpSession) mvc.perform(post("/login").with(csrf())
                        .param("identificador", CLIENTE).param("senha", SENHA))
                .andExpect(redirectedUrl("/"))
                .andReturn().getRequest().getSession(false);
    }

    private Long clienteId() {
        return jdbc.queryForObject("select id from usuario where email = ?", Long.class, CLIENTE);
    }

    private Long pedidoDoCliente() {
        return jdbc.queryForObject("insert into pedido (usuario_id, status, tipo_entrega, loja_retirada_id, subtotal, "
                + "frete, total) values (?, 'PAGAMENTO_APROVADO', 'RETIRADA', (select min(id) from loja), 10.00, 0, 10.00) "
                + "returning id", Long.class, clienteId());
    }

    @Test
    @DisplayName("cenário 8: cliente logado recebe 403 em POST de produto, categoria e status de pedido, e nada muda")
    void clienteNaoExecutaAcaoAdministrativa() throws Exception {
        MockHttpSession sessao = entrar();
        Long pedido = pedidoDoCliente();
        int produtos = jdbc.queryForObject("select count(*) from produto", Integer.class);
        int categorias = jdbc.queryForObject("select count(*) from categoria", Integer.class);

        mvc.perform(post("/admin/produtos").session(sessao).with(csrf()).param("nome", "Produto Invasor"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/admin/categorias").session(sessao).with(csrf()).param("nome", "Categoria Invasora"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/admin/pedidos/" + pedido + "/status").session(sessao).with(csrf())
                        .param("versao", "0").param("novoStatus", "ENTREGUE"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/admin/pedidos/" + pedido + "/conferencia-receita").session(sessao).with(csrf())
                        .param("versao", "0").param("confirmado", "true"))
                .andExpect(status().isForbidden());

        assertThat(jdbc.queryForObject("select count(*) from produto", Integer.class)).isEqualTo(produtos);
        assertThat(jdbc.queryForObject("select count(*) from categoria", Integer.class)).isEqualTo(categorias);
        assertThat(jdbc.queryForObject("select status from pedido where id = ?", String.class, pedido))
                .isEqualTo("PAGAMENTO_APROVADO");
    }

    @Test
    @DisplayName("cenário 9: depois do logout a área protegida fecha, e o novo login traz de volta endereços e pedidos")
    void logoutPreservaDadosDaConta() throws Exception {
        MockHttpSession sessao = entrar();
        mvc.perform(post("/enderecos").session(sessao).with(csrf())
                        .param("destinatario", "Cliente").param("cep", "79002-000").param("logradouro", "Rua Preservada")
                        .param("numero", "7").param("bairro", "Centro").param("cidade", "Campo Grande").param("uf", "MS"))
                .andExpect(status().is3xxRedirection());
        Long pedido = pedidoDoCliente();

        mvc.perform(post("/logout").session(sessao).with(csrf())).andExpect(redirectedUrl("/login?logout"));
        mvc.perform(get("/enderecos").session(sessao)).andExpect(redirectedUrlPattern("**/login*"));
        mvc.perform(get("/pedidos/" + pedido).session(sessao)).andExpect(redirectedUrlPattern("**/login*"));

        MockHttpSession deNovo = entrar();
        assertThat(pagina("/enderecos", deNovo)).contains("Rua Preservada");
        assertThat(pagina("/pedidos", deNovo)).contains("/pedidos/" + pedido);
    }

    private String pagina(String url, MockHttpSession sessao) throws Exception {
        return mvc.perform(get(url).session(sessao)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }
}
