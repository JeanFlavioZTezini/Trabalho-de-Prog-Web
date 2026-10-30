package com.farmacia.farmaciaonline.gateway.demo;

import java.time.Instant;
import java.time.YearMonth;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.farmacia.farmaciaonline.gateway.GatewayDePagamento;
import com.farmacia.farmaciaonline.gateway.dto.RequisicaoPagamento;
import com.farmacia.farmaciaonline.gateway.dto.RespostaPagamento;
import com.farmacia.farmaciaonline.model.enums.MetodoPagamento;
import com.farmacia.farmaciaonline.model.enums.StatusPagamento;

@Service
public class DemoGatewayAdapter implements GatewayDePagamento {
    private static final Logger log = LoggerFactory.getLogger(DemoGatewayAdapter.class);
    private static final int MAX_PARCELAS_CREDITO = 12;
    private static final long MINUTOS_EXPIRACAO_PIX = 30;
    private static final long DIAS_EXPIRACAO_BOLETO = 3;
    private final Map<String, EstadoTransacaoDemo> estadosDemo = new ConcurrentHashMap<>();
    @Override
    public RespostaPagamento processar(RequisicaoPagamento req) {
        validarParcelas(req);

        return switch (req.metodo()) {
            case PIX    -> processarPix(req);
            case BOLETO -> processarBoleto(req);
            default     -> processarCartao(req);
        };
    }

    @Override
    public RespostaPagamento consultar(String transacaoGatewayId) {
        EstadoTransacaoDemo estado = estadosDemo.get(transacaoGatewayId);
        if (estado == null) {
            return new RespostaPagamento(
                    transacaoGatewayId, StatusPagamento.RECUSADO,
                    "Transação não encontrada", null, null, null, null, null);
        }

        if (estado.status() == StatusPagamento.PENDENTE
                && estado.expiraEm() != null
                && Instant.now().isAfter(estado.expiraEm())) {
            EstadoTransacaoDemo expirado = estado.comStatus(StatusPagamento.EXPIRADO);
            estadosDemo.put(transacaoGatewayId, expirado);
            log.info("[DEMO] Transação {} expirou automaticamente", transacaoGatewayId);
            return toResposta(transacaoGatewayId, expirado, "Código expirado (demo)");
        }

        return toResposta(transacaoGatewayId, estado, mensagemStatus(estado.status()));
    }

    public void forcarStatus(String transacaoGatewayId, StatusPagamento novoStatus) {
        EstadoTransacaoDemo estado = estadosDemo.get(transacaoGatewayId);
        if (estado == null) {
            throw new IllegalArgumentException(
                    "Transação demo não encontrada: " + transacaoGatewayId);
        }
        estadosDemo.put(transacaoGatewayId, estado.comStatus(novoStatus));
        log.info("[DEMO] Status da transação {} forçado para {}", transacaoGatewayId, novoStatus);
    }

    private RespostaPagamento processarCartao(RequisicaoPagamento req) {
        String numeroLimpo = validarFormatoCartao(req.numeroCartao());
        validarValidadeCartao(req.validadeCartao());

        String ultimos4 = numeroLimpo.substring(numeroLimpo.length() - 4);
        String bandeira  = detectarBandeira(numeroLimpo);

        char ultimoDigito = numeroLimpo.charAt(numeroLimpo.length() - 1);
        StatusPagamento status = (ultimoDigito == '0')
                ? StatusPagamento.RECUSADO
                : StatusPagamento.APROVADO;

        String txId = gerarTxId("CARD");
        String mensagem = (status == StatusPagamento.APROVADO)
                ? "Pagamento aprovado (demo)"
                : "Cartão recusado pelo emissor (demo) — use um número que não termine em 0";

        log.info("[DEMO] Cartão processado: txId={} status={} bandeira={} ultimos4={}",
                txId, status, bandeira, ultimos4);
        EstadoTransacaoDemo estado = new EstadoTransacaoDemo(
                status, null, null, null, bandeira, ultimos4, req.valor());
        estadosDemo.put(txId, estado);

        return new RespostaPagamento(
                txId, status, mensagem,
                null, null,
                bandeira, ultimos4,
                req.valor());
    }

    private RespostaPagamento processarPix(RequisicaoPagamento req) {
        String txId       = gerarTxId("PIX");
        Instant expiraEm  = Instant.now().plusSeconds(MINUTOS_EXPIRACAO_PIX * 60);
        String codigo     = "DEMO-PIX-" + txId;

        EstadoTransacaoDemo estado = new EstadoTransacaoDemo(
                StatusPagamento.PENDENTE, codigo, expiraEm, null, null, null, req.valor());
        estadosDemo.put(txId, estado);

        log.info("[DEMO] Pix gerado: txId={} expiraEm={}", txId, expiraEm);
        return new RespostaPagamento(
                txId, StatusPagamento.PENDENTE,
                "Aguardando pagamento do Pix (demo — não é um código real)",
                codigo, expiraEm,
                null, null,
                req.valor());
    }

