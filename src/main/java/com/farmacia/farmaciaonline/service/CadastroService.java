package com.farmacia.farmaciaonline.service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.farmacia.farmaciaonline.model.CadastroForm;
import com.farmacia.farmaciaonline.model.Usuario;
import com.farmacia.farmaciaonline.model.enums.Perfil;
import com.farmacia.farmaciaonline.model.enums.StatusUsuario;
import com.farmacia.farmaciaonline.repository.UsuarioRepository;
import com.farmacia.farmaciaonline.validacao.CpfValidator;

/**
 * Cadastro de cliente (UC03). A conta nasce PENDENTE e com perfil CLIENTE, sempre: nada disso vem do navegador.
 * A confirmação do contato (F-05) é que a torna ATIVA.
 */
@Service
public class CadastroService {

    static final int IDADE_MINIMA = 18;
    /** O BCrypt só lê os primeiros 72 bytes da senha; mais que isso o Spring recusa. */
    static final int LIMITE_BYTES_SENHA = 72;
    private static final LocalDate NASCIMENTO_MAIS_ANTIGO = LocalDate.of(1900, 1, 1);

    private static final String MENSAGEM_CPF_USADO = "Já existe uma conta com este CPF.";
    private static final String MENSAGEM_EMAIL_USADO = "Já existe uma conta com este e-mail.";

    private final UsuarioRepository usuarios;
    private final PasswordEncoder encoder;
    private final String termosVersao;

    public CadastroService(UsuarioRepository usuarios, PasswordEncoder encoder,
            @Value("${app.termos.versao:1.0}") String termosVersao) {
        this.usuarios = usuarios;
        this.encoder = encoder;
        this.termosVersao = termosVersao;
    }

    /**
     * Regras que o Bean Validation não cobre: senhas iguais, data e 18 anos, CPF e e-mail ainda sem conta.
     * Devolve campo -> mensagem (vazio = tudo certo). As chaves {@code cpf} e {@code email} só aparecem por duplicidade.
     */
    @Transactional(readOnly = true)
    public Map<String, String> verificar(CadastroForm form) {
        Map<String, String> erros = new LinkedHashMap<>();

        String senha = form.getSenha();
        if (senha != null && form.getConfirmarSenha() != null && !senha.equals(form.getConfirmarSenha())) {
            erros.put("confirmarSenha", "As senhas não conferem.");
        }
        if (senha != null && senha.getBytes(StandardCharsets.UTF_8).length > LIMITE_BYTES_SENHA) {
            erros.put("senha", "A senha é longa demais: use até 72 caracteres (letras com acento contam mais).");
        }

        LocalDate nascimento = form.getDataNascimento();
        if (nascimento != null) {
            LocalDate hoje = LocalDate.now();
            if (nascimento.isBefore(NASCIMENTO_MAIS_ANTIGO) || nascimento.isAfter(hoje)) {
                erros.put("dataNascimento", "Informe uma data de nascimento válida.");
            } else if (Period.between(nascimento, hoje).getYears() < IDADE_MINIMA) {
                erros.put("dataNascimento", "É preciso ter 18 anos ou mais para criar uma conta.");
            }
        }

        if (CpfValidator.valido(form.getCpf()) && usuarios.existsByCpf(soDigitos(form.getCpf()))) {
            erros.put("cpf", MENSAGEM_CPF_USADO);
        }
        String email = emailDe(form);
        if (!email.isEmpty() && usuarios.existsByEmailIgnoreCase(email)) {
            erros.put("email", MENSAGEM_EMAIL_USADO);
        }
        return erros;
    }

    @Transactional
    public Usuario cadastrar(CadastroForm form) {
        Map<String, String> erros = verificar(form);
        if (!erros.isEmpty()) {
            throw new CadastroInvalidoException(erros);
        }

        Usuario usuario = Usuario.builder()
                .nome(form.getNome().trim().replaceAll("\\s+", " "))
                .cpf(soDigitos(form.getCpf()))
                .dataNascimento(form.getDataNascimento())
                .genero(form.getGenero())
                .celular(soDigitos(form.getCelular()))
                .email(emailDe(form).toLowerCase(Locale.ROOT))
                .senhaHash(encoder.encode(form.getSenha()))
                .perfil(Perfil.CLIENTE)
                .status(StatusUsuario.PENDENTE)
                .termosVersao(termosVersao)
                .termosAceitosEm(Instant.now())
                .build();
        try {
            return usuarios.saveAndFlush(usuario);
        } catch (DataIntegrityViolationException e) {
            // Outro cadastro igual entrou entre a verificação e a gravação: o banco é quem garante a unicidade.
            String causa = String.valueOf(e.getMostSpecificCause().getMessage());
            if (causa.contains("ux_usuario_email")) {
                throw new CadastroInvalidoException(Map.of("email", MENSAGEM_EMAIL_USADO));
            }
            if (causa.contains("uq_usuario_cpf")) {
                throw new CadastroInvalidoException(Map.of("cpf", MENSAGEM_CPF_USADO));
            }
            throw e;
        }
    }

    private static String emailDe(CadastroForm form) {
        return form.getEmail() == null ? "" : form.getEmail().trim();
    }

    private static String soDigitos(String texto) {
        return texto == null ? "" : texto.replaceAll("\\D", "");
    }
}
