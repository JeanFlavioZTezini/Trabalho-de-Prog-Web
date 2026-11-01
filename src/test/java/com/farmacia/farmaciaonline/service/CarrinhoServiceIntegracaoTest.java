package com.farmacia.farmaciaonline.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import com.farmacia.farmaciaonline.TestcontainersConfiguration;
import com.farmacia.farmaciaonline.model.Apresentacao;
import com.farmacia.farmaciaonline.model.Produto;
import com.farmacia.farmaciaonline.repository.ApresentacaoRepository;
import com.farmacia.farmaciaonline.repository.CarrinhoRepository;
import com.farmacia.farmaciaonline.repository.ProdutoRepository;
import com.farmacia.farmaciaonline.repository.UsuarioRepository;
import com.farmacia.farmaciaonline.service.ResumoCarrinho.LinhaCarrinho;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class CarrinhoServiceIntegracaoTest {
    @Autowired
    private CarrinhoService service;

    @Autowired
    private ProdutoRepository produtoRepository;

    @Autowired
    private ApresentacaoRepository apresentacaoRepository;

    @Autowired
    private CarrinhoRepository carrinhoRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    private DonoCarrinho clienteA;
    private DonoCarrinho clienteB;
    private int sequencia;

    @BeforeEach
    void donos() {
        clienteA = DonoCarrinho.deUsuario(usuarioRepository.findByEmailIgnoreCase("cliente@farma.com").orElseThrow().getId());
        clienteB = DonoCarrinho.deUsuario(usuarioRepository.findByEmailIgnoreCase("admin@farma.com").orElseThrow().getId());
    }

    private Apresentacao apresentacao(String preco, int estoque, int limite) {
        sequencia++;
        Produto produto = new Produto();
        produto.setNome("Produto de teste " + sequencia);
        produto.setMarca("Marca de teste");
        produtoRepository.saveAndFlush(produto);

        Apresentacao apresentacao = new Apresentacao();
        apresentacao.setProduto(produto);
        apresentacao.setDescricao("20 comprimidos");
        apresentacao.setSku("TESTE-CARRINHO-" + UUID.randomUUID().toString().substring(0, 8));
        apresentacao.setEan(String.format("99%012d", Math.abs(UUID.randomUUID().getMostSignificantBits()) % 1_000_000_000_000L));
        apresentacao.setPreco(new BigDecimal(preco));
        apresentacao.setEstoque(estoque);
        apresentacao.setLimitePorPedido(limite);
        apresentacaoRepository.saveAndFlush(apresentacao);
        produto.getApresentacoes().add(apresentacao);
        return apresentacao;
    }

    private LinhaCarrinho unicaLinha(DonoCarrinho dono) {
        ResumoCarrinho resumo = service.resumo(dono);
        assertThat(resumo.getLinhas()).hasSize(1);
        return resumo.getLinhas().get(0);
    }

    @Test
    @DisplayName("adicionar a mesma apresentação soma a quantidade em um único item")
    void somaApresentacaoRepetida() {
        Apresentacao novalgina = apresentacao("30.99", 50, 10);

        service.adicionar(clienteA, novalgina.getId(), 2);
        service.adicionar(clienteA, novalgina.getId(), 3);

        LinhaCarrinho linha = unicaLinha(clienteA);
        assertThat(linha.getQuantidade()).isEqualTo(5);
        assertThat(linha.getSubtotal()).isEqualByComparingTo("154.95");
    }

    @Test
    @DisplayName("o total é recalculado no servidor em BigDecimal, sem erro de arredondamento")
    void totalEmBigDecimal() {
        service.adicionar(clienteA, apresentacao("0.10", 50, 10).getId(), 3);
        service.adicionar(clienteA, apresentacao("30.99", 50, 10).getId(), 2);

        ResumoCarrinho resumo = service.resumo(clienteA);

        assertThat(resumo.getTotal()).isEqualByComparingTo("62.28");
        assertThat(resumo.getTotalUnidades()).isEqualTo(5);
    }

    @Test
    @DisplayName("acima do limite por pedido: ajusta para o limite e avisa")
    void acimaDoLimiteAjustaEAvisa() {
        Apresentacao apresentacao = apresentacao("10.00", 100, 4);

        List<String> avisos = service.adicionar(clienteA, apresentacao.getId(), 9);

        assertThat(unicaLinha(clienteA).getQuantidade()).isEqualTo(4);
        assertThat(avisos).hasSize(1);
        assertThat(avisos.get(0)).contains("limite").contains("4");
    }

    @Test
    @DisplayName("acima do estoque: ajusta para o estoque e avisa")
    void acimaDoEstoqueAjustaEAvisa() {
        Apresentacao apresentacao = apresentacao("10.00", 3, 10);

        List<String> avisos = service.adicionar(clienteA, apresentacao.getId(), 5);

        assertThat(unicaLinha(clienteA).getQuantidade()).isEqualTo(3);
        assertThat(avisos.get(0)).contains("estoque");
    }

    @Test
    @DisplayName("já no máximo possível: adicionar mais é recusado")
    void jaNoMaximoERecusado() {
        Apresentacao apresentacao = apresentacao("10.00", 2, 10);
        service.adicionar(clienteA, apresentacao.getId(), 2);

        assertThatThrownBy(() -> service.adicionar(clienteA, apresentacao.getId(), 1))
                .isInstanceOf(CarrinhoException.class)
                .hasMessageContaining("máxima");
    }

    @Test
    @DisplayName("não adiciona produto inativo, apresentação inativa nem item sem estoque")
    void recusaIndisponiveis() {
        Apresentacao semEstoque = apresentacao("10.00", 0, 10);
        Apresentacao inativa = apresentacao("10.00", 5, 10);
        inativa.setAtiva(false);
        Apresentacao produtoInativo = apresentacao("10.00", 5, 10);
        produtoInativo.getProduto().setAtivo(false);
        apresentacaoRepository.flush();

        assertThatThrownBy(() -> service.adicionar(clienteA, semEstoque.getId(), 1)).isInstanceOf(CarrinhoException.class);
        assertThatThrownBy(() -> service.adicionar(clienteA, inativa.getId(), 1)).isInstanceOf(CarrinhoException.class);
        assertThatThrownBy(() -> service.adicionar(clienteA, produtoInativo.getId(), 1)).isInstanceOf(CarrinhoException.class);
        assertThat(service.resumo(clienteA).isVazio()).isTrue();
    }

    @Test
    @DisplayName("quantidade não positiva é recusada na adição e na alteração")
    void quantidadeNaoPositiva() {
        Apresentacao apresentacao = apresentacao("10.00", 10, 10);
        service.adicionar(clienteA, apresentacao.getId(), 1);
        Long itemId = unicaLinha(clienteA).getItemId();

        assertThatThrownBy(() -> service.adicionar(clienteA, apresentacao.getId(), 0)).isInstanceOf(CarrinhoException.class);
        assertThatThrownBy(() -> service.alterarQuantidade(clienteA, itemId, 0)).isInstanceOf(CarrinhoException.class);
        assertThatThrownBy(() -> service.alterarQuantidade(clienteA, itemId, -2)).isInstanceOf(CarrinhoException.class);
        assertThat(unicaLinha(clienteA).getQuantidade()).isEqualTo(1);
    }

    @Test
    @DisplayName("item que ficou sem estoque depois de adicionado impede finalizar")
    void itemQueFicouIndisponivelImpedeFinalizar() {
        Apresentacao apresentacao = apresentacao("10.00", 5, 10);
        service.adicionar(clienteA, apresentacao.getId(), 2);
        assertThat(service.resumo(clienteA).isPodeFinalizar()).isTrue();

        apresentacao.setEstoque(0);
        apresentacaoRepository.saveAndFlush(apresentacao);

        ResumoCarrinho resumo = service.resumo(clienteA);
        assertThat(resumo.isPodeFinalizar()).isFalse();
        assertThat(resumo.getLinhas().get(0).getProblema()).contains("estoque");
        assertThat(resumo.getTotal()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("estoque menor que a quantidade do carrinho impede finalizar até ajustar")
    void estoqueInsuficienteImpedeFinalizar() {
        Apresentacao apresentacao = apresentacao("10.00", 5, 10);
        service.adicionar(clienteA, apresentacao.getId(), 5);

        apresentacao.setEstoque(2);
        apresentacaoRepository.saveAndFlush(apresentacao);

        assertThat(service.resumo(clienteA).isPodeFinalizar()).isFalse();

        service.alterarQuantidade(clienteA, unicaLinha(clienteA).getItemId(), 2);

        assertThat(service.resumo(clienteA).isPodeFinalizar()).isTrue();
    }

    @Test
    @DisplayName("preço alterado é informado, o total usa o preço atual e o cliente pode aceitar")
    void precoAlterado() {
        Apresentacao apresentacao = apresentacao("30.99", 50, 10);
        service.adicionar(clienteA, apresentacao.getId(), 2);

        apresentacao.setPreco(new BigDecimal("35.00"));
        apresentacaoRepository.saveAndFlush(apresentacao);

        LinhaCarrinho linha = unicaLinha(clienteA);
        assertThat(linha.isPrecoAlterado()).isTrue();
        assertThat(linha.getPrecoVisto()).isEqualByComparingTo("30.99");
        assertThat(linha.getPrecoUnitario()).isEqualByComparingTo("35.00");
        assertThat(service.resumo(clienteA).getTotal()).isEqualByComparingTo("70.00");

        service.aceitarPreco(clienteA, linha.getItemId());

        assertThat(unicaLinha(clienteA).isPrecoAlterado()).isFalse();
    }

    @Test
    void removerEEsvaziar() {
        Apresentacao primeira = apresentacao("10.00", 10, 10);
        Apresentacao segunda = apresentacao("20.00", 10, 10);
        service.adicionar(clienteA, primeira.getId(), 1);
        service.adicionar(clienteA, segunda.getId(), 1);

        Long primeiroItem = service.resumo(clienteA).getLinhas().get(0).getItemId();
        service.remover(clienteA, primeiroItem);
        assertThat(service.resumo(clienteA).getLinhas()).hasSize(1);

        service.esvaziar(clienteA);
        assertThat(service.resumo(clienteA).isVazio()).isTrue();
    }

    @Test
    @DisplayName("item de outro carrinho não pode ser alterado, removido nem consultado")
    void itemAlheioNaoMexe() {
        Apresentacao apresentacao = apresentacao("10.00", 50, 10);
        service.adicionar(clienteA, apresentacao.getId(), 2);
        Long itemDeA = unicaLinha(clienteA).getItemId();

        assertThatThrownBy(() -> service.alterarQuantidade(clienteB, itemDeA, 9)).isInstanceOf(CarrinhoException.class);
        assertThatThrownBy(() -> service.remover(clienteB, itemDeA)).isInstanceOf(CarrinhoException.class);
        assertThatThrownBy(() -> service.aceitarPreco(clienteB, itemDeA)).isInstanceOf(CarrinhoException.class);
        assertThat(service.resumo(clienteB).isVazio()).isTrue();
        assertThat(unicaLinha(clienteA).getQuantidade()).isEqualTo(2);
    }

    @Test
    @DisplayName("o carrinho de um visitante não é alcançado por outro token")
    void outroTokenNaoVeOCarrinho() {
        Apresentacao apresentacao = apresentacao("10.00", 50, 10);
        DonoCarrinho visitante = DonoCarrinho.deVisitante(UUID.randomUUID());
        service.adicionar(visitante, apresentacao.getId(), 1);

        assertThat(service.resumo(DonoCarrinho.deVisitante(UUID.randomUUID())).isVazio()).isTrue();
        assertThat(service.resumo(null).isVazio()).isTrue();
    }

    @Test
    void donoPrecisaSerUsuarioOuTokenNuncaOsDois() {
        assertThatThrownBy(() -> new DonoCarrinho(1L, UUID.randomUUID())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DonoCarrinho(null, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("mesclagem: item repetido soma, o resto vem junto e o carrinho do visitante some")
    void mesclaItemRepetido() {
        Apresentacao repetida = apresentacao("10.00", 50, 10);
        Apresentacao soDoVisitante = apresentacao("20.00", 50, 10);
        UUID token = UUID.randomUUID();
        DonoCarrinho visitante = DonoCarrinho.deVisitante(token);

        service.adicionar(clienteA, repetida.getId(), 3);
        service.adicionar(visitante, repetida.getId(), 2);
        service.adicionar(visitante, soDoVisitante.getId(), 1);

        List<String> avisos = service.mesclar(token, clienteA.usuarioId());

        assertThat(avisos).isEmpty();
        ResumoCarrinho resumo = service.resumo(clienteA);
        assertThat(resumo.getLinhas()).hasSize(2);
        assertThat(resumo.getTotalUnidades()).isEqualTo(6);
        assertThat(resumo.getTotal()).isEqualByComparingTo("70.00");
        assertThat(carrinhoRepository.findByToken(token)).isEmpty();
    }

    @Test
    @DisplayName("mesclagem: a soma que passa do estoque é cortada e vira aviso")
    void mesclaComExcessoDeEstoque() {
        Apresentacao apresentacao = apresentacao("10.00", 4, 10);
        UUID token = UUID.randomUUID();

        service.adicionar(clienteA, apresentacao.getId(), 3);
        service.adicionar(DonoCarrinho.deVisitante(token), apresentacao.getId(), 3);

        List<String> avisos = service.mesclar(token, clienteA.usuarioId());

        assertThat(unicaLinha(clienteA).getQuantidade()).isEqualTo(4);
        assertThat(avisos).hasSize(1);
        assertThat(avisos.get(0)).contains("estoque");
    }

    @Test
    @DisplayName("mesclagem: a soma que passa do limite por pedido é cortada e vira aviso")
    void mesclaComExcessoDeLimite() {
        Apresentacao apresentacao = apresentacao("10.00", 100, 5);
        UUID token = UUID.randomUUID();

        service.adicionar(clienteA, apresentacao.getId(), 4);
        service.adicionar(DonoCarrinho.deVisitante(token), apresentacao.getId(), 4);

        List<String> avisos = service.mesclar(token, clienteA.usuarioId());

        assertThat(unicaLinha(clienteA).getQuantidade()).isEqualTo(5);
        assertThat(avisos.get(0)).contains("limite");
    }

    @Test
    @DisplayName("mesclagem: o mesmo token não é consumido duas vezes (nada duplica)")
    void mesmoTokenNaoDuplica() {
        Apresentacao apresentacao = apresentacao("10.00", 50, 10);
        UUID token = UUID.randomUUID();
        service.adicionar(DonoCarrinho.deVisitante(token), apresentacao.getId(), 2);

        service.mesclar(token, clienteA.usuarioId());
        List<String> segunda = service.mesclar(token, clienteA.usuarioId());

        assertThat(segunda).isEmpty();
        assertThat(unicaLinha(clienteA).getQuantidade()).isEqualTo(2);
    }

    @Test
    @DisplayName("mesclagem: item que ficou indisponível continua no carrinho, marcado, para a pessoa decidir")
    void mesclaItemIndisponivel() {
        Apresentacao apresentacao = apresentacao("10.00", 5, 10);
        UUID token = UUID.randomUUID();
        service.adicionar(DonoCarrinho.deVisitante(token), apresentacao.getId(), 2);
        apresentacao.setEstoque(0);
        apresentacaoRepository.saveAndFlush(apresentacao);

        service.mesclar(token, clienteA.usuarioId());

        LinhaCarrinho linha = unicaLinha(clienteA);
        assertThat(linha.getQuantidade()).isEqualTo(2);
        assertThat(linha.isComProblema()).isTrue();
    }

    @Test
    @DisplayName("mesclar um token que não existe não faz nada")
    void tokenInexistente() {
        assertThat(service.mesclar(UUID.randomUUID(), clienteA.usuarioId())).isEmpty();
        assertThat(service.resumo(clienteA).isVazio()).isTrue();
    }

    @Test
    @DisplayName("contarUnidades: soma as quantidades do dono, do visitante, e dá zero sem carrinho ou sem dono")
    void contaUnidades() {
        UUID token = UUID.randomUUID();
        service.adicionar(clienteA, apresentacao("10.00", 50, 10).getId(), 2);
        service.adicionar(clienteA, apresentacao("10.00", 50, 10).getId(), 3);
        service.adicionar(DonoCarrinho.deVisitante(token), apresentacao("10.00", 50, 10).getId(), 4);

        assertThat(service.contarUnidades(clienteA)).isEqualTo(5);
        assertThat(service.contarUnidades(DonoCarrinho.deVisitante(token))).isEqualTo(4);
        assertThat(service.contarUnidades(clienteB)).isZero();
        assertThat(service.contarUnidades(DonoCarrinho.deVisitante(UUID.randomUUID()))).isZero();
        assertThat(service.contarUnidades(null)).isZero();
    }

    @Test
    @DisplayName("contarUnidades: carrinho esvaziado conta zero")
    void contaZeroDepoisDeEsvaziar() {
        service.adicionar(clienteA, apresentacao("10.00", 50, 10).getId(), 2);
        service.esvaziar(clienteA);

        assertThat(service.contarUnidades(clienteA)).isZero();
    }
}
