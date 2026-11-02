package com.farmacia.farmaciaonline.service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.farmacia.farmaciaonline.model.LogAuditoria;
import com.farmacia.farmaciaonline.model.Usuario;
import com.farmacia.farmaciaonline.model.enums.Perfil;
import com.farmacia.farmaciaonline.model.enums.StatusUsuario;
import com.farmacia.farmaciaonline.repository.LogAuditoriaRepository;
import com.farmacia.farmaciaonline.repository.PedidoRepository;
import com.farmacia.farmaciaonline.repository.UsuarioRepository;

import lombok.RequiredArgsConstructor;

/**
 * UC11: consulta administrativa de clientes, somente leitura. Cada consulta grava o log de auditoria na mesma
 * transação da leitura: se o log falhar, a consulta falha (nada de leitura sem rastro).
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ClienteAdminService {

    public static final String ACAO_LISTAR = "CLIENTE_LISTAR";
    public static final String ACAO_VISUALIZAR = "CLIENTE_VISUALIZAR";
    static final String ALVO_USUARIO = "USUARIO";
    static final int PEDIDOS_RECENTES = 10;

    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    private static final String ESCAPE = "!";

    private final UsuarioRepository usuarios;
    private final PedidoRepository pedidos;
    private final LogAuditoriaRepository auditoria;

    /**
     * Lista só contas CLIENTE (administrador nunca aparece). O texto buscado NÃO vai para o log: pode ser CPF ou
     * e-mail. O log guarda só o filtro de status, se houve busca e a página.
     */
    public Page<ClienteResumo> listar(Long adminId, String ip, String busca, StatusUsuario status, Pageable pageable) {
        String termo = busca == null ? "" : busca.trim();
        String digitos = termo.replaceAll("\\D", "");
        List<StatusUsuario> statuses = status == null ? Arrays.asList(StatusUsuario.values()) : List.of(status);

        Page<ClienteResumo> pagina = usuarios
                .buscarClientes(statuses, padraoDeTexto(termo), digitos.length() >= 3 ? "%" + digitos + "%" : "",
                        pageable)
                .map(ClienteResumo::de);

        registrar(adminId, ip, ACAO_LISTAR, null, "status=" + (status == null ? "TODOS" : status) + ", busca="
                + (termo.isEmpty() ? "nao" : "sim") + ", pagina=" + pageable.getPageNumber());
        return pagina;
    }

    /** Cliente inexistente ou conta que não é CLIENTE (admin) responde 404, sem gravar log. */
    public ClienteDetalhe detalhar(Long adminId, String ip, Long clienteId) {
        Usuario cliente = usuarios.findById(clienteId)
                .filter(u -> u.getPerfil() == Perfil.CLIENTE)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        var recentes = pedidos
                .findByUsuarioIdOrderByCriadoEmDescIdDesc(cliente.getId(), PageRequest.of(0, PEDIDOS_RECENTES))
                .getContent();

        registrar(adminId, ip, ACAO_VISUALIZAR, cliente.getId(), null);
        return ClienteDetalhe.de(cliente, recentes);
    }

    static LocalDateTime paraHorarioLocal(Instant instante) {
        return instante == null ? null : LocalDateTime.ofInstant(instante, FUSO);
    }

    /** Vira o padrão do LIKE em minúsculas, com %, _ e o próprio escape tratados como texto comum. */
    private static String padraoDeTexto(String termo) {
        String escapado = termo.toLowerCase()
                .replace(ESCAPE, ESCAPE + ESCAPE)
                .replace("%", ESCAPE + "%")
                .replace("_", ESCAPE + "_");
        return "%" + escapado + "%";
    }

    private void registrar(Long adminId, String ip, String acao, Long alvoId, String detalhes) {
        if (adminId == null) {
            throw new IllegalStateException("Administrador não identificado: a consulta não pode ficar sem autor.");
        }
        LogAuditoria log = new LogAuditoria();
        log.setUsuario(usuarios.getReferenceById(adminId));
        log.setAcao(acao);
        log.setAlvoTipo(alvoId == null ? null : ALVO_USUARIO);
        log.setAlvoId(alvoId);
        log.setDetalhes(detalhes);
        log.setIp(ip);
        auditoria.save(log);
    }
}
