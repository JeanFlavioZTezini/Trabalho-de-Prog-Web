package com.farmacia.farmaciaonline.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.farmacia.farmaciaonline.model.CompletarSocialForm;
import com.farmacia.farmaciaonline.model.IdentidadeSocial;
import com.farmacia.farmaciaonline.model.Usuario;
import com.farmacia.farmaciaonline.model.enums.CanalCodigo;
import com.farmacia.farmaciaonline.model.enums.FinalidadeCodigo;
import com.farmacia.farmaciaonline.model.enums.Perfil;
import com.farmacia.farmaciaonline.model.enums.StatusUsuario;
import com.farmacia.farmaciaonline.repository.IdentidadeSocialRepository;
import com.farmacia.farmaciaonline.repository.UsuarioRepository;
import com.farmacia.farmaciaonline.security.LoginTentativasService;
import com.farmacia.farmaciaonline.service.CodigoVerificacaoService.ResultadoEnvio;
import com.farmacia.farmaciaonline.service.CodigoVerificacaoService.ResultadoValidacao;
import com.farmacia.farmaciaonline.social.IdentidadeExterna;

/**
 * Regras do login social (F-20, UC10-A1/A2). O provedor só prova quem a pessoa é <em>no provedor</em>; quem ela é
 * aqui depende do vínculo gravado em {@code identidade_social}:
 * <ul>
 *   <li>vínculo existente com conta ativa: entra;</li>
 *   <li>sem vínculo: completa CPF, nascimento (18+) e o que faltar; CPF novo cria a conta, CPF de conta existente
 *       exige a senha dessa conta ou um código mandado ao e-mail <em>dela</em> (e-mail igual sozinho nunca basta);</li>
 *   <li>administrador nunca entra nem vincula por aqui (só com senha).</li>
 * </ul>
 * Sem transação na classe: a validação de código grava a tentativa errada por conta própria (contrato da F-05).
 */
@Service
public class LoginSocialService {

    public static final String MENSAGEM_ADMIN = "Conta de administrador entra só com e-mail e senha.";
    public static final String MENSAGEM_INDISPONIVEL = "Não foi possível entrar com essa conta. Entre com e-mail ou CPF e senha.";
    public static final String MENSAGEM_JA_VINCULADA = "Esta conta do provedor já está ligada a outro cadastro.";

    public enum Tipo { ENTRAR, CONFIRMAR, COMPLETAR, VINCULADO, RECUSADO }

    /** {@code usuario} existe em ENTRAR, CONFIRMAR e VINCULADO; {@code mensagem} em RECUSADO e VINCULADO. */
    public record Resolucao(Tipo tipo, Usuario usuario, String mensagem) {
        static Resolucao de(Tipo tipo, Usuario usuario) {
            return new Resolucao(tipo, usuario, null);
        }

        static Resolucao recusa(String mensagem) {
            return new Resolucao(Tipo.RECUSADO, null, mensagem);
        }
    }

    public enum Desfecho { CONTA_ATIVA, CONTA_PENDENTE, CPF_EXISTENTE, ERROS }

    /** Resultado do primeiro acesso. Em CPF_EXISTENTE, {@code contaId} é a conta que precisa ser comprovada. */
    public record Conclusao(Desfecho desfecho, Usuario usuario, Long contaId, Map<String, String> erros) {
    }

    private final UsuarioRepository usuarios;
    private final IdentidadeSocialRepository identidades;
    private final CodigoVerificacaoService codigos;
    private final PasswordEncoder encoder;
    private final ObjectProvider<LoginTentativasService> tentativas;
    private final TransactionTemplate transacao;
    private final String termosVersao;

    public LoginSocialService(UsuarioRepository usuarios, IdentidadeSocialRepository identidades,
            CodigoVerificacaoService codigos, PasswordEncoder encoder,
            ObjectProvider<LoginTentativasService> tentativas, TransactionTemplate transacao,
            @Value("${app.termos.versao:1.0}") String termosVersao) {
        this.usuarios = usuarios;
        this.identidades = identidades;
        this.codigos = codigos;
        this.encoder = encoder;
        this.tentativas = tentativas;
        this.transacao = transacao;
        this.termosVersao = termosVersao;
    }

