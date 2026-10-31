package com.farmacia.farmaciaonline.service;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.farmacia.farmaciaonline.model.Endereco;
import com.farmacia.farmaciaonline.model.EnderecoEntrega;
import com.farmacia.farmaciaonline.model.EnderecoForm;
import com.farmacia.farmaciaonline.model.Usuario;
import com.farmacia.farmaciaonline.model.enums.StatusUsuario;
import com.farmacia.farmaciaonline.repository.EnderecoRepository;
import com.farmacia.farmaciaonline.repository.UsuarioRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class EnderecoService {

    private final EnderecoRepository repository;
    private final UsuarioRepository usuarioRepository;

    @Transactional(readOnly = true)
    public List<Endereco> listar(Long usuarioId) {
        exigirContaAtiva(usuarioId);
        return repository.findByUsuarioIdOrderByCriadoEmDesc(usuarioId);
    }

    @Transactional(readOnly = true)
    public Endereco buscar(Long usuarioId, Long id) {
        exigirContaAtiva(usuarioId);
        return repository.findByIdAndUsuarioId(id, usuarioId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public EnderecoForm formDe(Long usuarioId, Long id) {
        Endereco e = buscar(usuarioId, id);
        EnderecoForm form = new EnderecoForm();
        form.setDestinatario(e.getDestinatario());
        form.setCep(e.getCep());
        form.setLogradouro(e.getLogradouro());
        form.setNumero(e.getNumero());
        form.setComplemento(e.getComplemento());
        form.setBairro(e.getBairro());
        form.setCidade(e.getCidade());
        form.setUf(e.getUf());
        form.setReferencia(e.getReferencia());
        return form;
    }

    public Long salvar(Long usuarioId, Long id, EnderecoForm form) {
        Usuario usuario = exigirContaAtiva(usuarioId);
        Endereco endereco;
        if (id == null) {
            endereco = new Endereco();
            endereco.setUsuario(usuario);
        } else {
            endereco = repository.findByIdAndUsuarioId(id, usuarioId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        }

        String cep = somenteDigitos(form.getCep());
        if (cep.length() != 8) {
            throw new IllegalArgumentException("CEP deve ter 8 dígitos.");
        }
        endereco.setDestinatario(form.getDestinatario().trim());
        endereco.setCep(cep);
        endereco.setLogradouro(form.getLogradouro().trim());
        endereco.setNumero(form.getNumero().trim());
        endereco.setComplemento(vazioParaNulo(form.getComplemento()));
        endereco.setBairro(form.getBairro().trim());
        endereco.setCidade(form.getCidade().trim());
        endereco.setUf(form.getUf().trim().toUpperCase(java.util.Locale.ROOT));
        endereco.setReferencia(vazioParaNulo(form.getReferencia()));
        return repository.saveAndFlush(endereco).getId();
    }

    public void excluir(Long usuarioId, Long id) {
        Endereco endereco = buscar(usuarioId, id);
        repository.delete(endereco);
        repository.flush();
    }

    @Transactional(readOnly = true)
    public EnderecoEntrega copiaParaEntrega(Long usuarioId, Long id) {
        Endereco e = buscar(usuarioId, id);
        EnderecoEntrega copia = new EnderecoEntrega();
        copia.setDestinatario(e.getDestinatario());
        copia.setCep(e.getCep());
        copia.setLogradouro(e.getLogradouro());
        copia.setNumero(e.getNumero());
        copia.setComplemento(e.getComplemento());
        copia.setBairro(e.getBairro());
        copia.setCidade(e.getCidade());
        copia.setUf(e.getUf());
        copia.setReferencia(e.getReferencia());
        return copia;
    }

    private Usuario exigirContaAtiva(Long usuarioId) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN));
        if (usuario.getStatus() != StatusUsuario.ATIVA || usuario.getExcluidoEm() != null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        return usuario;
    }

    private static String somenteDigitos(String texto) {
        return texto == null ? "" : texto.replaceAll("\\D", "");
    }

    private static String vazioParaNulo(String texto) {
        return texto == null || texto.isBlank() ? null : texto.trim();
    }
}
