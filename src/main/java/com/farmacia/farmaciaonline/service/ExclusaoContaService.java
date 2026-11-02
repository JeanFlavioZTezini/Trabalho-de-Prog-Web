package com.farmacia.farmaciaonline.service;

import java.time.Instant;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.farmacia.farmaciaonline.model.ExclusaoContaForm;
import com.farmacia.farmaciaonline.model.LogAuditoria;
import com.farmacia.farmaciaonline.model.Usuario;
import com.farmacia.farmaciaonline.model.enums.Perfil;
import com.farmacia.farmaciaonline.model.enums.StatusPedido;
import com.farmacia.farmaciaonline.model.enums.StatusUsuario;
import com.farmacia.farmaciaonline.repository.LogAuditoriaRepository;
import com.farmacia.farmaciaonline.security.LoginTentativasService;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;

/**
 * Exclusão da própria conta (F-24 / I14). Política da demonstração: recusa enquanto houver pedido em andamento; ao excluir,
 * apaga os dados acessórios (endereços, cartões salvos, carrinho, vínculos sociais e códigos) e anonimiza a linha do
 * usuário, que continua existindo porque pedidos, pagamentos e histórico apontam para ela e precisam ficar intactos.
 */
@Service
@RequiredArgsConstructor
public class ExclusaoContaService {

    public static final String PALAVRA_CONFIRMACAO = "EXCLUIR";
    public static final String ACAO_AUDITORIA = "CONTA_EXCLUIR";
    public static final String NOME_ANONIMO = "Conta excluída";

    static final Set<StatusPedido> EM_ANDAMENTO = EnumSet.of(StatusPedido.AGUARDANDO_PAGAMENTO,
            StatusPedido.PAGAMENTO_APROVADO, StatusPedido.EM_SEPARACAO, StatusPedido.ENVIADO,
            StatusPedido.PRONTO_PARA_RETIRADA);

    static final String MENSAGEM_PEDIDO = "Você tem pedido em andamento. A conta só pode ser excluída depois que todos os "
            + "pedidos forem entregues ou cancelados.";
    static final String MENSAGEM_SEM_SENHA = "Sua conta ainda não tem senha. Defina uma senha antes de excluir a conta.";
    static final String MENSAGEM_BLOQUEADA = "Muitas tentativas com a senha errada. Tente de novo em alguns minutos.";

    private final LogAuditoriaRepository auditoria;
    private final PasswordEncoder encoder;
    private final LoginTentativasService tentativas;

    @PersistenceContext
    private EntityManager em;

    public record Situacao(boolean temSenha, boolean temPedidoEmAndamento) {
    }

    @Transactional(readOnly = true)
    public Situacao situacao(Long usuarioId) {
        Usuario usuario = clienteAtivo(em.find(Usuario.class, usuarioId));
        return new Situacao(temSenha(usuario), temPedidoEmAndamento(usuarioId));
    }

    /**
     * Exclui a conta ou lança {@link ContaInvalidaException} (campos) / {@link ExclusaoContaRecusadaException}. Não volta
     * atrás nessas recusas: a falha de senha precisa ficar gravada.
     */
    @Transactional(noRollbackFor = { ContaInvalidaException.class, ExclusaoContaRecusadaException.class })
    public void excluir(Long usuarioId, ExclusaoContaForm form, String ip) {
        // Trava a linha: duas exclusões simultâneas (ou exclusão e tentativa errada) não se atropelam.
        Usuario usuario = clienteAtivo(em.find(Usuario.class, usuarioId, LockModeType.PESSIMISTIC_WRITE));
        if (temPedidoEmAndamento(usuarioId)) {
            throw new ExclusaoContaRecusadaException(MENSAGEM_PEDIDO);
        }
        if (!temSenha(usuario)) {
            throw new ExclusaoContaRecusadaException(MENSAGEM_SEM_SENHA);
        }
        if (usuario.getBloqueadoAte() != null && usuario.getBloqueadoAte().isAfter(Instant.now())) {
            throw new ExclusaoContaRecusadaException(MENSAGEM_BLOQUEADA);
        }

        Map<String, String> erros = new LinkedHashMap<>();
        if (!PALAVRA_CONFIRMACAO.equals(form.getConfirmacao().trim())) {
            erros.put("confirmacao", "Digite EXCLUIR, em letras maiúsculas, para confirmar.");
        }
        if (!encoder.matches(form.getSenhaAtual(), usuario.getSenhaHash())) {
            tentativas.registrarFalha(usuario.getEmail());
            erros.put("senhaAtual", "Senha atual incorreta.");
        }
        if (!erros.isEmpty()) {
            throw new ContaInvalidaException(erros);
        }

        apagarDadosAcessorios(usuarioId);
        anonimizar(usuario);
        registrarAuditoria(usuario, ip);
    }

    private void apagarDadosAcessorios(Long usuarioId) {
        em.flush();
        // Itens do carrinho caem junto (ON DELETE CASCADE); pagamento.cartao_salvo_id vira NULL (ON DELETE SET NULL).
        for (String entidade : new String[] { "Endereco", "CartaoSalvo", "Carrinho", "IdentidadeSocial",
                "CodigoVerificacao" }) {
            em.createQuery("delete from " + entidade + " x where x.usuario.id = :id")
                    .setParameter("id", usuarioId)
                    .executeUpdate();
        }
    }

    private static void anonimizar(Usuario usuario) {
        usuario.setNome(NOME_ANONIMO);
        // E-mail é NOT NULL e único: o endereço reservado .invalid libera o original para um cadastro novo.
        usuario.setEmail("excluida-" + usuario.getId() + "@excluida.invalid");
        usuario.setCpf(null);
        usuario.setCelular(null);
        usuario.setGenero(null);
        usuario.setSenhaHash(null);
        usuario.setEmailConfirmadoEm(null);
        usuario.setCelularConfirmadoEm(null);
        usuario.setTentativasLoginFalhas(0);
        usuario.setBloqueadoAte(null);
        usuario.setStatus(StatusUsuario.EXCLUIDA);
        usuario.setExcluidoEm(Instant.now());
        // data_nascimento é NOT NULL na V1 (que não pode ser editada): fica como está.
    }

    private void registrarAuditoria(Usuario usuario, String ip) {
        LogAuditoria log = new LogAuditoria();
        log.setUsuario(usuario);
        log.setAcao(ACAO_AUDITORIA);
        log.setAlvoTipo("USUARIO");
        log.setAlvoId(usuario.getId());
        log.setIp(ip);
        auditoria.save(log);
    }

    private boolean temPedidoEmAndamento(Long usuarioId) {
        Long quantidade = em.createQuery(
                "select count(p) from Pedido p where p.usuario.id = :id and p.status in :status", Long.class)
                .setParameter("id", usuarioId)
                .setParameter("status", EM_ANDAMENTO)
                .getSingleResult();
        return quantidade > 0;
    }

    private static boolean temSenha(Usuario usuario) {
        return usuario.getSenhaHash() != null && !usuario.getSenhaHash().isBlank();
    }

    private static Usuario clienteAtivo(Usuario usuario) {
        if (usuario == null || usuario.getStatus() != StatusUsuario.ATIVA) {
            throw new AccessDeniedException("Conta não está ativa.");
        }
        if (usuario.getPerfil() != Perfil.CLIENTE) {
            throw new AccessDeniedException("Conta de administrador não é excluída por aqui.");
        }
        return usuario;
    }
}
