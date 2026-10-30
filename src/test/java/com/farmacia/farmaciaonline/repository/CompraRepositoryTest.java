package com.farmacia.farmaciaonline.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import com.farmacia.farmaciaonline.TestcontainersConfiguration;
import com.farmacia.farmaciaonline.model.Apresentacao;
import com.farmacia.farmaciaonline.model.CartaoSalvo;
import com.farmacia.farmaciaonline.model.Carrinho;
import com.farmacia.farmaciaonline.model.Endereco;
import com.farmacia.farmaciaonline.model.ItemCarrinho;
import com.farmacia.farmaciaonline.model.Loja;
import com.farmacia.farmaciaonline.model.Usuario;

/**
 * Carrinho, endereço, cartão e loja contra o PostgreSQL real (V1 + seed do V2).
 * Cada teste é desfeito no fim (@DataJpaTest é transacional).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class CompraRepositoryTest {

    @Autowired
    private CarrinhoRepository carrinhoRepository;

    @Autowired
    private EnderecoRepository enderecoRepository;

    @Autowired
    private CartaoSalvoRepository cartaoSalvoRepository;

    @Autowired
    private LojaRepository lojaRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private ApresentacaoRepository apresentacaoRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbc;

    private Usuario cliente() {
        return usuarioRepository.findByEmailIgnoreCase("cliente@farma.com").orElseThrow();
    }

    private Apresentacao novalgina20() {
        return apresentacaoRepository.findBySku("NOV-DIP1G-20").orElseThrow();
    }

    private ItemCarrinho item(Carrinho carrinho, Apresentacao apresentacao, int quantidade, String precoVisto) {
        ItemCarrinho item = new ItemCarrinho();
        item.setCarrinho(carrinho);
        item.setApresentacao(apresentacao);
        item.setQuantidade(quantidade);
        item.setPrecoVisto(new BigDecimal(precoVisto));
        return item;
    }

    private Carrinho carrinhoDeVisitante(UUID token) {
        Carrinho carrinho = new Carrinho();
        carrinho.setToken(token);
        return carrinho;
    }

    private Endereco endereco(Usuario usuario, String destinatario) {
        Endereco endereco = new Endereco();
        endereco.setUsuario(usuario);
        endereco.setDestinatario(destinatario);
        endereco.setCep("01310100");
        endereco.setLogradouro("Avenida Paulista");
        endereco.setNumero("1000");
        endereco.setBairro("Bela Vista");
        endereco.setCidade("São Paulo");
        endereco.setUf("SP");
        return endereco;
    }

    private CartaoSalvo cartao(Usuario usuario, String token) {
        CartaoSalvo cartao = new CartaoSalvo();
        cartao.setUsuario(usuario);
        cartao.setTokenGateway(token);
        cartao.setBandeira("VISA");
        cartao.setUltimos4("4242");
        cartao.setValidadeMes(12);
        cartao.setValidadeAno(2030);
        cartao.setNomeImpresso("CLIENTE TESTE");
        return cartao;
    }

    private Loja loja(String nome, boolean ativa) {
        Loja loja = new Loja();
        loja.setNome(nome);
        loja.setCep("04094050");
        loja.setLogradouro("Avenida Ibirapuera");
        loja.setNumero("2000");
        loja.setBairro("Moema");
        loja.setCidade("São Paulo");
        loja.setUf("SP");
        loja.setAtiva(ativa);
        return loja;
    }

    // ---------- Carrinho ----------

    @Test
    @DisplayName("carrinho de visitante grava os itens e é encontrado pelo token")
    void carrinhoDeVisitanteEBuscadoPeloToken() {
        UUID token = UUID.randomUUID();
        Carrinho carrinho = carrinhoDeVisitante(token);
        carrinho.getItens().add(item(carrinho, novalgina20(), 2, "30.99"));
        carrinhoRepository.saveAndFlush(carrinho);
        entityManager.clear();

        Carrinho encontrado = carrinhoRepository.findByToken(token).orElseThrow();

        assertThat(encontrado.isDeVisitante()).isTrue();
        assertThat(encontrado.getItens()).hasSize(1);
        assertThat(encontrado.getItens().get(0).getSubtotal()).isEqualByComparingTo("61.98");
        assertThat(carrinhoRepository.findByToken(UUID.randomUUID())).isEmpty();
    }

    @Test
    @DisplayName("carrinho de usuário é encontrado pelo id do usuário")
    void carrinhoDeUsuarioEBuscadoPeloIdDoUsuario() {
        Usuario cliente = cliente();
        Carrinho carrinho = new Carrinho();
        carrinho.setUsuario(cliente);
        carrinhoRepository.saveAndFlush(carrinho);
        entityManager.clear();

        Carrinho encontrado = carrinhoRepository.findByUsuarioId(cliente.getId()).orElseThrow();

        assertThat(encontrado.isDeVisitante()).isFalse();
        assertThat(encontrado.getToken()).isNull();
        assertThat(carrinhoRepository.findByUsuarioId(-1L)).isEmpty();
    }

    @Test
    @DisplayName("remover o item da lista apaga o item do banco (orphanRemoval)")
    void removerItemDaListaApagaDoBanco() {
        Carrinho carrinho = carrinhoDeVisitante(UUID.randomUUID());
        carrinho.getItens().add(item(carrinho, novalgina20(), 1, "30.99"));
        carrinhoRepository.saveAndFlush(carrinho);

        carrinho.getItens().clear();
        carrinhoRepository.saveAndFlush(carrinho);

        Long itens = jdbc.queryForObject(
                "SELECT count(*) FROM item_carrinho WHERE carrinho_id = ?", Long.class, carrinho.getId());
        assertThat(itens).isZero();
    }

    @Test
    @DisplayName("um usuário só pode ter um carrinho")
    void umCarrinhoPorUsuario() {
        Usuario cliente = cliente();
        Carrinho primeiro = new Carrinho();
        primeiro.setUsuario(cliente);
        carrinhoRepository.saveAndFlush(primeiro);

        Carrinho segundo = new Carrinho();
        segundo.setUsuario(cliente);

        assertThatThrownBy(() -> carrinhoRepository.saveAndFlush(segundo))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("o banco recusa carrinho sem dono")
    void carrinhoSemDonoEhRecusado() {
        Carrinho semDono = new Carrinho();

        assertThatThrownBy(() -> carrinhoRepository.saveAndFlush(semDono))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("o banco recusa carrinho com usuário e token ao mesmo tempo")
    void carrinhoComDoisDonosEhRecusado() {
        Carrinho comDoisDonos = carrinhoDeVisitante(UUID.randomUUID());
        comDoisDonos.setUsuario(cliente());

        assertThatThrownBy(() -> carrinhoRepository.saveAndFlush(comDoisDonos))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("a mesma apresentação não pode aparecer duas vezes no mesmo carrinho")
    void mesmaApresentacaoDuasVezesEhRecusada() {
        Apresentacao apresentacao = novalgina20();
        Carrinho carrinho = carrinhoDeVisitante(UUID.randomUUID());
        carrinho.getItens().add(item(carrinho, apresentacao, 1, "30.99"));
        carrinho.getItens().add(item(carrinho, apresentacao, 2, "30.99"));

        assertThatThrownBy(() -> carrinhoRepository.saveAndFlush(carrinho))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ---------- Endereço ----------

    @Test
    @DisplayName("endereços do usuário vêm do mais recente para o mais antigo")
    void enderecosVemDoMaisRecenteParaOMaisAntigo() {
        Usuario cliente = cliente();
        Endereco casa = enderecoRepository.saveAndFlush(endereco(cliente, "Casa"));
        Endereco trabalho = enderecoRepository.saveAndFlush(endereco(cliente, "Trabalho"));
        Endereco praia = enderecoRepository.saveAndFlush(endereco(cliente, "Praia"));
        // criado_em é preenchido pelo Hibernate; fixar datas distintas evita depender do relógio
        definirCriadoEm(casa.getId(), 3);
        definirCriadoEm(trabalho.getId(), 1);
        definirCriadoEm(praia.getId(), 2);
        entityManager.clear();

        List<Endereco> enderecos = enderecoRepository.findByUsuarioIdOrderByCriadoEmDesc(cliente.getId());

        assertThat(enderecos).extracting(Endereco::getDestinatario)
                .containsExactly("Trabalho", "Praia", "Casa");
    }

    private void definirCriadoEm(Long enderecoId, int diasAtras) {
        jdbc.update("UPDATE endereco SET criado_em = ? WHERE id = ?",
                Timestamp.from(Instant.now().minus(diasAtras, ChronoUnit.DAYS)), enderecoId);
    }

    // ---------- Cartão ----------

    @Test
    @DisplayName("cartões são buscados por usuário")
    void cartoesSaoBuscadosPorUsuario() {
        Usuario cliente = cliente();
        Usuario admin = usuarioRepository.findByEmailIgnoreCase("admin@farma.com").orElseThrow();
        cartaoSalvoRepository.saveAndFlush(cartao(cliente, "tok_teste_1"));
        entityManager.clear();

        assertThat(cartaoSalvoRepository.findByUsuarioId(cliente.getId()))
                .extracting(CartaoSalvo::getUltimos4).containsExactly("4242");
        assertThat(cartaoSalvoRepository.findByUsuarioId(admin.getId())).isEmpty();
    }

    @Test
    @DisplayName("o mesmo token do gateway não pode ser salvo duas vezes para o mesmo usuário")
    void tokenRepetidoNoMesmoUsuarioEhRecusado() {
        Usuario cliente = cliente();
        cartaoSalvoRepository.saveAndFlush(cartao(cliente, "tok_repetido"));

        CartaoSalvo repetido = cartao(cliente, "tok_repetido");

        assertThatThrownBy(() -> cartaoSalvoRepository.saveAndFlush(repetido))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ---------- Loja ----------

    @Test
    @DisplayName("findByAtivaTrue traz as lojas do seed e ignora as inativas")
    void lojasAtivas() {
        List<Loja> antes = lojaRepository.findByAtivaTrue();
        lojaRepository.saveAndFlush(loja("Loja fechada", false));

        List<Loja> depois = lojaRepository.findByAtivaTrue();

        assertThat(antes).isNotEmpty().allMatch(Loja::isAtiva);
        assertThat(antes).extracting(Loja::getNome).contains("Farma++ Centro");
        assertThat(depois).hasSameSizeAs(antes);
        assertThat(depois).extracting(Loja::getNome).doesNotContain("Loja fechada");
    }
}