    private RespostaPagamento processarBoleto(RequisicaoPagamento req) {
        String txId       = gerarTxId("BOLETO");
        Instant expiraEm  = Instant.now().plusSeconds(DIAS_EXPIRACAO_BOLETO * 24 * 60 * 60);
        String codigo     = "9999.0001 DEMO-BOLETO-" + txId + " 00000.000000 0 00000000000000";

        EstadoTransacaoDemo estado = new EstadoTransacaoDemo(
                StatusPagamento.PENDENTE, codigo, expiraEm, null, null, null, req.valor());
        estadosDemo.put(txId, estado);

        log.info("[DEMO] Boleto gerado: txId={} expiraEm={}", txId, expiraEm);
        return new RespostaPagamento(
                txId, StatusPagamento.PENDENTE,
                "Boleto gerado (demo — linha digitável não é pagável)",
                codigo, expiraEm,
                null, null,
                req.valor());
    }

    private String validarFormatoCartao(String numero) {
        if (numero == null || numero.isBlank()) {
            throw new IllegalArgumentException("Número do cartão é obrigatório");
        }
        String limpo = numero.replaceAll("[\\s-]", "");
        if (!limpo.matches("\\d{13,19}")) {
            throw new IllegalArgumentException(
                    "Número do cartão inválido: deve ter entre 13 e 19 dígitos");
        }
        if (!passaLuhn(limpo)) {
            throw new IllegalArgumentException("Número do cartão inválido (dígito verificador)");
        }
        return limpo;
    }

    private void validarValidadeCartao(String validade) {
        if (validade == null || !validade.matches("\\d{2}/\\d{2}")) {
            throw new IllegalArgumentException("Validade do cartão deve estar no formato MM/AA");
        }
        try {
            int mes = Integer.parseInt(validade.substring(0, 2));
            int ano = 2000 + Integer.parseInt(validade.substring(3, 5));
            YearMonth cartao  = YearMonth.of(ano, mes);
            YearMonth agora   = YearMonth.now();
            if (cartao.isBefore(agora)) {
                throw new IllegalArgumentException("Cartão vencido");
            }
        } catch (java.time.DateTimeException e) {
            throw new IllegalArgumentException("Validade do cartão inválida");
        }
    }

    private void validarParcelas(RequisicaoPagamento req) {
        int parcelas = req.parcelas();
        if (req.metodo() != MetodoPagamento.CARTAO_CREDITO && parcelas != 1) {
            throw new IllegalArgumentException(
                    req.metodo() + " não aceita parcelamento — parcelas deve ser 1");
        }
        if (req.metodo() == MetodoPagamento.CARTAO_CREDITO
                && (parcelas < 1 || parcelas > MAX_PARCELAS_CREDITO)) {
            throw new IllegalArgumentException(
                    "Parcelas deve ser entre 1 e " + MAX_PARCELAS_CREDITO);
        }
    }
    private String detectarBandeira(String numeroLimpo) {
        if (numeroLimpo.startsWith("4"))                           return "VISA";
        if (numeroLimpo.matches("5[1-5].*"))                       return "MASTERCARD";
        if (numeroLimpo.matches("2(2[2-9]|[3-6]\\d|7[01]).*"))    return "MASTERCARD";
        if (numeroLimpo.matches("3[47].*"))                        return "AMEX";
        if (numeroLimpo.matches("(636368|438935|504175|451416|636297|5067|4576|4011|6062).*"))
                                                                   return "ELO";
        if (numeroLimpo.startsWith("6011") || numeroLimpo.startsWith("65"))
                                                                   return "DISCOVER";
        return "DESCONHECIDA";
    }

    private String gerarTxId(String prefixo) {
        return prefixo + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
    }
    private boolean passaLuhn(String numero) {
        int soma = 0;
        boolean dobrar = false;
        for (int i = numero.length() - 1; i >= 0; i--) {
            int digito = numero.charAt(i) - '0';
            if (dobrar) {
                digito *= 2;
                if (digito > 9) digito -= 9;
            }
            soma += digito;
            dobrar = !dobrar;
        }
        return soma % 10 == 0;
    }

    private String mensagemStatus(StatusPagamento status) {
        return switch (status) {
            case PENDENTE  -> "Aguardando pagamento (demo)";
            case APROVADO  -> "Pagamento aprovado (demo)";
            case RECUSADO  -> "Pagamento recusado (demo)";
            case EXPIRADO  -> "Código expirado (demo)";
        };
    }

    private RespostaPagamento toResposta(String txId, EstadoTransacaoDemo e, String mensagem) {
        return new RespostaPagamento(
                txId, e.status(), mensagem,
                e.codigoPagamento(), e.expiraEm(),
                e.bandeira(), e.ultimos4(),
                e.valorConfirmado());
    }

    private record EstadoTransacaoDemo(
            StatusPagamento status,
            String codigoPagamento,
            Instant expiraEm,
            String mensagem,
            String bandeira,
            String ultimos4,
            java.math.BigDecimal valorConfirmado
    ) {
        EstadoTransacaoDemo comStatus(StatusPagamento novoStatus) {
            return new EstadoTransacaoDemo(
                    novoStatus, codigoPagamento, expiraEm,
                    mensagem, bandeira, ultimos4, valorConfirmado);
        }
    }
}
