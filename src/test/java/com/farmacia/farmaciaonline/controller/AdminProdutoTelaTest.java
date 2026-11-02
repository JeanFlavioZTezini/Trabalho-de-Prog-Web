package com.farmacia.farmaciaonline.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.farmacia.farmaciaonline.config.SecurityConfig;
import com.farmacia.farmaciaonline.model.ProdutoForm;
import com.farmacia.farmaciaonline.model.enums.Perfil;
import com.farmacia.farmaciaonline.repository.CategoriaRepository;
import com.farmacia.farmaciaonline.security.UsuarioAutenticado;
import com.farmacia.farmaciaonline.service.CategoriaService;
import com.farmacia.farmaciaonline.service.ProdutoException;
import com.farmacia.farmaciaonline.service.ProdutoService;

/** HTML das telas de produto (UC12): estrutura, rótulos e mensagem de erro no elemento certo. */
@WebMvcTest(AdminProdutoController.class)
@Import(SecurityConfig.class)
class AdminProdutoTelaTest {

    @MockBean
    private ProdutoService service;

    @MockBean
    private CategoriaService categoriaService;

    @MockBean
    private CategoriaRepository categoriaRepository;

    @Autowired
    private MockMvc mvc;

    private static RequestPostProcessor admin() {
        return user(new UsuarioAutenticado(1L, "Admin", Perfil.ADMIN, "admin@farma.com", "x"));
    }

    private static int ocorrencias(String html, String regex) {
        Matcher m = Pattern.compile(regex).matcher(html);
        int total = 0;
        while (m.find()) {
            total++;
        }
        return total;
    }

    /** Só o conteúdo da página: o cabeçalho do layout tem os próprios formulários e botões. */
    private static String conteudo(String html) {
        return html.substring(html.indexOf("<main"), html.indexOf("</main>"));
    }

