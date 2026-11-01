package com.farmacia.farmaciaonline.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.farmacia.farmaciaonline.TestcontainersConfiguration;
import com.farmacia.farmaciaonline.model.ItemPedido;
import com.farmacia.farmaciaonline.model.LogAuditoria;
import com.farmacia.farmaciaonline.model.Pedido;
import com.farmacia.farmaciaonline.model.Usuario;
import com.farmacia.farmaciaonline.model.enums.Genero;
import com.farmacia.farmaciaonline.model.enums.Perfil;
import com.farmacia.farmaciaonline.model.enums.StatusUsuario;
import com.farmacia.farmaciaonline.model.enums.TipoEntrega;
import com.farmacia.farmaciaonline.repository.ApresentacaoRepository;
import com.farmacia.farmaciaonline.repository.LogAuditoriaRepository;
import com.farmacia.farmaciaonline.repository.LojaRepository;
import com.farmacia.farmaciaonline.repository.PedidoRepository;
import com.farmacia.farmaciaonline.repository.UsuarioRepository;

/**
 * UC11 contra o PostgreSQL real. O seed e os testes do checkout deixam dados no banco: nada aqui assume contagem
 * global; cada busca usa um termo único e a auditoria compara a contagem antes e depois.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class ClienteAdminServiceIntegracaoTest {

    private static final String IP = "203.0.113.7";
    private static final Pageable PAGINA = PageRequest.of(0, 20, Sort.by("nome"));

    @Autowired
    private ClienteAdminService service;

    @Autowired
    private UsuarioRepository usuarios;

    @Autowired
    private PedidoRepository pedidos;

    @Autowired
    private LogAuditoriaRepository auditoria;

    @Autowired
    private ApresentacaoRepository apresentacoes;

    @Autowired
    private LojaRepository lojas;

    private Long adminId;
    private int sequencia;

    @BeforeEach
    void admin() {
        adminId = usuarios.findByEmailIgnoreCase("admin@farma.com").orElseThrow().getId();
    }

    private Usuario conta(String nome, String email, String cpf, StatusUsuario status, Perfil perfil) {
        sequencia++;
        return usuarios.saveAndFlush(Usuario.builder()
                .nome(nome)
                .email(email)
                .cpf(cpf)
                .celular("11987654321")
                .dataNascimento(LocalDate.of(1990, 5, 17))
                .genero(Genero.FEMININO)
                .senhaHash("$2a$10$hash-secreto-que-nao-pode-vazar-" + sequencia)
                .perfil(perfil)
                .status(status)
                .excluidoEm(status == StatusUsuario.EXCLUIDA ? Instant.now() : null)
                .termosVersao("1.0")
                .termosAceitosEm(Instant.now())
                .build());
    }

    private Usuario cliente(String nome, String email, String cpf) {
        return conta(nome, email, cpf, StatusUsuario.ATIVA, Perfil.CLIENTE);
    }

    private Page<ClienteResumo> buscar(String q) {
        return service.listar(adminId, IP, q, null, PAGINA);
    }

    private List<String> nomes(Page<ClienteResumo> pagina) {
        return pagina.getContent().stream().map(ClienteResumo::getNome).toList();
    }

    private LogAuditoria ultimoLog() {
        return auditoria.findAll(Sort.by(Sort.Direction.DESC, "id")).get(0);
    }

    private Pedido pedido(Usuario dono) {
        ItemPedido item = new ItemPedido();
        var apresentacao = apresentacoes.findBySku("NOV-DIP1G-20").orElseThrow();
        Pedido pedido = new Pedido();
        pedido.setUsuario(dono);
        pedido.setTipoEntrega(TipoEntrega.RETIRADA);
        pedido.setLojaRetirada(lojas.findByAtivaTrue().get(0));
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
        return pedidos.saveAndFlush(pedido);
    }

    @Test
    @DisplayName("busca por nome, sem diferenciar maiúsculas")
    void buscaPorNome() {
        cliente("Zeferino Quimera", "zef@teste-f09.com", "90000000001");

        assertThat(nomes(buscar("QUIMERA"))).containsExactly("Zeferino Quimera");
    }

    @Test
    @DisplayName("busca por e-mail")
    void buscaPorEmail() {
        cliente("Maria Unica", "maria.unica.f09@teste.com", "90000000002");

        assertThat(nomes(buscar("Maria.Unica.F09"))).containsExactly("Maria Unica");
    }

    @Test
    @DisplayName("busca por CPF, com e sem pontuação, e por pedaço com 3 dígitos ou mais")
    void buscaPorCpf() {
        cliente("Cpf Procurado", "cpf.procurado@teste.com", "98765432100");

        assertThat(nomes(buscar("98765432100"))).containsExactly("Cpf Procurado");
        assertThat(nomes(buscar("987.654.321-00"))).containsExactly("Cpf Procurado");
        assertThat(nomes(buscar("654321"))).containsExactly("Cpf Procurado");
    }

    @Test
    @DisplayName("pedaço de CPF com menos de 3 dígitos não vira busca por CPF")
    void poucosDigitosNaoBuscamCpf() {
        cliente("Digitos Curtos", "digitos.curtos@teste.com", "98700000099");

        assertThat(nomes(buscar("98"))).doesNotContain("Digitos Curtos");
    }

    @Test
    @DisplayName("filtro de status separa pendente, ativa e excluída")
    void filtroDeStatus() {
        conta("Statusx Pendente", "sx.pendente@teste.com", "90000000011", StatusUsuario.PENDENTE, Perfil.CLIENTE);
        conta("Statusx Ativa", "sx.ativa@teste.com", "90000000012", StatusUsuario.ATIVA, Perfil.CLIENTE);
        conta("Statusx Excluida", "sx.excluida@teste.com", "90000000013", StatusUsuario.EXCLUIDA, Perfil.CLIENTE);

        assertThat(nomes(service.listar(adminId, IP, "statusx", StatusUsuario.PENDENTE, PAGINA)))
                .containsExactly("Statusx Pendente");
        assertThat(nomes(service.listar(adminId, IP, "statusx", StatusUsuario.ATIVA, PAGINA)))
                .containsExactly("Statusx Ativa");
        assertThat(nomes(service.listar(adminId, IP, "statusx", StatusUsuario.EXCLUIDA, PAGINA)))
                .containsExactly("Statusx Excluida");
        assertThat(nomes(buscar("statusx"))).containsExactlyInAnyOrder("Statusx Pendente", "Statusx Ativa",
                "Statusx Excluida");
    }

    @Test
    @DisplayName("administrador nunca aparece na lista de clientes")
    void soContasCliente() {
        conta("Perfil Admin Extra", "perfil.admin.extra@teste.com", "90000000021", StatusUsuario.ATIVA, Perfil.ADMIN);

        assertThat(buscar("perfil admin extra").getTotalElements()).isZero();
        assertThat(buscar("admin@farma.com").getTotalElements()).isZero();
        assertThat(buscar("cliente@farma.com").getContent()).extracting(ClienteResumo::getEmail)
                .containsExactly("cliente@farma.com");
    }

    @Test
    @DisplayName("lista paginada e ordenada por nome")
    void paginacao() {
        cliente("Pagtest C", "pag.c@teste.com", "90000000033");
        cliente("Pagtest A", "pag.a@teste.com", "90000000031");
        cliente("Pagtest B", "pag.b@teste.com", "90000000032");

        Page<ClienteResumo> primeira = service.listar(adminId, IP, "pagtest", null, PageRequest.of(0, 2, Sort.by("nome")));
        Page<ClienteResumo> segunda = service.listar(adminId, IP, "pagtest", null, PageRequest.of(1, 2, Sort.by("nome")));

        assertThat(primeira.getTotalElements()).isEqualTo(3);
        assertThat(primeira.getTotalPages()).isEqualTo(2);
        assertThat(nomes(primeira)).containsExactly("Pagtest A", "Pagtest B");
        assertThat(nomes(segunda)).containsExactly("Pagtest C");
    }

    @Test
    @DisplayName("%, _ e ! digitados são texto comum, não curinga")
    void caracteresEspeciaisDaBusca() {
        cliente("Abc_Def", "abc.under@teste.com", "90000000041");
        cliente("AbcXDef", "abc.xis@teste.com", "90000000042");
        cliente("Cem%Porcento", "cem.pct@teste.com", "90000000043");
        cliente("Ola!Mundo", "ola.exclama@teste.com", "90000000044");

        assertThat(nomes(buscar("c_d"))).containsExactly("Abc_Def");
        assertThat(nomes(buscar("%"))).containsExactly("Cem%Porcento");
        assertThat(nomes(buscar("a!m"))).containsExactly("Ola!Mundo");
    }

    @Test
    @DisplayName("resumo da lista mascara o CPF e não carrega senha")
    void resumoMascaraCpf() {
        cliente("Resumo Mascarado", "resumo.mascarado@teste.com", "52998224799");

        ClienteResumo resumo = buscar("resumo mascarado").getContent().get(0);

        assertThat(resumo.getCpfMascarado()).isEqualTo("***.982.247-**");
        assertThat(resumo.toString()).doesNotContain("52998224799").doesNotContain("hash-secreto");
        assertThat(nomesDosCampos(ClienteResumo.class)).noneMatch(n -> n.toLowerCase().contains("senha")
                || n.toLowerCase().contains("hash"));
    }

    @Test
    @DisplayName("detalhe traz os dados da conta e nada de senha, hash ou códigos")
    void detalheSemSegredos() {
        Usuario cliente = cliente("Detalhe Completo", "detalhe.completo@teste.com", "52998224788");

        ClienteDetalhe detalhe = service.detalhar(adminId, IP, cliente.getId());

        assertThat(detalhe.getNome()).isEqualTo("Detalhe Completo");
        assertThat(detalhe.getCpfFormatado()).isEqualTo("529.982.247-88");
        assertThat(detalhe.getCelularFormatado()).isEqualTo("(11) 98765-4321");
        assertThat(detalhe.isEmailConfirmado()).isFalse();
        assertThat(detalhe.getStatusRotulo()).isEqualTo("Ativa");
        assertThat(detalhe.toString()).doesNotContain("hash-secreto").doesNotContain("$2a$");
        assertThat(nomesDosCampos(ClienteDetalhe.class)).noneMatch(n -> {
            String minusculo = n.toLowerCase();
            return minusculo.contains("senha") || minusculo.contains("hash") || minusculo.contains("termos")
                    || minusculo.contains("tentativas") || minusculo.contains("codigo");
        });
    }

    @Test
    @DisplayName("e-mail do seed aparece como confirmado no detalhe (V3)")
    void detalheMostraContatoConfirmado() {
        Usuario seed = usuarios.findByEmailIgnoreCase("cliente@farma.com").orElseThrow();

        ClienteDetalhe detalhe = service.detalhar(adminId, IP, seed.getId());

        assertThat(detalhe.isEmailConfirmado()).isTrue();
    }

    @Test
    @DisplayName("listar grava CLIENTE_LISTAR com autor e IP, sem o texto buscado")
    void listarGravaAuditoria() {
        long antes = auditoria.count();

        service.listar(adminId, IP, "Segredo Buscado", StatusUsuario.ATIVA, PageRequest.of(2, 20, Sort.by("nome")));

        assertThat(auditoria.count()).isEqualTo(antes + 1);
        LogAuditoria log = ultimoLog();
        assertThat(log.getAcao()).isEqualTo("CLIENTE_LISTAR");
        assertThat(log.getUsuario().getId()).isEqualTo(adminId);
        assertThat(log.getIp()).isEqualTo(IP);
        assertThat(log.getCriadoEm()).isNotNull();
        assertThat(log.getAlvoTipo()).isNull();
        assertThat(log.getAlvoId()).isNull();
        assertThat(log.getDetalhes()).isEqualTo("status=ATIVA, busca=sim, pagina=2")
                .doesNotContainIgnoringCase("Segredo");
    }

    @Test
    @DisplayName("listar sem busca registra busca=nao e status TODOS")
    void listarSemBuscaGravaDetalhesNeutros() {
        service.listar(adminId, IP, "  ", null, PAGINA);

        assertThat(ultimoLog().getDetalhes()).isEqualTo("status=TODOS, busca=nao, pagina=0");
    }

    @Test
    @DisplayName("buscar por CPF ou e-mail não deixa CPF nem e-mail no log")
    void logNaoTemCpfNemEmail() {
        cliente("Log Sem Cpf", "log.sem.cpf@teste.com", "52998224711");

        service.listar(adminId, IP, "529.982.247-11", null, PAGINA);
        assertThat(ultimoLog().getDetalhes()).doesNotContain("52998224711").doesNotContain("529.982");

        service.listar(adminId, IP, "log.sem.cpf@teste.com", null, PAGINA);
        assertThat(ultimoLog().getDetalhes()).doesNotContain("log.sem.cpf");

        Usuario alvo = usuarios.findByCpf("52998224711").orElseThrow();
        service.detalhar(adminId, IP, alvo.getId());
        LogAuditoria visualizacao = ultimoLog();
        assertThat(String.valueOf(visualizacao.getDetalhes())).doesNotContain("52998224711");
    }

    @Test
    @DisplayName("detalhar grava CLIENTE_VISUALIZAR com autor, alvo, IP e data")
    void detalharGravaAuditoria() {
        Usuario alvo = cliente("Alvo Auditado", "alvo.auditado@teste.com", "90000000051");
        long antes = auditoria.count();

        service.detalhar(adminId, IP, alvo.getId());

        assertThat(auditoria.count()).isEqualTo(antes + 1);
        LogAuditoria log = ultimoLog();
        assertThat(log.getAcao()).isEqualTo("CLIENTE_VISUALIZAR");
        assertThat(log.getUsuario().getId()).isEqualTo(adminId);
        assertThat(log.getAlvoTipo()).isEqualTo("USUARIO");
        assertThat(log.getAlvoId()).isEqualTo(alvo.getId());
        assertThat(log.getIp()).isEqualTo(IP);
        assertThat(log.getCriadoEm()).isNotNull();
    }

    @Test
    @DisplayName("detalhar um administrador responde 404 e não grava log")
    void detalharAdminDa404SemLog() {
        long antes = auditoria.count();

        assertThatThrownBy(() -> service.detalhar(adminId, IP, adminId))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        assertThat(auditoria.count()).isEqualTo(antes);
    }

    @Test
    @DisplayName("detalhar id inexistente responde 404 e não grava log")
    void detalharInexistenteDa404SemLog() {
        long antes = auditoria.count();

        assertThatThrownBy(() -> service.detalhar(adminId, IP, 999_999_999L))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        assertThat(auditoria.count()).isEqualTo(antes);
    }

    @Test
    @DisplayName("pedidos recentes: só do cliente, no máximo 10, do mais novo para o mais antigo")
    void pedidosRecentes() {
        Usuario dono = cliente("Dono Pedidos", "dono.pedidos@teste.com", "90000000061");
        Usuario outro = cliente("Outro Dono", "outro.dono@teste.com", "90000000062");
        for (int i = 0; i < 11; i++) {
            pedido(dono);
        }
        Pedido alheio = pedido(outro);

        List<ClienteDetalhe.PedidoResumo> recentes = service.detalhar(adminId, IP, dono.getId()).getPedidosRecentes();

        assertThat(recentes).hasSize(10);
        assertThat(recentes).extracting(ClienteDetalhe.PedidoResumo::getId).doesNotContain(alheio.getId());
        assertThat(recentes).extracting(ClienteDetalhe.PedidoResumo::getId).isSortedAccordingTo(
                (a, b) -> Long.compare(b, a));
        assertThat(recentes.get(0).getStatusRotulo()).isEqualTo("Aguardando pagamento");
        assertThat(recentes.get(0).getTotal()).isPositive();
    }

    @Test
    @DisplayName("cliente sem pedidos vem com a lista vazia")
    void semPedidos() {
        Usuario sem = cliente("Sem Pedidos", "sem.pedidos@teste.com", "90000000071");

        assertThat(service.detalhar(adminId, IP, sem.getId()).getPedidosRecentes()).isEmpty();
    }

    @Test
    @DisplayName("se o log não puder ser gravado, a consulta falha")
    void consultaFalhaSeOLogFalhar() {
        LogAuditoriaRepository quebrado = mock(LogAuditoriaRepository.class);
        when(quebrado.save(any())).thenThrow(new IllegalStateException("log indisponível"));
        ClienteAdminService semLog = new ClienteAdminService(usuarios, pedidos, quebrado);
        Usuario alvo = cliente("Log Quebrado", "log.quebrado@teste.com", "90000000081");

        assertThatThrownBy(() -> semLog.listar(adminId, IP, "quebrado", null, PAGINA))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> semLog.detalhar(adminId, IP, alvo.getId()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("sem administrador identificado não há consulta")
    void semAutorNaoConsulta() {
        long antes = auditoria.count();

        assertThatThrownBy(() -> service.listar(null, IP, null, null, PAGINA))
                .isInstanceOf(IllegalStateException.class);
        assertThat(auditoria.count()).isEqualTo(antes);
    }

    private static List<String> nomesDosCampos(Class<?> tipo) {
        return Arrays.stream(tipo.getDeclaredFields()).map(Field::getName).toList();
    }
}
