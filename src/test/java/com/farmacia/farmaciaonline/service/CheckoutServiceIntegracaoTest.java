package com.farmacia.farmaciaonline.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.farmacia.farmaciaonline.model.Apresentacao;
import com.farmacia.farmaciaonline.model.CheckoutForm;
import com.farmacia.farmaciaonline.model.HistoricoStatusPedido;
import com.farmacia.farmaciaonline.model.ItemPedido;
import com.farmacia.farmaciaonline.model.Loja;
import com.farmacia.farmaciaonline.model.Pagamento;
import com.farmacia.farmaciaonline.model.Pedido;
import com.farmacia.farmaciaonline.model.Usuario;
import com.farmacia.farmaciaonline.model.enums.MetodoPagamento;
import com.farmacia.farmaciaonline.model.enums.Perfil;
import com.farmacia.farmaciaonline.model.enums.StatusPagamento;
import com.farmacia.farmaciaonline.model.enums.StatusPedido;
import com.farmacia.farmaciaonline.model.enums.StatusUsuario;
import com.farmacia.farmaciaonline.model.enums.TipoEntrega;
import com.farmacia.farmaciaonline.repository.LojaRepository;

/** Regras do registro do pedido: acesso, total, frete, receita, preço alterado, estoque e envio repetido. */
class CheckoutServiceIntegracaoTest extends CheckoutTesteBase {

    @Autowired
    private LojaRepository lojaRepository;