    /**
     * O que fazer com a identidade que voltou do provedor. Com {@code logadoId}, a pessoa está em "Minha conta" e o
     * vínculo é feito direto na conta dela (a sessão já prova quem ela é).
     */
    public Resolucao resolver(IdentidadeExterna identidade, Long logadoId) {
        return transacao.execute(status -> {
            var vinculo = identidades.findByProvedorAndProvedorUsuarioId(identidade.provedor(), identidade.id());
            if (logadoId != null) {
                Usuario logado = usuarios.findById(logadoId).orElseThrow();
                if (logado.getPerfil() == Perfil.ADMIN) {
                    return Resolucao.recusa(MENSAGEM_ADMIN);
                }
                if (vinculo.isPresent()) {
                    return vinculo.get().getUsuario().getId().equals(logadoId)
                            ? new Resolucao(Tipo.VINCULADO, logado, "Esta conta já estava vinculada.")
                            : Resolucao.recusa(MENSAGEM_JA_VINCULADA);
                }
                if (identidades.existsByUsuarioIdAndProvedor(logadoId, identidade.provedor())) {
                    return Resolucao.recusa("Sua conta já tem outra conta desse provedor vinculada.");
                }
                vincular(logado, identidade);
                return new Resolucao(Tipo.VINCULADO, logado, "Conta vinculada. Agora você também pode entrar por ela.");
            }
            if (vinculo.isEmpty()) {
                return Resolucao.de(Tipo.COMPLETAR, null);
            }
            Usuario conta = vinculo.get().getUsuario();
            if (conta.getPerfil() == Perfil.ADMIN) {
                return Resolucao.recusa(MENSAGEM_ADMIN);
            }
            if (conta.getStatus() == StatusUsuario.PENDENTE) {
                return Resolucao.de(Tipo.CONFIRMAR, conta);
            }
            if (conta.getStatus() != StatusUsuario.ATIVA || conta.getExcluidoEm() != null
                    || (conta.getBloqueadoAte() != null && conta.getBloqueadoAte().isAfter(Instant.now()))) {
                return Resolucao.recusa(MENSAGEM_INDISPONIVEL);
            }
            return Resolucao.de(Tipo.ENTRAR, conta);
        });
    }

    /** Primeiro acesso: valida o que faltou e cria a conta, ou aponta para a comprovação quando o CPF já existe. */
    public Conclusao completar(IdentidadeExterna identidade, CompletarSocialForm form) {
        Map<String, String> erros = new LinkedHashMap<>();
        LocalDate nascimento = form.getDataNascimento();
        LocalDate hoje = LocalDate.now(PeriodoFiltro.FUSO_SAO_PAULO);
        if (nascimento != null && (nascimento.isAfter(hoje) || nascimento.isBefore(LocalDate.of(1900, 1, 1)))) {
            erros.put("dataNascimento", "Informe uma data de nascimento válida.");
        } else if (nascimento != null && Period.between(nascimento, hoje).getYears() < CadastroService.IDADE_MINIMA) {
            erros.put("dataNascimento", "É preciso ter 18 anos ou mais para criar a conta.");
        }
        String email = identidade.temEmail() ? identidade.email() : texto(form.getEmail()).toLowerCase(Locale.ROOT);
        if (!identidade.temEmail() && !email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            erros.put("email", "Informe um e-mail válido: o provedor não informou um.");
        }
        if (!erros.isEmpty()) {
            return new Conclusao(Desfecho.ERROS, null, null, erros);
        }

        String cpf = soDigitos(form.getCpf());
        var dono = usuarios.findByCpf(cpf);
        if (dono.isPresent()) {
            return new Conclusao(Desfecho.CPF_EXISTENTE, null, dono.get().getId(), Map.of());
        }
        if (usuarios.existsByEmailIgnoreCase(email)) {
            erros.put("email", "Este e-mail já está em outra conta. Entre com e-mail ou CPF e senha e vincule em Minha conta.");
            return new Conclusao(Desfecho.ERROS, null, null, erros);
        }

        boolean verificado = identidade.temEmail() && identidade.emailVerificado();
        try {
            Usuario criado = transacao.execute(status -> {
                if (identidades.findByProvedorAndProvedorUsuarioId(identidade.provedor(), identidade.id()).isPresent()) {
                    throw new IllegalStateException(MENSAGEM_JA_VINCULADA);
                }
                Usuario conta = usuarios.saveAndFlush(Usuario.builder()
                        .nome(form.getNome().trim().replaceAll("\\s+", " "))
                        .cpf(cpf)
                        .dataNascimento(nascimento)
                        .genero(form.getGenero())
                        .celular(soDigitos(form.getCelular()))
                        .email(email)
                        .senhaHash(null)   // conta social nasce sem senha; pode definir depois pela recuperação
                        .perfil(Perfil.CLIENTE)
                        .status(verificado ? StatusUsuario.ATIVA : StatusUsuario.PENDENTE)
                        .emailConfirmadoEm(verificado ? Instant.now() : null)
                        .termosVersao(termosVersao)
                        .termosAceitosEm(Instant.now())
                        .build());
                vincular(conta, identidade);
                return conta;
            });
            return new Conclusao(verificado ? Desfecho.CONTA_ATIVA : Desfecho.CONTA_PENDENTE, criado, null, Map.of());
        } catch (IllegalStateException e) {
            return new Conclusao(Desfecho.ERROS, null, null, Map.of("geral", e.getMessage()));
        } catch (DataIntegrityViolationException e) {
            // Outro cadastro com o mesmo CPF/e-mail entrou no meio: o banco garante a unicidade.
            return new Conclusao(Desfecho.ERROS, null, null,
                    Map.of("geral", "Não foi possível criar a conta com esses dados. Confira o CPF e o e-mail."));
        }
    }

