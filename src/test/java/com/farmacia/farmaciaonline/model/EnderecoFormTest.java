package com.farmacia.farmaciaonline.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

class EnderecoFormTest {

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

    private static EnderecoForm valido() {
        EnderecoForm form = new EnderecoForm();
        form.setDestinatario("Maria da Silva");
        form.setCep("01310-100");
        form.setLogradouro("Avenida Paulista");
        form.setNumero("1000");
        form.setBairro("Bela Vista");
        form.setCidade("São Paulo");
        form.setUf("SP");
        return form;
    }

    private static Set<String> camposComErro(EnderecoForm form) {
        return validator.validate(form).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(Collectors.toSet());
    }

    @Test
    void formularioCompletoEhValido() {
        assertThat(camposComErro(valido())).isEmpty();
    }

    @Test
    void complementoEReferenciaSaoOpcionais() {
        EnderecoForm form = valido();
        form.setComplemento(null);
        form.setReferencia(null);

        assertThat(camposComErro(form)).isEmpty();
    }

    @Test
    void camposObrigatoriosVaziosSaoRecusados() {
        assertThat(camposComErro(new EnderecoForm()))
                .containsExactlyInAnyOrder("destinatario", "cep", "logradouro", "numero", "bairro", "cidade", "uf");
    }

    @Test
    void camposObrigatoriosSoComEspacosSaoRecusados() {
        EnderecoForm form = valido();
        form.setLogradouro("   ");
        form.setNumero("   ");

        assertThat(camposComErro(form)).containsExactlyInAnyOrder("logradouro", "numero");
    }

    @ParameterizedTest
    @ValueSource(strings = {"01310100", "01310-100", "  01310-100  "})
    void cepComOitoDigitosEhAceito(String cep) {
        EnderecoForm form = valido();
        form.setCep(cep);

        assertThat(camposComErro(form)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"0131010", "013101000", "01310-10", "0131-0100", "abcdefgh", "01.310-100"})
    void cepInvalidoEhRecusado(String cep) {
        EnderecoForm form = valido();
        form.setCep(cep);

        assertThat(camposComErro(form)).containsExactly("cep");
    }

    @Test
    void todaUfDaListaEhAceitaEFalsaNao() {
        for (String uf : EnderecoForm.UFS) {
            EnderecoForm form = valido();
            form.setUf(uf);
            assertThat(camposComErro(form)).as(uf).isEmpty();
        }
        assertThat(EnderecoForm.UFS).hasSize(27);

        EnderecoForm form = valido();
        form.setUf("XX");
        assertThat(camposComErro(form)).containsExactly("uf");
    }

    @Test
    void ufEmMinusculasEhNormalizada() {
        EnderecoForm form = valido();
        form.setUf(" sp ");

        assertThat(form.getUf()).isEqualTo("SP");
        assertThat(camposComErro(form)).isEmpty();
    }

    @Test
    void textosAcimaDoLimiteDaColunaSaoRecusados() {
        EnderecoForm form = valido();
        form.setDestinatario("a".repeat(151));
        form.setLogradouro("a".repeat(201));
        form.setNumero("1".repeat(21));
        form.setComplemento("a".repeat(101));
        form.setBairro("a".repeat(101));
        form.setCidade("a".repeat(101));
        form.setReferencia("a".repeat(201));

        assertThat(camposComErro(form)).containsExactlyInAnyOrder(
                "destinatario", "logradouro", "numero", "complemento", "bairro", "cidade", "referencia");
    }
}
