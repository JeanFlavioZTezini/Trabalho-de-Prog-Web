package com.farmacia.farmaciaonline.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;

import com.farmacia.farmaciaonline.TestcontainersConfiguration;
import com.farmacia.farmaciaonline.model.ProdutoForm;
import com.farmacia.farmaciaonline.model.ProdutoForm.ApresentacaoForm;
import com.farmacia.farmaciaonline.repository.ApresentacaoRepository;
import com.farmacia.farmaciaonline.repository.ImagemProdutoRepository;

/**
 * UC12, regras de posse e de unicidade que o formulário do admin não pode furar (até aqui sem teste). Seed (V2): produto 1
 * (Novalgina, apresentações 1 a 3, foto 1) e produto 2 (apresentação 4, SKU PRA-DIP500-20, EAN 7891000000045, foto 2).
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class ProdutoServiceRegrasIntegracaoTest {

    private static final long ADMIN = 1L;
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};

    @Autowired
    private ProdutoService service;
    @Autowired
    private ApresentacaoRepository apresentacoes;
    @Autowired
    private ImagemProdutoRepository imagens;

    private static ApresentacaoForm linha(String sku, String ean) {
        ApresentacaoForm a = new ApresentacaoForm();
        a.setDescricao("Nova apresentação");
        a.setSku(sku);
        a.setEan(ean);
        a.setPreco(new BigDecimal("9.90"));
        a.setEstoque(5);
        return a;
    }

    private ProdutoException recusa(ProdutoForm form) {
        try {
            service.salvar(1L, form, ADMIN);
        } catch (ProdutoException e) {
            return e;
        }
        throw new AssertionError("o salvamento deveria ter sido recusado");
    }

    @Test
    @DisplayName("remover a foto de outro produto é recusado e a foto continua lá")
    void naoRemoveFotoDeOutroProduto() {
        ProdutoForm form = service.formDe(1L);
        form.getRemoverImagemIds().add(2L);

        assertThat(recusa(form).getErros()).contains("Imagem a remover não pertence a este produto.");
        assertThat(imagens.findById(2L)).isPresent();
    }

    @Test
    @DisplayName("linha com o id de uma apresentação de outro produto é recusada e não muda de dono")
    void naoEditaApresentacaoDeOutroProduto() {
        ProdutoForm form = service.formDe(1L);
        ApresentacaoForm alheia = linha("PRA-DIP500-20", "7891000000045");
        alheia.setId(4L);
        alheia.setVersao(apresentacoes.findById(4L).orElseThrow().getVersao());
        form.getApresentacoes().add(alheia);

        assertThat(recusa(form).getErros()).anyMatch(e -> e.endsWith("não pertence a este produto."));
        assertThat(apresentacoes.findById(4L).orElseThrow().getProduto().getId()).isEqualTo(2L);
    }

    @Test
    @DisplayName("foto nova vinculada à apresentação de outro produto é recusada")
    void fotoNaoVinculaApresentacaoDeOutroProduto() {
        ProdutoForm form = service.formDe(1L);
        form.setImagemApresentacaoId(4L);
        form.getNovasImagens().add(new MockMultipartFile("novasImagens", "foto.png", "image/png", PNG));

        assertThat(recusa(form).getErros()).contains("A apresentação escolhida para a imagem não pertence a este produto.");
    }

    @Test
    @DisplayName("SKU e EAN repetidos no mesmo formulário são recusados")
    void skuEEanRepetidosNoFormulario() {
        ProdutoForm form = service.formDe(1L);
        form.getApresentacoes().add(linha("NOVO-SKU-1", "7899999000011"));
        form.getApresentacoes().add(linha("NOVO-SKU-1", "7899999000011"));

        assertThat(recusa(form).getErros()).contains("Apresentação 5: SKU repetido no formulário.",
                "Apresentação 5: EAN repetido no formulário.");
    }

    @Test
    @DisplayName("SKU e EAN que já são de outra apresentação são recusados")
    void skuEEanJaCadastrados() {
        ProdutoForm form = service.formDe(1L);
        form.getApresentacoes().add(linha("PRA-DIP500-20", "7891000000045"));

        assertThat(recusa(form).getErros()).contains("Apresentação 4: SKU \"PRA-DIP500-20\" já cadastrado.",
                "Apresentação 4: EAN \"7891000000045\" já cadastrado.");
    }

    @Test
    @DisplayName("preço acima do máximo da coluna e limite por pedido zero são recusados")
    void precoAcimaDoMaximoELimiteZero() {
        ProdutoForm form = service.formDe(1L);
        ApresentacaoForm nova = linha("NOVO-SKU-2", "7899999000028");
        nova.setPreco(new BigDecimal("100000000.00"));
        nova.setLimitePorPedido(0);
        form.getApresentacoes().add(nova);

        assertThat(recusa(form).getErros()).contains("Apresentação 4: o preço deve ser maior que zero.",
                "Apresentação 4: o limite por pedido deve ser maior que zero.");
    }

    @Test
    @DisplayName("EAN com letras ou curto demais é recusado")
    void eanInvalido() {
        ProdutoForm form = service.formDe(1L);
        form.getApresentacoes().add(linha("NOVO-SKU-3", "78ABC"));

        assertThatThrownBy(() -> service.salvar(1L, form, ADMIN)).isInstanceOf(ProdutoException.class)
                .hasMessageContaining("Apresentação 4: o EAN deve ter de 8 a 14 dígitos.");
    }
}
