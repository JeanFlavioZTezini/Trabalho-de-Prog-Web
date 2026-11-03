package com.farmacia.farmaciaonline.mensagem.demo;

import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import jakarta.servlet.http.HttpSession;

import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.farmacia.farmaciaonline.mensagem.EnvioDeMensagem;
import com.farmacia.farmaciaonline.mensagem.MensagemEnviada;
import com.farmacia.farmaciaonline.model.enums.CanalCodigo;

/**
 * Envio simulado de e-mail e SMS: em vez de mandar, guarda as últimas mensagens na memória (somem ao reiniciar).
 * Só a página {@code /caixa-demo}, que existe apenas no perfil demo, mostra essas mensagens. Nada é gravado em log.
 * Cada mensagem guarda a "caixa" da sessão que a gerou, para um visitante não ler o código de outra conta.
 */
@Component
public class DemoEnvioAdapter implements EnvioDeMensagem {

    static final int LIMITE = 50;

    /**
     * Atributo de sessão com a caixa do navegador. É um atributo, e não o id da sessão, porque o id muda no login
     * (proteção contra fixação de sessão) e os atributos sobrevivem a essa troca.
     */
    public static final String ATRIBUTO_CAIXA = "caixaDemo";

    private final Deque<MensagemEnviada> caixa = new ArrayDeque<>();

    @Override
    public synchronized void enviar(CanalCodigo canal, String destino, String assunto, String texto) {
        caixa.addFirst(new MensagemEnviada(LocalDateTime.now(), canal, destino, assunto, texto, caixaDaRequisicaoAtual()));
        while (caixa.size() > LIMITE) {
            caixa.removeLast();
        }
    }

    /** Da mais recente para a mais antiga. */
    public synchronized List<MensagemEnviada> mensagens() {
        return List.copyOf(caixa);
    }

    /** O administrador vê todas; os demais, só as da própria caixa (sem caixa: nenhuma). */
    public synchronized List<MensagemEnviada> mensagensVisiveis(String caixaId, boolean admin) {
        if (admin) {
            return List.copyOf(caixa);
        }
        if (caixaId == null) {
            return List.of();
        }
        return caixa.stream().filter(m -> Objects.equals(caixaId, m.caixaId())).toList();
    }

    /** Caixa da sessão da requisição atual, criada no primeiro envio. Sem requisição (job), null. */
    private static String caixaDaRequisicaoAtual() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes atributos)) {
            return null;
        }
        HttpSession sessao = atributos.getRequest().getSession();
        Object atual = sessao.getAttribute(ATRIBUTO_CAIXA);
        if (atual instanceof String caixaId) {
            return caixaId;
        }
        String nova = UUID.randomUUID().toString();
        sessao.setAttribute(ATRIBUTO_CAIXA, nova);
        return nova;
    }
}
