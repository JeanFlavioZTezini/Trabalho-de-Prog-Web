package com.farmacia.farmaciaonline.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.farmacia.farmaciaonline.model.enums.Genero;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

class ContaFormsTest {

    private static ValidatorFactory fabrica;
    private static Validator validator;

    @BeforeAll
    static void abrir() {
        fabrica = Validation.buildDefaultValidatorFactory();
        validator = fabrica.getValidator();
    }

    @AfterAll
    static void fechar() {
        fabrica.close();
    }

    private static ContaForm conta() {
        ContaForm form = new ContaForm();
        form.setNome("Maria da Silva");
        form.setDataNascimento(LocalDate.of(1990, 3, 15));
        form.setGenero(Genero.FEMININO);
        return form;
    }

    private static SenhaForm senha() {
        SenhaForm form = new SenhaForm();
        form.setSenhaAtual("Farma@123");
        form.setNovaSenha("NovaSenha1");
        form.setConfirmarNovaSenha("NovaSenha1");
        return form;
    }

    private static <T> Set<String> camposComErro(T form) {
        return validator.validate(form).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(Collectors.toSet());
    }

    @Test
    void contaValidaNaoTemErros() {
        assertThat(validator.validate(conta())).isEmpty();
    }

    @Test
    void contaExigeNomeNascimentoEGenero() {
        assertThat(camposComErro(new ContaForm())).containsExactlyInAnyOrder("nome", "dataNascimento", "genero");
    }

    @Test
    void nomeEmBrancoOuMuitoLongoEhRecusado() {
        ContaForm branco = conta();
        branco.setNome("   ");
        ContaForm longo = conta();
        longo.setNome("a".repeat(151));

        assertThat(camposComErro(branco)).containsExactly("nome");
        assertThat(camposComErro(longo)).containsExactly("nome");
    }

    @Test
    void senhaValidaNaoTemErros() {
        assertThat(validator.validate(senha())).isEmpty();
    }

    @Test
    void senhaExigeOsTresCampos() {
        assertThat(camposComErro(new SenhaForm()))
                .containsExactlyInAnyOrder("senhaAtual", "novaSenha", "confirmarNovaSenha");
    }

    @ParameterizedTest
    @ValueSource(strings = {"curta1", "somenteletras", "123456789"})
    void novaSenhaForaDaPoliticaEhRecusada(String invalida) {
        SenhaForm form = senha();
        form.setNovaSenha(invalida);

        assertThat(camposComErro(form)).containsExactly("novaSenha");
    }
}
