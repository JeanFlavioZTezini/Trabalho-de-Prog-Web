package com.farmacia.farmaciaonline.model;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.springframework.web.multipart.MultipartFile;

import com.farmacia.farmaciaonline.model.enums.Tarja;
import com.farmacia.farmaciaonline.model.enums.TipoProduto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProdutoForm {

    @NotBlank(message = "Informe o nome.")
    @Size(max = 200, message = "O nome deve ter no máximo 200 caracteres.")
    private String nome;

    @NotBlank(message = "Informe a marca.")
    @Size(max = 100, message = "A marca deve ter no máximo 100 caracteres.")
    private String marca;

    @Size(max = 150, message = "O princípio ativo deve ter no máximo 150 caracteres.")
    private String principioAtivo;

    private TipoProduto tipo;

    @Size(max = 30, message = "A forma farmacêutica deve ter no máximo 30 caracteres.")
    private String formaFarmaceutica;

    private Tarja tarja = Tarja.SEM_TARJA;

    private boolean exigeReceita;

    @Size(max = 30, message = "O registro deve ter no máximo 30 caracteres.")
    private String registroMs;

    @Size(max = 255, message = "A URL da bula deve ter no máximo 255 caracteres.")
    private String bulaUrl;

    private String indicacao;
    private String acao;
    private String viaAdministracao;
    private String publicoAlvo;

    private boolean ativo = true;

    private List<Long> categoriaIds = new ArrayList<>();

    private List<ApresentacaoForm> apresentacoes = new ArrayList<>();

    /** Fotos novas desta edição. */
    private List<MultipartFile> novasImagens = new ArrayList<>();

    /** Apresentação das fotos novas; nulo = vale para todas. */
    private Long imagemApresentacaoId;

    /** Fotos já salvas a remover. */
    private List<Long> removerImagemIds = new ArrayList<>();

    @Getter
    @Setter
    public static class ApresentacaoForm {
        private Long id;
        /** Versão (lock otimista) lida ao abrir o formulário. */
        private Long versao;
        private String descricao;
        private String sku;
        private String ean;
        private BigDecimal preco;
        private Integer estoque;
        private Integer limitePorPedido = 10;
        private boolean ativa = true;

        public boolean isVazia() {
            return id == null && isBlank(descricao) && isBlank(sku) && isBlank(ean) && preco == null
                    && estoque == null;
        }

        private static boolean isBlank(String s) {
            return s == null || s.isBlank();
        }
    }
}
