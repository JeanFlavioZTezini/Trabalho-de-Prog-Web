package com.farmacia.farmaciaonline.service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
    public static final String ACAO_EXPORTAR = "CLIENTE_EXPORTAR";
    public static final int LIMITE_EXPORTACAO = 5000;
    static final String ALVO_USUARIO = "USUARIO";
    static final int PEDIDOS_RECENTES = 10;

    private static final ZoneId FUSO = PeriodoFiltro.FUSO;
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final String ESCAPE = "!";

    private final UsuarioRepository usuarios;
    private final PedidoRepository pedidos;
    private final LogAuditoriaRepository auditoria;

    /**
     * Lista só contas CLIENTE (administrador nunca aparece). O texto buscado NÃO vai para o log: pode ser CPF ou
     * e-mail. O log guarda só o filtro de status, se houve busca/período e a página.
     */
    public Page<ClienteResumo> listar(Long adminId, String ip, String busca, StatusUsuario status, Pageable pageable) {
        return listar(adminId, ip, busca, status, PeriodoFiltro.VAZIO, pageable);
    }

    public Page<ClienteResumo> listar(Long adminId, String ip, String busca, StatusUsuario status,
            PeriodoFiltro periodo, Pageable pageable) {
        String termo = busca == null ? "" : busca.trim();
        Page<ClienteResumo> pagina = consultar(termo, status, periodo, pageable).map(ClienteResumo::de);

        registrar(adminId, ip, ACAO_LISTAR, null, filtros(termo, status, periodo) + ", pagina="
                + pageable.getPageNumber());
        return pagina;
    }

    /**
     * F-23: CSV com os mesmos filtros da tela, até {@link #LIMITE_EXPORTACAO} linhas. CPF, e-mail e celular saem
     * mascarados como na tela; nada de senha, hash ou dado de login. A exportação grava CLIENTE_EXPORTAR.
     */
    public String exportarCsv(Long adminId, String ip, String busca, StatusUsuario status, PeriodoFiltro periodo) {
        String termo = busca == null ? "" : busca.trim();
        List<Usuario> clientes = consultar(termo, status, periodo,
                PageRequest.of(0, LIMITE_EXPORTACAO, Sort.by("nome", "id"))).getContent();

        StringBuilder csv = new StringBuilder(Csv.BOM);
        Csv.linha(csv, "Id", "Nome", "CPF", "E-mail", "Celular", "Status", "Cadastro");
        for (Usuario u : clientes) {
            LocalDateTime criado = paraHorarioLocal(u.getCriadoEm());
            Csv.linha(csv, String.valueOf(u.getId()), u.getNome(), ContatoMascarado.cpf(u.getCpf()),
                    ContatoMascarado.email(u.getEmail()), ContatoMascarado.celular(u.getCelular()),
                    ClienteResumo.rotulo(u.getStatus()), criado == null ? "" : DATA.format(criado));
        }

        registrar(adminId, ip, ACAO_EXPORTAR, null, filtros(termo, status, periodo) + ", linhas=" + clientes.size());
        return csv.toString();
    }

    private Page<Usuario> consultar(String termo, StatusUsuario status, PeriodoFiltro periodo, Pageable pageable) {
        String digitos = termo.replaceAll("\\D", "");
        List<StatusUsuario> statuses = status == null ? Arrays.asList(StatusUsuario.values()) : List.of(status);
        PeriodoFiltro p = periodo == null ? PeriodoFiltro.VAZIO : periodo;
        return usuarios.buscarClientes(statuses, padraoDeTexto(termo), padraoDeCpf(digitos), p.inicio(), p.fim(),
                pageable);
    }

    private static String filtros(String termo, StatusUsuario status, PeriodoFiltro periodo) {
        return "status=" + (status == null ? "TODOS" : status) + ", busca=" + (termo.isEmpty() ? "nao" : "sim")
                + (periodo != null && periodo.informado() ? ", periodo=sim" : "");
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

    /** Só dígitos do CPF; com menos de 3 dígitos não filtra por CPF (""). Também usado pela lista de pedidos. */
    static String padraoDeCpf(String digitos) {
        return digitos.length() >= 3 ? "%" + digitos + "%" : "";
    }

    /** Vira o padrão do LIKE em minúsculas, com %, _ e o próprio escape tratados como texto comum. */
    static String padraoDeTexto(String termo) {
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
