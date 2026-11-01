package com.farmacia.farmaciaonline.service;

import java.time.LocalDate;
import java.time.Period;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.farmacia.farmaciaonline.model.ContaForm;
import com.farmacia.farmaciaonline.model.SenhaForm;
import com.farmacia.farmaciaonline.model.Usuario;
import com.farmacia.farmaciaonline.model.enums.StatusUsuario;
import com.farmacia.farmaciaonline.repository.UsuarioRepository;
import com.farmacia.farmaciaonline.validacao.PoliticaDeSenha;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ContaService {

    private static final LocalDate NASCIMENTO_MAIS_ANTIGO = LocalDate.of(1900, 1, 1);

    private final UsuarioRepository usuarios;
    private final PasswordEncoder encoder;

    @Transactional(readOnly = true)
    public DadosDaConta dados(Long usuarioId) {
        return DadosDaConta.de(contaAtiva(usuarioId));
    }

    @Transactional(readOnly = true)
    public ContaForm formDe(Long usuarioId) {
        Usuario usuario = contaAtiva(usuarioId);
        ContaForm form = new ContaForm();
        form.setNome(usuario.getNome());
        form.setDataNascimento(usuario.getDataNascimento());
        form.setGenero(usuario.getGenero());
        return form;
    }

    @Transactional
    public Usuario atualizarDados(Long usuarioId, ContaForm form) {
        Usuario usuario = contaAtiva(usuarioId);

        Map<String, String> erros = new LinkedHashMap<>();
        LocalDate nascimento = form.getDataNascimento();
        LocalDate hoje = LocalDate.now();
        if (nascimento.isBefore(NASCIMENTO_MAIS_ANTIGO) || nascimento.isAfter(hoje)) {
            erros.put("dataNascimento", "Informe uma data de nascimento válida.");
        } else if (Period.between(nascimento, hoje).getYears() < CadastroService.IDADE_MINIMA) {
            erros.put("dataNascimento", "É preciso ter 18 anos ou mais para ter uma conta.");
        }
        if (!erros.isEmpty()) {
            throw new ContaInvalidaException(erros);
        }

        usuario.setNome(form.getNome().trim().replaceAll("\\s+", " "));
        usuario.setDataNascimento(nascimento);
        usuario.setGenero(form.getGenero());
        return usuarios.saveAndFlush(usuario);
    }

    @Transactional
    public Usuario alterarSenha(Long usuarioId, SenhaForm form) {
        Usuario usuario = contaAtiva(usuarioId);

        Map<String, String> erros = new LinkedHashMap<>();
        String hashAtual = usuario.getSenhaHash();
        if (hashAtual == null || hashAtual.isBlank()) {
            erros.put("senhaAtual", "Esta conta não tem senha cadastrada.");
        } else if (!encoder.matches(form.getSenhaAtual(), hashAtual)) {
            erros.put("senhaAtual", "Senha atual incorreta.");
        }

        String nova = form.getNovaSenha();
        if (!nova.equals(form.getConfirmarNovaSenha())) {
            erros.put("confirmarNovaSenha", "As senhas não conferem.");
        }
        if (PoliticaDeSenha.excedeLimite(nova)) {
            erros.put("novaSenha", PoliticaDeSenha.MENSAGEM_LIMITE);
        } else if (nova.equals(form.getSenhaAtual())) {
            erros.put("novaSenha", "A nova senha deve ser diferente da atual.");
        }
        if (!erros.isEmpty()) {
            throw new ContaInvalidaException(erros);
        }

        usuario.setSenhaHash(encoder.encode(nova));
        return usuarios.saveAndFlush(usuario);
    }

    private Usuario contaAtiva(Long usuarioId) {
        Usuario usuario = usuarios.findById(usuarioId)
                .orElseThrow(() -> new AccessDeniedException("Conta não encontrada."));
        if (usuario.getStatus() != StatusUsuario.ATIVA) {
            throw new AccessDeniedException("Conta não está ativa.");
        }
        return usuario;
    }
}