    private String html(String url) throws Exception {
        return conteudo(mvc.perform(get(url).with(admin())).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("lista vazia: um único h1, busca rotulada e 'Nenhum produto encontrado.'")
    void listaVazia() throws Exception {
        when(service.buscar(any(), any(), any())).thenReturn(new PageImpl<>(List.of()));

        String html = html("/admin/produtos");

        assertThat(ocorrencias(html, "<h1[ >]")).isEqualTo(1);
        assertThat(html).contains("<label for=\"q\"").contains("<label for=\"status\"");
        assertThat(html).contains("<p>Nenhum produto encontrado.</p>").doesNotContain("<table");
        assertThat(html).contains("href=\"/admin/produtos/novo\"");
    }

    @Test
    @DisplayName("formulário de novo produto: um único h1, rótulos ligados aos campos e envio multipart")
    void formularioNovo() throws Exception {
        when(service.formDe(any())).thenReturn(new ProdutoForm());

        String html = html("/admin/produtos/novo");

        assertThat(ocorrencias(html, "<h1[ >]")).isEqualTo(1);
        assertThat(html).contains("Novo produto").contains("enctype=\"multipart/form-data\"");
        for (String campo : List.of("nome", "marca", "tarja", "bulaUrl", "novasImagens", "imagemApresentacaoId")) {
            assertThat(html).contains("<label for=\"" + campo + "\"").contains("id=\"" + campo + "\"");
        }
        assertThat(html).contains("Sem fotos: o site usa a imagem padrão.");
    }

    @Test
    @DisplayName("formulário de edição mostra 'Editar produto'")
    void formularioEdicao() throws Exception {
        when(service.formDe(1L)).thenReturn(new ProdutoForm());

        assertThat(html("/admin/produtos/1/editar")).contains("Editar produto").doesNotContain("Novo produto");
    }

    @Test
    @DisplayName("regra de negócio violada volta ao formulário com as mensagens no alerta de erro")
    void erroDeNegocioApareceNoAlerta() throws Exception {
        doThrow(new ProdutoException(List.of("Selecione ao menos uma categoria ativa.", "SKU já cadastrado.")))
                .when(service).salvar(any(), any(), any());

        String html = conteudo(mvc.perform(post("/admin/produtos").with(admin()).with(csrf())
                .param("nome", "Produto").param("marca", "Marca").param("tarja", "SEM_TARJA"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));

        Matcher alerta = Pattern.compile("(?s)<div class=\"alerta alerta--erro\" role=\"alert\">(.*?)</div>")
                .matcher(html);
        assertThat(alerta.find()).isTrue();
        assertThat(alerta.group(1)).contains("Selecione ao menos uma categoria ativa.").contains("SKU já cadastrado.");
    }

    @Test
    @DisplayName("nome em branco: o erro aparece no elemento nome-erro, ligado ao campo, e nada é gravado")
    void validacaoDoFormularioMostraMensagemNoCampo() throws Exception {
        String html = conteudo(mvc.perform(post("/admin/produtos").with(admin()).with(csrf())
                .param("nome", "").param("marca", "Marca").param("tarja", "SEM_TARJA"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));

        assertThat(html).containsPattern("(?s)<p class=\"campo__erro\" role=\"alert\" id=\"nome-erro\">\\s*Informe o nome\\.\\s*</p>");
        assertThat(html).contains("aria-invalid=\"true\"").contains("aria-describedby=\"nome-erro\"");
        assertThat(html).doesNotContain("id=\"marca-erro\"");
        org.mockito.Mockito.verify(service, org.mockito.Mockito.never()).salvar(any(), any(), any());
    }

    @Test
    @DisplayName("lista com produtos: tabela dentro de região rolável com nome acessível")
    void listaComProdutosUsaRegiaoRolavel() throws Exception {
        com.farmacia.farmaciaonline.model.Produto p = new com.farmacia.farmaciaonline.model.Produto();
        p.setNome("Dipirona");
        p.setMarca("Marca");
        when(service.buscar(any(), any(), any())).thenReturn(new PageImpl<>(List.of(p)));

        String html = html("/admin/produtos");

        assertThat(html).contains("class=\"tabela-rolavel\"").contains("role=\"region\"")
                .contains("aria-label=\"Lista de produtos\"").contains("tabindex=\"0\"");
        assertThat(html).contains("<td>Dipirona</td>").doesNotContain("Nenhum produto encontrado.");
    }

    @Test
    @DisplayName("lista de produtos tem o link 'Voltar ao painel' para /admin")
    void listaTemVoltarAoPainel() throws Exception {
        when(service.buscar(any(), any(), any())).thenReturn(new PageImpl<>(List.of()));

        assertThat(html("/admin/produtos")).containsPattern("<a href=\"/admin\">Voltar ao painel</a>");
    }

    @Test
    @DisplayName("apresentações em cartões: um fieldset por linha, rótulos ligados aos campos, interruptor em ativo/ativa e demais checkboxes em label.campo__marcador")
    void apresentacoesEmCartoes() throws Exception {
        ProdutoForm form = new ProdutoForm();
        form.getApresentacoes().add(new ProdutoForm.ApresentacaoForm());
        form.getApresentacoes().add(new ProdutoForm.ApresentacaoForm());
        when(service.formDe(1L)).thenReturn(form);

        String html = html("/admin/produtos/1/editar");

        assertThat(html).doesNotContain("<table");
        assertThat(ocorrencias(html, "<fieldset class=\"apresentacao\"")).isEqualTo(2);
        assertThat(html).containsPattern("<legend>Apresentação 1</legend>").containsPattern("<legend>Apresentação 2</legend>");
        for (int i = 0; i < 2; i++) {
            for (String campo : List.of("descricao", "sku", "ean", "preco", "estoque", "limitePorPedido", "ativa")) {
                String id = "apr-" + campo + "-" + i;
                assertThat(html).containsPattern("<label [^>]*for=\"" + id + "\"").contains("id=\"" + id + "\"")
                        .contains("name=\"apresentacoes[" + i + "]." + campo + "\"");
            }
        }
        assertThat(html).containsPattern("(?s)<label class=\"interruptor\" for=\"apr-ativa-0\">\\s*<input type=\"checkbox\" role=\"switch\"");
        assertThat(html).containsPattern("<label class=\"interruptor\"><input type=\"checkbox\" role=\"switch\"[^>]*id=\"ativo\\d*\"")
                .containsPattern("<label class=\"campo__marcador\"><input type=\"checkbox\"[^>]*id=\"exigeReceita\\d*\"");
    }

    @Test
    @DisplayName("foto: remover é um ícone com nome acessível sobre o checkbox removerImagemIds; 'todas as apresentações' não aparece")
    void fotoComIconeDeRemover() throws Exception {
        com.farmacia.farmaciaonline.model.ImagemProduto img = new com.farmacia.farmaciaonline.model.ImagemProduto();
        img.setId(7L);
        img.setArquivo("/uploads/x.png");
        when(service.formDe(1L)).thenReturn(new ProdutoForm());
        when(service.imagensDe(1L)).thenReturn(List.of(img));

        String html = html("/admin/produtos/1/editar");

        assertThat(html).containsPattern("(?s)<label class=\"remover-foto\">\\s*<input type=\"checkbox\" name=\"removerImagemIds\" value=\"7\"");
        assertThat(html).contains("Remover esta foto").doesNotContain("todas as apresentações");
    }

    @Test
    @DisplayName("lista: editar e excluir são ícones com nome acessível que traz o nome do produto")
    void acoesComoIcones() throws Exception {
        com.farmacia.farmaciaonline.model.Produto p = new com.farmacia.farmaciaonline.model.Produto();
        p.setId(5L);
        p.setNome("Dipirona");
        when(service.buscar(any(), any(), any())).thenReturn(new PageImpl<>(List.of(p)));

        String html = html("/admin/produtos");

        assertThat(html).contains("href=\"/admin/produtos/5/editar\"").contains("action=\"/admin/produtos/5/excluir\"");
        assertThat(html).contains(">Editar Dipirona</span>").contains(">Excluir Dipirona</span>");
        assertThat(html).containsPattern("class=\"acao-icone\"").containsPattern("acao-icone acao-icone--perigo");
    }
}