    /** Comprova a conta dona do CPF com a senha dela. Erro conta como tentativa de login (bloqueio da F-18). */
    public Resolucao vincularComSenha(IdentidadeExterna identidade, Long contaId, String senha) {
        Usuario conta = usuarios.findById(contaId).orElseThrow();
        Resolucao barrada = barrarVinculo(conta);
        if (barrada != null) {
            return barrada;
        }
        if (conta.getSenhaHash() == null || senha == null || !encoder.matches(senha, conta.getSenhaHash())) {
            tentativas.ifAvailable(t -> t.registrarFalha(conta.getEmail()));
            return Resolucao.recusa("Senha incorreta. Se não lembra, use o código por e-mail.");
        }
        return concluirVinculo(identidade, conta);
    }

    /** O código vai para o e-mail cadastrado na conta dona do CPF, nunca para o e-mail que veio do provedor. */
    public ResultadoEnvio enviarCodigoVinculo(Long contaId) {
        Usuario conta = usuarios.findById(contaId).orElseThrow();
        return codigos.enviar(conta, FinalidadeCodigo.VINCULO_SOCIAL, CanalCodigo.EMAIL, conta.getEmail());
    }

    public String emailMascaradoDa(Long contaId) {
        return usuarios.findById(contaId).map(u -> ContatoMascarado.email(u.getEmail())).orElse("");
    }

    public Resolucao vincularComCodigo(IdentidadeExterna identidade, Long contaId, String codigo) {
        Usuario conta = usuarios.findById(contaId).orElseThrow();
        Resolucao barrada = barrarVinculo(conta);
        if (barrada != null) {
            return barrada;
        }
        ResultadoValidacao validacao = codigos.validar(contaId, FinalidadeCodigo.VINCULO_SOCIAL, codigo);
        if (!validacao.valido()) {
            return Resolucao.recusa(switch (validacao.situacao()) {
                case EXPIRADO -> "O código expirou. Peça outro.";
                case BLOQUEADO -> "Muitas tentativas erradas. Peça outro código.";
                case JA_USADO -> "Este código já foi usado. Peça outro.";
                case INEXISTENTE -> "Peça o código primeiro.";
                default -> "Código incorreto. Tentativas restantes: " + validacao.tentativasRestantes() + ".";
            });
        }
        return concluirVinculo(identidade, conta);
    }

    public List<IdentidadeSocial> vinculosDe(Long usuarioId) {
        return identidades.findByUsuarioIdOrderByCriadoEmAsc(usuarioId);
    }

    private Resolucao barrarVinculo(Usuario conta) {
        if (conta.getPerfil() == Perfil.ADMIN) {
            return Resolucao.recusa(MENSAGEM_ADMIN);
        }
        if (conta.getStatus() == StatusUsuario.EXCLUIDA
                || (conta.getBloqueadoAte() != null && conta.getBloqueadoAte().isAfter(Instant.now()))) {
            return Resolucao.recusa(MENSAGEM_INDISPONIVEL);
        }
        return null;
    }

    private Resolucao concluirVinculo(IdentidadeExterna identidade, Usuario conta) {
        return transacao.execute(status -> {
            if (identidades.findByProvedorAndProvedorUsuarioId(identidade.provedor(), identidade.id()).isPresent()) {
                return Resolucao.recusa(MENSAGEM_JA_VINCULADA);
            }
            if (identidades.existsByUsuarioIdAndProvedor(conta.getId(), identidade.provedor())) {
                return Resolucao.recusa("Esta conta já tem outra conta desse provedor vinculada.");
            }
            Usuario atual = usuarios.findById(conta.getId()).orElseThrow();
            vincular(atual, identidade);
            return Resolucao.de(atual.getStatus() == StatusUsuario.ATIVA ? Tipo.ENTRAR : Tipo.CONFIRMAR, atual);
        });
    }

    private void vincular(Usuario conta, IdentidadeExterna identidade) {
        identidades.saveAndFlush(IdentidadeSocial.builder()
                .usuario(conta)
                .provedor(identidade.provedor())
                .provedorUsuarioId(identidade.id())
                .build());
    }

    private static String texto(String valor) {
        return valor == null ? "" : valor.trim();
    }

    private static String soDigitos(String valor) {
        return valor == null ? "" : valor.replaceAll("\\D", "");
    }
}
