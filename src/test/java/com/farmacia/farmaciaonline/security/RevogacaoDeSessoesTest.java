package com.farmacia.farmaciaonline.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.session.SessionRegistryImpl;

import com.farmacia.farmaciaonline.model.Usuario;
import com.farmacia.farmaciaonline.model.enums.Perfil;
import com.farmacia.farmaciaonline.model.enums.StatusUsuario;

/**
 * Teste unitário puro: sem Spring e sem banco.
 */
class RevogacaoDeSessoesTest {

    private static UsuarioAutenticado principal(long id) {
        return UsuarioAutenticado.de(Usuario.builder().id(id).nome("Pessoa " + id).email("p" + id + "@exemplo.com")
                .dataNascimento(LocalDate.of(1990, 1, 1)).senhaHash("hash").perfil(Perfil.CLIENTE)
                .status(StatusUsuario.ATIVA).termosVersao("1.0").termosAceitosEm(Instant.now()).build());
    }

    @Test
    void encerraTodasAsSessoesDoUsuarioESoDele() {
        SessionRegistryImpl registro = new SessionRegistryImpl();
        registro.registerNewSession("navegador-1", principal(2));
        registro.registerNewSession("navegador-2", principal(2));
        registro.registerNewSession("outra-pessoa", principal(3));

        int encerradas = new RevogacaoDeSessoes(registro).encerrarSessoesDe(2L);

        assertThat(encerradas).isEqualTo(2);
        assertThat(registro.getSessionInformation("navegador-1").isExpired()).isTrue();
        assertThat(registro.getSessionInformation("navegador-2").isExpired()).isTrue();
        assertThat(registro.getSessionInformation("outra-pessoa").isExpired()).isFalse();
    }

    @Test
    void encerrarOutrasPoupaSoASessaoAtual() {
        SessionRegistryImpl registro = new SessionRegistryImpl();
        registro.registerNewSession("atual", principal(2));
        registro.registerNewSession("outro-navegador", principal(2));
        registro.registerNewSession("outra-pessoa", principal(3));

        int encerradas = new RevogacaoDeSessoes(registro).encerrarOutrasSessoesDe(2L, "atual");

        assertThat(encerradas).isEqualTo(1);
        assertThat(registro.getSessionInformation("atual").isExpired()).isFalse();
        assertThat(registro.getSessionInformation("outro-navegador").isExpired()).isTrue();
        assertThat(registro.getSessionInformation("outra-pessoa").isExpired()).isFalse();
    }

    @Test
    void usuarioSemSessaoNaoEncerraNada() {
        SessionRegistryImpl registro = new SessionRegistryImpl();
        registro.registerNewSession("outra-pessoa", principal(3));

        assertThat(new RevogacaoDeSessoes(registro).encerrarSessoesDe(2L)).isZero();
    }
}
