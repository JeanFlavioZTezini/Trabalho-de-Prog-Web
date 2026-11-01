package com.farmacia.farmaciaonline.security;

import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.stereotype.Component;

/**
 * Encerra todas as sessões abertas de um usuário, em qualquer navegador (UC14, UC10-A4). Usa o registro de sessões do
 * Spring Security (ligado na {@code SecurityConfig}): a sessão encerrada é barrada na próxima requisição e vai para
 * {@code /login?expirada}. Serve também para a troca de senha logado (F-12) e para o login (F-18).
 */
@Component
public class RevogacaoDeSessoes {

    private final SessionRegistry registro;

    public RevogacaoDeSessoes(SessionRegistry registro) {
        this.registro = registro;
    }

    /** @return quantas sessões foram encerradas */
    public int encerrarSessoesDe(Long usuarioId) {
        return encerrarOutrasSessoesDe(usuarioId, null);
    }

    /**
     * Encerra as sessões da conta menos a de {@code idSessaoAtual}: é o que a troca de senha logado (F-12) usa, para a
     * pessoa continuar na tela em que acabou de trocar a senha. Sem id (null), encerra todas.
     *
     * @return quantas sessões foram encerradas
     */
    public int encerrarOutrasSessoesDe(Long usuarioId, String idSessaoAtual) {
        int encerradas = 0;
        for (Object principal : registro.getAllPrincipals()) {
            if (principal instanceof UsuarioAutenticado usuario && usuario.getId().equals(usuarioId)) {
                for (SessionInformation sessao : registro.getAllSessions(principal, false)) {
                    if (sessao.getSessionId().equals(idSessaoAtual)) {
                        continue;
                    }
                    sessao.expireNow();
                    encerradas++;
                }
            }
        }
        return encerradas;
    }
}
