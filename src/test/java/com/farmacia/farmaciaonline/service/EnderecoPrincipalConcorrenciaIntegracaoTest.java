package com.farmacia.farmaciaonline.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.farmacia.farmaciaonline.TestcontainersConfiguration;
import com.farmacia.farmaciaonline.model.EnderecoForm;
import com.farmacia.farmaciaonline.model.Usuario;
import com.farmacia.farmaciaonline.model.enums.Perfil;
import com.farmacia.farmaciaonline.model.enums.StatusUsuario;
import com.farmacia.farmaciaonline.repository.UsuarioRepository;

/**
 * Duas gravações simultâneas do primeiro endereço não podem violar o índice ux_endereco_principal (antes viravam
 * erro 500). Fora da transação do teste: cada thread grava na própria transação; a conta criada é apagada no fim.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class EnderecoPrincipalConcorrenciaIntegracaoTest {

    @Autowired private EnderecoService enderecos;
    @Autowired private UsuarioRepository usuarios;
    @Autowired private JdbcTemplate jdbc;

    private Long criada;

    @AfterEach
    void limpar() {
        if (criada != null) {
            jdbc.update("DELETE FROM endereco WHERE usuario_id = ?", criada);
            jdbc.update("DELETE FROM usuario WHERE id = ?", criada);
        }
    }

    private static EnderecoForm endereco(String numero) {
        EnderecoForm form = new EnderecoForm();
        form.setDestinatario("Destinatário");
        form.setCep("79002-000");
        form.setLogradouro("Avenida Teste");
        form.setNumero(numero);
        form.setBairro("Centro");
        form.setCidade("Campo Grande");
        form.setUf("MS");
        return form;
    }

    @Test
    @DisplayName("dois primeiros endereços salvos ao mesmo tempo: os dois ficam gravados e só um é o principal")
    void primeiroEnderecoSimultaneo() throws Exception {
        long n = ThreadLocalRandom.current().nextLong(1_000_000_000L, 9_999_999_999L);
        Usuario cliente = usuarios.saveAndFlush(Usuario.builder()
                .nome("Endereço Concorrente").email("endereco" + n + "@teste.com").cpf("8" + n)
                .dataNascimento(LocalDate.of(1990, 1, 1)).senhaHash("hash").perfil(Perfil.CLIENTE)
                .status(StatusUsuario.ATIVA).termosVersao("1.0").termosAceitosEm(Instant.now()).build());
        criada = cliente.getId();

        ExecutorService threads = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch largada = new CountDownLatch(1);
            Callable<Long> salvarA = () -> { largada.await(); return enderecos.salvar(criada, null, endereco("1")); };
            Callable<Long> salvarB = () -> { largada.await(); return enderecos.salvar(criada, null, endereco("2")); };
            Future<Long> a = threads.submit(salvarA);
            Future<Long> b = threads.submit(salvarB);
            largada.countDown();
            a.get(10, TimeUnit.SECONDS);
            b.get(10, TimeUnit.SECONDS);
        } finally {
            threads.shutdownNow();
        }

        Integer total = jdbc.queryForObject("SELECT count(*) FROM endereco WHERE usuario_id = ?", Integer.class, criada);
        Integer principais = jdbc.queryForObject("SELECT count(*) FROM endereco WHERE usuario_id = ? AND principal",
                Integer.class, criada);
        assertThat(total).isEqualTo(2);
        assertThat(principais).isEqualTo(1);
    }
}