    private static void assertStatus(Throwable erro, HttpStatus esperado) {
        assertThat(erro).isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(esperado));
    }

    private Pedido pedidoCompleto(Long id) {
        return transacao.execute(status -> {
            Pedido pedido = pedidoRepository.findById(id).orElseThrow();
            pedido.getItens().size();
            return pedido;
        });
    }

    // ---------- Quem pode comprar ----------

    @Test
    @DisplayName("conta pendente e administrador não compram")
    void soClienteAtivo() {
        Usuario pendente = novoUsuario(Perfil.CLIENTE, StatusUsuario.PENDENTE);
        Usuario admin = novoUsuario(Perfil.ADMIN, StatusUsuario.ATIVA);
        CheckoutForm form = retirada(MetodoPagamento.PIX);

        assertThatThrownBy(() -> checkout.montarResumo(pendente.getId(), form))
                .satisfies(e -> assertStatus(e, HttpStatus.FORBIDDEN));
        assertThatThrownBy(() -> checkout.registrar(admin.getId(), form, "x", UUID.randomUUID()))
                .satisfies(e -> assertStatus(e, HttpStatus.FORBIDDEN));
    }

    @Test
    @DisplayName("carrinho vazio não vira pedido")
    void carrinhoVazio() {
        Usuario semCarrinho = novoCliente();

        assertThat(checkout.montarResumo(semCarrinho.getId(), retirada(MetodoPagamento.PIX))).isEmpty();
        assertThatThrownBy(() -> checkout.registrar(semCarrinho.getId(), retirada(MetodoPagamento.PIX), "x", UUID.randomUUID()))
                .isInstanceOf(CheckoutException.class);
    }

    @Test
    @DisplayName("endereço de outro cliente responde 404, inclusive ao registrar")
    void enderecoAlheio() {
        Usuario dono = novoCliente();
        Usuario outro = novoCliente();
        noCarrinho(outro, novaApresentacao("10.00", 5), 1);
        Long enderecoDoDono = novoEndereco(dono, "79002-000", "MS");
        CheckoutForm form = entrega(enderecoDoDono, MetodoPagamento.PIX);

        assertThatThrownBy(() -> checkout.montarResumo(outro.getId(), form))
                .satisfies(e -> assertStatus(e, HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> checkout.registrar(outro.getId(), form, "x", UUID.randomUUID()))
                .satisfies(e -> assertStatus(e, HttpStatus.NOT_FOUND));
    }

    @Test
    @DisplayName("loja inativa e faltas de escolha aparecem como problemas e não deixam confirmar")
    void lojaInativaEEscolhasFaltando() {
        Usuario cliente = novoCliente();
        noCarrinho(cliente, novaApresentacao("10.00", 5), 1);
        Loja fechada = new Loja();
        fechada.setNome("Fechada");
        fechada.setCep("01310100");
        fechada.setLogradouro("Rua");
        fechada.setNumero("1");
        fechada.setBairro("B");
        fechada.setCidade("C");
        fechada.setUf("SP");
        fechada.setAtiva(false);
        Long fechadaId = lojaRepository.saveAndFlush(fechada).getId();

        CheckoutForm naLojaFechada = retirada(MetodoPagamento.PIX);
        naLojaFechada.setLojaId(fechadaId);
        ResumoCheckout comLojaFechada = checkout.montarResumo(cliente.getId(), naLojaFechada).orElseThrow();
        assertThat(comLojaFechada.isConfirmavel()).isFalse();
        assertThatThrownBy(() -> checkout.registrar(cliente.getId(), naLojaFechada, comLojaFechada.conferencia(), UUID.randomUUID()))
                .isInstanceOf(CheckoutException.class);

        ResumoCheckout semNada = checkout.montarResumo(cliente.getId(), new CheckoutForm()).orElseThrow();
        assertThat(semNada.problemas()).hasSize(2);
    }

    // ---------- Total e frete ----------

    @Test
    @DisplayName("entrega: total é a soma dos itens com o frete da tabela, e o pedido guarda cópias de tudo")
    void totalESnapshotsDaEntrega() {
        Usuario cliente = novoCliente();
        Apresentacao a = novaApresentacao("10.50", 10);
        Apresentacao b = novaApresentacao("3.33", 10);
        noCarrinho(cliente, a, 2);
        noCarrinho(cliente, b, 3);
        Long enderecoId = novoEndereco(cliente, "79800-001", "MS");

        Long id = comprar(cliente, entrega(enderecoId, MetodoPagamento.CARTAO_DEBITO), cartao(CARTAO_APROVADO));

        Pedido pedido = pedidoCompleto(id);
        assertThat(pedido.getSubtotal()).isEqualByComparingTo("30.99");
        assertThat(pedido.getFrete()).isEqualByComparingTo("14.90");
        assertThat(pedido.getTotal()).isEqualByComparingTo("45.89");
        assertThat(pedido.getPrazoEntregaDias()).isEqualTo(3);
        assertThat(pedido.getEnderecoEntrega().getCep()).isEqualTo("79800001");
        assertThat(pedido.getItens()).hasSize(2);
        ItemPedido item = pedido.getItens().get(0);
        assertThat(item.getNomeProduto()).isEqualTo(a.getProduto().getNome());
        assertThat(item.getDescricaoApresentacao()).isEqualTo(a.getDescricao());
        assertThat(item.getSku()).isEqualTo(a.getSku());
        assertThat(item.getPrecoUnitario()).isEqualByComparingTo("10.50");

        assertThat(estoqueDe(a)).isEqualTo(8);
        assertThat(estoqueDe(b)).isEqualTo(7);
        assertThat(itensNoCarrinho(cliente)).isZero();

        // Mudar o catálogo e o endereço depois não altera o pedido.
        mudarPreco(a, "99.00");
        enderecos.excluir(cliente.getId(), enderecoId);
        Pedido depois = pedidoCompleto(id);
        assertThat(depois.getItens().get(0).getPrecoUnitario()).isEqualByComparingTo("10.50");
        assertThat(depois.getEnderecoEntrega().getLogradouro()).isEqualTo("Avenida Teste");
    }

    @Test
    @DisplayName("retirada: frete zero, sem endereço, com cópia da loja")
    void retiradaTemFreteZero() {
        Usuario cliente = novoCliente();
        noCarrinho(cliente, novaApresentacao("10.00", 5), 1);

        Long id = comprar(cliente, retirada(MetodoPagamento.PIX), null);

        Pedido pedido = pedidoCompleto(id);
        assertThat(pedido.getTipoEntrega()).isEqualTo(TipoEntrega.RETIRADA);
        assertThat(pedido.getFrete()).isEqualByComparingTo("0");
        assertThat(pedido.getTotal()).isEqualByComparingTo("10.00");
        assertThat(pedido.getEnderecoEntrega()).isNull();
        assertThat(pedido.getLocalRetirada().getNome()).isEqualTo("Farma++ Centro");
    }

    @Test
    @DisplayName("região não atendida bloqueia a entrega, mas a retirada continua possível")
    void regiaoNaoAtendida() {
        Usuario cliente = novoCliente();
        Apresentacao a = novaApresentacao("10.00", 5);
        noCarrinho(cliente, a, 1);
        Long acre = novoEndereco(cliente, "69900-000", "AC");
        CheckoutForm form = entrega(acre, MetodoPagamento.PIX);

        ResumoCheckout resumo = checkout.montarResumo(cliente.getId(), form).orElseThrow();
        assertThat(resumo.problemas()).anyMatch(p -> p.contains("Mato Grosso do Sul"));
        assertThatThrownBy(() -> checkout.registrar(cliente.getId(), form, resumo.conferencia(), UUID.randomUUID()))
                .isInstanceOf(CheckoutException.class);
        assertThat(estoqueDe(a)).isEqualTo(5);

        assertThat(comprar(cliente, retirada(MetodoPagamento.PIX), null)).isNotNull();
    }

    // ---------- Receita ----------

    @Test
    @DisplayName("controlado: entrega é recusada no servidor mesmo com endereço válido; retirada passa e cita a receita")
    void controladoSoComRetirada() {
        Usuario cliente = novoCliente();
        Apresentacao controlado = novaApresentacao("18.50", 5, true);
        noCarrinho(cliente, novaApresentacao("10.00", 5), 1);
        noCarrinho(cliente, controlado, 1);
        CheckoutForm entrega = entrega(novoEndereco(cliente, "79002-000", "MS"), MetodoPagamento.PIX);

        ResumoCheckout resumo = checkout.montarResumo(cliente.getId(), entrega).orElseThrow();
        assertThat(resumo.exigeReceita()).isTrue();
        assertThat(resumo.problemas()).anyMatch(p -> p.contains("exige receita"));
        assertThatThrownBy(() -> checkout.registrar(cliente.getId(), entrega, resumo.conferencia(), UUID.randomUUID()))
                .isInstanceOf(CheckoutException.class);
        assertThat(estoqueDe(controlado)).isEqualTo(5);

        Long id = comprar(cliente, retirada(MetodoPagamento.PIX), null);
        assertThat(pedidoCompleto(id).temItemQueExigeReceita()).isTrue();
        assertThat(envio.mensagens().get(0).texto()).contains("apresente a receita na loja");
    }

    // ---------- Preço, estoque e limite revalidados ----------

    @Test
    @DisplayName("preço alterado: exige nova confirmação, e o total novo só vale depois de aceito")
    void precoAlteradoExigeNovaConfirmacao() {
        Usuario cliente = novoCliente();
        Apresentacao a = novaApresentacao("10.00", 5);
        noCarrinho(cliente, a, 2);
        CheckoutForm form = retirada(MetodoPagamento.PIX);
        String conferenciaAntiga = conferenciaDe(cliente, form);

        mudarPreco(a, "12.00");

        assertThatThrownBy(() -> checkout.registrar(cliente.getId(), form, conferenciaAntiga, UUID.randomUUID()))
                .isInstanceOf(CheckoutException.class)
                .hasMessageContaining("preço");
        assertThat(estoqueDe(a)).isEqualTo(5);
        assertThat(itensNoCarrinho(cliente)).isEqualTo(1);

        ResumoCheckout comAviso = checkout.montarResumo(cliente.getId(), form).orElseThrow();
        assertThat(comAviso.precoAlterado()).isTrue();
        assertThat(comAviso.isConfirmavel()).isFalse();
        assertThat(comAviso.total()).isEqualByComparingTo("24.00");

        // Mesmo aceitando, a confirmação antiga não vale: a pessoa precisa ver o resumo novo.
        checkout.aceitarPrecos(cliente.getId());
        assertThatThrownBy(() -> checkout.registrar(cliente.getId(), form, conferenciaAntiga, UUID.randomUUID()))
                .isInstanceOf(CheckoutException.class);

        Long id = comprar(cliente, form, null);
        assertThat(pedidoCompleto(id).getTotal()).isEqualByComparingTo("24.00");
    }

    @Test
    @DisplayName("preço que muda entre a leitura e a reserva não passa: a atualização condicional confere o preço")
    void reservaConfereOPreco() {
        Apresentacao a = novaApresentacao("10.00", 5);

        int reservou = transacao.execute(status -> apresentacaoRepository.reservarEstoque(a.getId(), 1, new BigDecimal("9.00")));

        assertThat(reservou).isZero();
        assertThat(estoqueDe(a)).isEqualTo(5);
    }

    @Test
    @DisplayName("sem estoque, acima do limite ou item inativo: não confirma e não altera nada")
    void estoqueLimiteEInativo() {
        Usuario semEstoque = novoCliente();
        Apresentacao poucas = novaApresentacao("10.00", 1);
        noCarrinho(semEstoque, poucas, 2);
        assertThat(checkout.montarResumo(semEstoque.getId(), retirada(MetodoPagamento.PIX)).orElseThrow().problemas())
                .anyMatch(p -> p.contains("só restam 1"));

        Usuario acimaDoLimite = novoCliente();
        Apresentacao limitada = novaApresentacao("10.00", 50);
        limitada.setLimitePorPedido(2);
        limitada = apresentacaoRepository.saveAndFlush(limitada);
        noCarrinho(acimaDoLimite, limitada, 3);
        assertThat(checkout.montarResumo(acimaDoLimite.getId(), retirada(MetodoPagamento.PIX)).orElseThrow().problemas())
                .anyMatch(p -> p.contains("limite"));

        Usuario comInativo = novoCliente();
        Apresentacao inativa = novaApresentacao("10.00", 5);
        inativa.setAtiva(false);
        inativa = apresentacaoRepository.saveAndFlush(inativa);
        noCarrinho(comInativo, inativa, 1);
        CheckoutForm form = retirada(MetodoPagamento.PIX);
        String conferencia = conferenciaDe(comInativo, form);
        assertThatThrownBy(() -> checkout.registrar(comInativo.getId(), form, conferencia, UUID.randomUUID()))
                .isInstanceOf(CheckoutException.class)
                .hasMessageContaining("não está mais à venda");
    }

    @Test
    @DisplayName("conferência que não bate com o recalculado é recusada: o navegador não define o resumo")
    void conferenciaAdulterada() {
        Usuario cliente = novoCliente();
        Apresentacao a = novaApresentacao("10.00", 5);
        noCarrinho(cliente, a, 1);

        assertThatThrownBy(() -> checkout.registrar(cliente.getId(), retirada(MetodoPagamento.PIX), "adulterada", UUID.randomUUID()))
                .isInstanceOf(CheckoutException.class);
        assertThat(estoqueDe(a)).isEqualTo(5);
        assertThat(itensNoCarrinho(cliente)).isEqualTo(1);
    }

    @Test
    @DisplayName("falha no meio do registro desfaz tudo: o primeiro item reservado volta e o carrinho fica intacto")
    void registroEhAtomico() {
        Usuario cliente = novoCliente();
        Apresentacao primeiro = novaApresentacao("10.00", 5);
        Apresentacao segundo = novaApresentacao("10.00", 5);
        noCarrinho(cliente, primeiro, 1);
        noCarrinho(cliente, segundo, 1);
        CheckoutForm form = retirada(MetodoPagamento.PIX);
        String conferencia = conferenciaDe(cliente, form);

        // O resumo foi calculado; o segundo item esgota antes da reserva dele.
        Apresentacao esgotado = apresentacaoRepository.findById(segundo.getId()).orElseThrow();
        esgotado.setEstoque(0);
        apresentacaoRepository.saveAndFlush(esgotado);

        assertThatThrownBy(() -> checkout.registrar(cliente.getId(), form, conferencia, UUID.randomUUID()))
                .isInstanceOf(CheckoutException.class);

        assertThat(estoqueDe(primeiro)).isEqualTo(5);
        assertThat(itensNoCarrinho(cliente)).isEqualTo(2);
    }

    // ---------- Concorrência ----------

    /** Solta todas as tarefas ao mesmo tempo, para elas disputarem de verdade. */
    private <T> List<Future<T>> juntos(List<Callable<T>> tarefas) throws InterruptedException {
        ExecutorService threads = Executors.newFixedThreadPool(tarefas.size());
        CountDownLatch largada = new CountDownLatch(1);
        List<Future<T>> resultados = new ArrayList<>();
        for (Callable<T> tarefa : tarefas) {
            resultados.add(threads.submit(() -> {
                largada.await();
                return tarefa.call();
            }));
        }
        largada.countDown();
        threads.shutdown();
        assertThat(threads.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        return resultados;
    }

    @Test
    @DisplayName("duas compras da última unidade: só uma é confirmada, e o estoque não fica negativo")
    void ultimaUnidade() throws Exception {
        Apresentacao ultima = novaApresentacao("10.00", 1);
        Usuario ana = novoCliente();
        Usuario bia = novoCliente();
        noCarrinho(ana, ultima, 1);
        noCarrinho(bia, ultima, 1);
        CheckoutForm form = retirada(MetodoPagamento.PIX);
        String conferencia = conferenciaDe(ana, form);

        List<Future<Long>> resultados = juntos(List.of(
                () -> checkout.registrar(ana.getId(), form, conferencia, UUID.randomUUID()),
                () -> checkout.registrar(bia.getId(), form, conferencia, UUID.randomUUID())));

        int confirmados = 0;
        int recusados = 0;
        for (Future<Long> resultado : resultados) {
            try {
                resultado.get();
                confirmados++;
            } catch (ExecutionException e) {
                assertThat(e.getCause()).isInstanceOf(CheckoutException.class);
                recusados++;
            }
        }
        assertThat(confirmados).isEqualTo(1);
        assertThat(recusados).isEqualTo(1);
        assertThat(estoqueDe(ultima)).isZero();
    }

    @Test
    @DisplayName("com estoque de sobra, compras simultâneas todas passam e o estoque fecha a conta")
    void compraSimultaneaComEstoque() throws Exception {
        Apresentacao a = novaApresentacao("10.00", 20);
        Apresentacao b = novaApresentacao("10.00", 20);
        List<Callable<Long>> tarefas = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            Usuario cliente = novoCliente();
            // Ordens invertidas de propósito: a reserva por id evita o deadlock.
            noCarrinho(cliente, i % 2 == 0 ? a : b, 1);
            noCarrinho(cliente, i % 2 == 0 ? b : a, 1);
            CheckoutForm form = retirada(MetodoPagamento.PIX);
            String conferencia = conferenciaDe(cliente, form);
            tarefas.add(() -> checkout.registrar(cliente.getId(), form, conferencia, UUID.randomUUID()));
        }

        for (Future<Long> resultado : juntos(tarefas)) {
            assertThat(resultado.get()).isNotNull();
        }
        assertThat(estoqueDe(a)).isEqualTo(14);
        assertThat(estoqueDe(b)).isEqualTo(14);
    }

    @Test
    @DisplayName("a reserva sobe a versão: a tela do admin aberta antes da compra não sobrescreve o estoque")
    void reservaSobeAVersao() {
        Usuario cliente = novoCliente();
        Apresentacao a = novaApresentacao("10.00", 5);
        Long versaoAntes = a.getVersao();
        noCarrinho(cliente, a, 1);

        comprar(cliente, retirada(MetodoPagamento.PIX), null);

        assertThat(apresentacaoRepository.findById(a.getId()).orElseThrow().getVersao()).isGreaterThan(versaoAntes);
    }

    // ---------- Envio repetido ----------

    @Test
    @DisplayName("mesmo envio repetido devolve o mesmo pedido, sem nova reserva nem nova cobrança")
    void envioRepetido() {
        Usuario cliente = novoCliente();
        Apresentacao a = novaApresentacao("10.00", 5);
        noCarrinho(cliente, a, 1);
        CheckoutForm form = retirada(MetodoPagamento.CARTAO_CREDITO);
        String conferencia = conferenciaDe(cliente, form);
        UUID chave = UUID.randomUUID();

        Long primeiro = pagamentos.confirmarCompra(cliente.getId(), form, conferencia, chave, cartao(CARTAO_APROVADO));
        Long repetido = pagamentos.confirmarCompra(cliente.getId(), form, conferencia, chave, cartao(CARTAO_APROVADO));

        assertThat(repetido).isEqualTo(primeiro);
        assertThat(estoqueDe(a)).isEqualTo(4);
        assertThat(vendidosDe(a)).isEqualTo(1);
        assertThat(pagamentoRepository.findByPedidoId(primeiro)).hasSize(1);
        assertThat(envio.mensagens().stream().filter(m -> m.assunto().endsWith("nº " + primeiro))).hasSize(1);
    }

    @Test
    @DisplayName("duplo clique de verdade (dois envios ao mesmo tempo): um pedido, uma reserva, uma aprovação")
    void duploCliqueSimultaneo() throws Exception {
        Usuario cliente = novoCliente();
        Apresentacao a = novaApresentacao("10.00", 5);
        noCarrinho(cliente, a, 1);
        CheckoutForm form = retirada(MetodoPagamento.CARTAO_CREDITO);
        String conferencia = conferenciaDe(cliente, form);
        UUID chave = UUID.randomUUID();
        Callable<Long> envio = () -> pagamentos.confirmarCompra(cliente.getId(), form, conferencia, chave, cartao(CARTAO_APROVADO));

        List<Future<Long>> resultados = juntos(List.of(envio, envio));

        assertThat(resultados.get(0).get()).isEqualTo(resultados.get(1).get());
        Long id = resultados.get(0).get();
        assertThat(estoqueDe(a)).isEqualTo(4);
        assertThat(vendidosDe(a)).isEqualTo(1);
        assertThat(pagamentoRepository.findByPedidoId(id)).extracting(Pagamento::getStatus)
                .containsExactly(StatusPagamento.APROVADO);
        assertThat(pedidoCompleto(id).getStatus()).isEqualTo(StatusPedido.PAGAMENTO_APROVADO);
    }

    @Test
    @DisplayName("histórico inicial: o pedido nasce aguardando pagamento, com autor sistema")
    void historicoInicial() {
        Usuario cliente = novoCliente();
        noCarrinho(cliente, novaApresentacao("10.00", 5), 1);

        Long id = comprar(cliente, retirada(MetodoPagamento.PIX), null);

        List<HistoricoStatusPedido> historico = historicoRepository.findByPedidoIdOrderByCriadoEmAsc(id);
        assertThat(historico).hasSize(1);
        assertThat(historico.get(0).getStatus()).isEqualTo(StatusPedido.AGUARDANDO_PAGAMENTO);
        assertThat(historico.get(0).getAlteradoPor()).isNull();
    }
}
