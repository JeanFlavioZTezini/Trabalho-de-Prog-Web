package com.farmacia.farmaciaonline.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.beans.PropertyDescriptor;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.BeanUtils;

import com.farmacia.farmaciaonline.model.enums.Genero;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

/**
 * Teste unitário do Bean Validation do formulário de cadastro: sem Spring e sem banco.
 */
class CadastroFormTest {

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

    private static CadastroForm valido() {
        CadastroForm form = new CadastroForm();
        form.setNome("Maria da Silva");
        form.setCpf("529.982.247-25");
        form.setDataNascimento(LocalDate.of(1995, 5, 20));
        form.setGenero(Genero.FEMININO);
        form.setCelular("(67) 99999-8888");
        form.setEmail("maria@exemplo.com");
        form.setSenha("Senha123");
        form.setConfirmarSenha("Senha123");
        form.setAceiteTermos(true);
        return form;
    }

    private static Set<String> propriedadesComErro(CadastroForm form) {
        return validator.validate(form).stream().map(v -> v.getPropertyPath().toString()).collect(Collectors.toSet());
    }

    private static Set<String> mensagens(CadastroForm form, String propriedade) {
        return validator.validate(form).stream()
                .filter(v -> v.getPropertyPath().toString().equals(propriedade))
                .map(ConstraintViolation::getMessage).collect(Collectors.toSet());
    }

    private static CadastroForm com(Consumer<CadastroForm> ajuste) {
        CadastroForm form = valido();
        ajuste.accept(form);
        return form;
    }

    @Test
    void formularioValidoNaoTemErro() {
        assertThat(validator.validate(valido())).isEmpty();
    }

    @ParameterizedTest(name = "celular \"{0}\" é aceito")
    @ValueSource(strings = {"(67) 99999-8888", "67999998888", "(67)999998888", "67 99999-8888", "(11) 98765-4321"})
    void aceitaCelularComOuSemMascara(String celular) {
        assertThat(propriedadesComErro(com(f -> f.setCelular(celular)))).doesNotContain("celular");
    }

    @ParameterizedTest(name = "celular \"{0}\" é recusado")
    @ValueSource(strings = {"(67) 3333-4444", "(67) 9999-8888", "(07) 99999-8888", "(67) 89999-8888", "999998888",
            "(67) 99999-88889", "abc"})
    void recusaCelularInvalido(String celular) {
        assertThat(propriedadesComErro(com(f -> f.setCelular(celular)))).containsExactly("celular");
    }

    @ParameterizedTest(name = "e-mail \"{0}\" é recusado")
    @ValueSource(strings = {"maria", "maria@", "maria@dominio", "@dominio.com", "ma ria@dominio.com", "maria@@dominio.com"})
    void recusaEmailInvalido(String email) {
        assertThat(propriedadesComErro(com(f -> f.setEmail(email)))).containsExactly("email");
    }

    @ParameterizedTest(name = "senha \"{0}\" é recusada")
    @ValueSource(strings = {"abcdefgh", "12345678", "Ab1", "Senha12", "senha com espaço so"})
    void recusaSenhaFraca(String senha) {
        assertThat(propriedadesComErro(com(f -> f.setSenha(senha)))).containsExactly("senha");
    }

    @ParameterizedTest(name = "senha \"{0}\" é aceita")
    @ValueSource(strings = {"Senha123", "abcdefg1", "12345a78", "Açaí2024!", "uma senha longa 123"})
    void aceitaSenhaComOitoCaracteresLetraENumero(String senha) {
        assertThat(propriedadesComErro(com(f -> f.setSenha(senha)))).doesNotContain("senha");
    }

    @Test
    void cpfComDigitoVerificadorErradoEhRecusado() {
        CadastroForm form = com(f -> f.setCpf("529.982.247-24"));

        assertThat(mensagens(form, "cpf")).containsExactly("CPF inválido: confira os 11 números.");
    }

    @Test
    void camposObrigatoriosVaziosGeramUmaMensagemCada() {
        CadastroForm vazio = new CadastroForm();
        vazio.setNome(" ");
        vazio.setCpf("");
        vazio.setCelular("");
        vazio.setEmail("");
        vazio.setSenha("");
        vazio.setConfirmarSenha("");

        assertThat(propriedadesComErro(vazio)).containsExactlyInAnyOrder("nome", "cpf", "dataNascimento", "genero",
                "celular", "email", "senha", "confirmarSenha", "aceiteTermos");
        assertThat(mensagens(vazio, "senha")).containsExactly("Crie uma senha.");
        assertThat(mensagens(vazio, "celular")).containsExactly("Informe o celular.");
        assertThat(mensagens(vazio, "cpf")).containsExactly("Informe o CPF.");
    }

    @Test
    void semAceiteDosTermosNaoCria() {
        assertThat(mensagens(com(f -> f.setAceiteTermos(false)), "aceiteTermos"))
                .containsExactly("É preciso aceitar os termos para criar a conta.");
    }

    @Test
    void naoTemCampoParaPerfilStatusNemTermos() {
        // O que não existe no formulário o Spring não consegue preencher com dados da requisição.
        Set<String> campos = Arrays.stream(BeanUtils.getPropertyDescriptors(CadastroForm.class))
                .map(PropertyDescriptor::getName).collect(Collectors.toSet());

        assertThat(campos).doesNotContain("perfil", "status", "id", "termosVersao", "termosAceitosEm", "senhaHash",
                "tentativasLoginFalhas", "bloqueadoAte", "excluidoEm");
    }
}
