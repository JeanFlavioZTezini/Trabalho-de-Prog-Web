package com.farmacia.farmaciaonline.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.farmacia.farmaciaonline.TestcontainersConfiguration;
import com.farmacia.farmaciaonline.model.Apresentacao;
import com.farmacia.farmaciaonline.model.Endereco;
import com.farmacia.farmaciaonline.model.EnderecoEntrega;
import com.farmacia.farmaciaonline.model.EnderecoForm;
import com.farmacia.farmaciaonline.model.ItemPedido;
import com.farmacia.farmaciaonline.model.Pedido;
import com.farmacia.farmaciaonline.model.Usuario;
import com.farmacia.farmaciaonline.model.enums.TipoEntrega;
import com.farmacia.farmaciaonline.repository.ApresentacaoRepository;
import com.farmacia.farmaciaonline.repository.EnderecoRepository;
import com.farmacia.farmaciaonline.repository.PedidoRepository;
import com.farmacia.farmaciaonline.repository.UsuarioRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class EnderecoServiceIntegracaoTest {

    private static final long CLIENTE = 2L;
    private static final long OUTRO_USUARIO = 1L;
    private static final long PENDENTE = 3L;

    @Autowired
    private EnderecoService service;

    @Autowired
    private EnderecoRepository enderecoRepository;

    @Autowired
    private PedidoRepository pedidoRepository;

    @Autowired
    private ApresentacaoRepository apresentacaoRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @PersistenceContext
    private EntityManager em;

    private static EnderecoForm formPaulista() {
        EnderecoForm form = new EnderecoForm();
        form.setDestinatario("Cliente Teste");
        form.setCep("01310-100");
        form.setLogradouro("Avenida Paulista");
        form.setNumero("1000");
        form.setComplemento("Apto 12");
        form.setBairro("Bela Vista");
        form.setCidade("São Paulo");
        form.setUf("SP");
        form.setReferencia("Portaria 24h");
        return form;
    }

    private static void assertStatus(Throwable erro, HttpStatus esperado) {
        assertThat(erro).isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(esperado));
    }

    @Test
    @DisplayName("criar vincula o endereço ao usuário informado e grava o CEP só com dígitos")
    void criarVinculaAoUsuarioDaSessao() {
        Long id = service.salvar(CLIENTE, null, formPaulista());
        em.flush();
        em.clear();

        Endereco salvo = enderecoRepository.findById(id).orElseThrow();
        assertThat(salvo.getUsuario().getId()).isEqualTo(CLIENTE);
        assertThat(salvo.getCep()).isEqualTo("01310100");
        assertThat(salvo.getUf()).isEqualTo("SP");
        assertThat(salvo.getComplemento()).isEqualTo("Apto 12");
        assertThat(service.listar(CLIENTE)).extracting(Endereco::getId).contains(id);
    }

    @Test
    @DisplayName("espaços sobram nas pontas, UF vai para maiúsculas e complemento/referência vazios viram nulo")
    void normalizaOsCampos() {
        EnderecoForm form = formPaulista();
        form.setDestinatario("  Cliente Teste  ");
        form.setLogradouro("  Avenida Paulista ");
        form.setComplemento("   ");
        form.setReferencia("");

        Endereco salvo = enderecoRepository.findById(service.salvar(CLIENTE, null, form)).orElseThrow();

        assertThat(salvo.getDestinatario()).isEqualTo("Cliente Teste");
        assertThat(salvo.getLogradouro()).isEqualTo("Avenida Paulista");
        assertThat(salvo.getComplemento()).isNull();
        assertThat(salvo.getReferencia()).isNull();
    }

    @Test
    @DisplayName("endereço fora da área atendida também é salvo (quem bloqueia é o checkout)")
    void enderecoDeQualquerRegiaoEhSalvo() {
        EnderecoForm form = formPaulista();
        form.setCep("69900-000");
        form.setCidade("Rio Branco");
        form.setUf("AC");

        Long id = service.salvar(CLIENTE, null, form);

        assertThat(enderecoRepository.findById(id)).isPresent();
    }

    @Test
    @DisplayName("editar altera o próprio endereço")
    void editarAlteraOsDados() {
        Long id = service.salvar(CLIENTE, null, formPaulista());
        EnderecoForm novo = service.formDe(CLIENTE, id);
        novo.setNumero("2000");
        novo.setCep("04094-050");
        service.salvar(CLIENTE, id, novo);
        em.flush();
        em.clear();

        Endereco salvo = enderecoRepository.findById(id).orElseThrow();
        assertThat(salvo.getNumero()).isEqualTo("2000");
        assertThat(salvo.getCep()).isEqualTo("04094050");
    }

    @Test
    @DisplayName("excluir remove o próprio endereço")
    void excluirRemove() {
        Long id = service.salvar(CLIENTE, null, formPaulista());

        service.excluir(CLIENTE, id);
        em.clear();

        assertThat(enderecoRepository.findById(id)).isEmpty();
    }

    @Test
    @DisplayName("a lista de cada usuário só tem os endereços dele")
    void listaSoDoProprioUsuario() {
        Long id = service.salvar(CLIENTE, null, formPaulista());

        assertThat(service.listar(CLIENTE)).extracting(Endereco::getId).contains(id);
        assertThat(service.listar(OUTRO_USUARIO)).extracting(Endereco::getId).doesNotContain(id);
    }

    @Test
    @DisplayName("ler, editar, excluir ou copiar endereço de outro usuário responde 404 e não altera nada")
    void enderecoDeOutroUsuarioNaoExisteParaVoce() {
        Long id = service.salvar(CLIENTE, null, formPaulista());
        em.flush();
        em.clear();

        assertThatThrownBy(() -> service.buscar(OUTRO_USUARIO, id)).satisfies(e -> assertStatus(e, HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> service.formDe(OUTRO_USUARIO, id)).satisfies(e -> assertStatus(e, HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> service.copiaParaEntrega(OUTRO_USUARIO, id))
                .satisfies(e -> assertStatus(e, HttpStatus.NOT_FOUND));

        EnderecoForm invasor = formPaulista();
        invasor.setLogradouro("Rua do Invasor");
        assertThatThrownBy(() -> service.salvar(OUTRO_USUARIO, id, invasor))
                .satisfies(e -> assertStatus(e, HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> service.excluir(OUTRO_USUARIO, id)).satisfies(e -> assertStatus(e, HttpStatus.NOT_FOUND));

        em.clear();
        Endereco intacto = enderecoRepository.findById(id).orElseThrow();
        assertThat(intacto.getUsuario().getId()).isEqualTo(CLIENTE);
        assertThat(intacto.getLogradouro()).isEqualTo("Avenida Paulista");
    }

    @Test
    @DisplayName("id que não existe também responde 404")
    void idInexistente() {
        assertThatThrownBy(() -> service.buscar(CLIENTE, 999_999L)).satisfies(e -> assertStatus(e, HttpStatus.NOT_FOUND));
    }

    @Test
    @DisplayName("conta que não está ATIVA recebe 403")
    void contaPendenteNaoUsaEnderecos() {
        assertThatThrownBy(() -> service.listar(PENDENTE)).satisfies(e -> assertStatus(e, HttpStatus.FORBIDDEN));
        assertThatThrownBy(() -> service.salvar(PENDENTE, null, formPaulista()))
                .satisfies(e -> assertStatus(e, HttpStatus.FORBIDDEN));
    }

    @Test
    @DisplayName("conta excluída depois do login recebe 403")
    void contaExcluidaNaoUsaEnderecos() {
        Usuario cliente = usuarioRepository.findById(CLIENTE).orElseThrow();
        cliente.setStatus(com.farmacia.farmaciaonline.model.enums.StatusUsuario.EXCLUIDA);
        cliente.setExcluidoEm(java.time.Instant.now());
        usuarioRepository.saveAndFlush(cliente);

        assertThatThrownBy(() -> service.listar(CLIENTE)).satisfies(e -> assertStatus(e, HttpStatus.FORBIDDEN));
    }

    private Pedido pedidoComEntrega(Usuario usuario, EnderecoEntrega entrega) {
        Apresentacao apresentacao = apresentacaoRepository.findBySku("NOV-DIP1G-20").orElseThrow();

        Pedido pedido = new Pedido();
        pedido.setUsuario(usuario);
        pedido.setTipoEntrega(TipoEntrega.ENTREGA);
        pedido.setEnderecoEntrega(entrega);
        pedido.setPrazoEntregaDias(5);
        pedido.setFrete(new BigDecimal("9.90"));

        ItemPedido item = new ItemPedido();
        item.setPedido(pedido);
        item.setApresentacao(apresentacao);
        item.setNomeProduto(apresentacao.getProduto().getNome());
        item.setDescricaoApresentacao(apresentacao.getDescricao());
        item.setSku(apresentacao.getSku());
        item.setPrecoUnitario(apresentacao.getPreco());
        item.setQuantidade(1);
        item.setExigeReceita(apresentacao.getProduto().isExigeReceita());
        pedido.getItens().add(item);
        pedido.recalcularTotais();
        return pedido;
    }

    @Test
    @DisplayName("editar e excluir o endereço salvo não altera o endereço de um pedido já feito")
    void pedidoAntigoGuardaACopia() {
        Long enderecoId = service.salvar(CLIENTE, null, formPaulista());
        EnderecoEntrega copia = service.copiaParaEntrega(CLIENTE, enderecoId);
        Usuario cliente = usuarioRepository.findById(CLIENTE).orElseThrow();
        Pedido pedido = pedidoRepository.saveAndFlush(pedidoComEntrega(cliente, copia));

        EnderecoForm novo = service.formDe(CLIENTE, enderecoId);
        novo.setLogradouro("Rua Nova");
        novo.setNumero("1");
        novo.setCep("20040-020");
        novo.setCidade("Rio de Janeiro");
        novo.setUf("RJ");
        service.salvar(CLIENTE, enderecoId, novo);
        em.flush();
        em.clear();

        EnderecoEntrega aposEditar = pedidoRepository.findById(pedido.getId()).orElseThrow().getEnderecoEntrega();
        assertThat(aposEditar.getLogradouro()).isEqualTo("Avenida Paulista");
        assertThat(aposEditar.getCep()).isEqualTo("01310100");
        assertThat(aposEditar.getCidade()).isEqualTo("São Paulo");
        assertThat(aposEditar.getUf()).isEqualTo("SP");

        service.excluir(CLIENTE, enderecoId);
        em.flush();
        em.clear();

        assertThat(enderecoRepository.findById(enderecoId)).isEmpty();
        EnderecoEntrega aposExcluir = pedidoRepository.findById(pedido.getId()).orElseThrow().getEnderecoEntrega();
        assertThat(aposExcluir.getDestinatario()).isEqualTo("Cliente Teste");
        assertThat(aposExcluir.getLogradouro()).isEqualTo("Avenida Paulista");
        assertThat(aposExcluir.getNumero()).isEqualTo("1000");
        assertThat(aposExcluir.getComplemento()).isEqualTo("Apto 12");
        assertThat(aposExcluir.getBairro()).isEqualTo("Bela Vista");
        assertThat(aposExcluir.getReferencia()).isEqualTo("Portaria 24h");
    }

    @Test
    @DisplayName("a cópia para o pedido não acompanha mudanças posteriores no endereço salvo")
    void copiaEhIndependenteDoEnderecoSalvo() {
        Long id = service.salvar(CLIENTE, null, formPaulista());
        EnderecoEntrega copia = service.copiaParaEntrega(CLIENTE, id);

        EnderecoForm novo = service.formDe(CLIENTE, id);
        novo.setLogradouro("Rua Nova");
        service.salvar(CLIENTE, id, novo);

        assertThat(copia.getLogradouro()).isEqualTo("Avenida Paulista");
        assertThat(copia.getCep()).isEqualTo("01310100");
    }
}
