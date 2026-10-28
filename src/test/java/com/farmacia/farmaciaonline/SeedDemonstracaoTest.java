package com.farmacia.farmaciaonline;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

/**
 * Confere, num PostgreSQL de verdade, o que a V2__seed.sql promete.
 * O que o teste escreve é desfeito no fim de cada teste (@Transactional).
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class SeedDemonstracaoTest {

    private static final String[] EMAILS_DE_TESTE = {"admin@farma.com", "cliente@farma.com", "pendente@farma.com"};

    private static final String ID_NOVALGINA = "(SELECT id FROM produto WHERE nome = 'Novalgina Dipirona 1g')";

    @Autowired
    private JdbcTemplate jdbc;

    private long contar(String sql, Object... argumentos) {
        Long total = jdbc.queryForObject(sql, Long.class, argumentos);
        return total == null ? 0 : total;
    }

    private String usuario(String coluna, String email) {
        return jdbc.queryForObject("SELECT " + coluna + " FROM usuario WHERE email = ?", String.class, email);
    }

    private static boolean ean13Valido(String ean) {
        if (ean == null || !ean.matches("[0-9]{13}")) {
            return false;
        }
        int soma = 0;
        for (int i = 0; i < 12; i++) {
            soma += (ean.charAt(i) - '0') * (i % 2 == 0 ? 1 : 3);
        }
        return (10 - soma % 10) % 10 == ean.charAt(12) - '0';
    }

    @Test
    void usuariosDeTesteTemPerfilStatusECpfDaIssue() {
        assertThat(usuario("perfil", "admin@farma.com")).isEqualTo("ADMIN");
        assertThat(usuario("status", "admin@farma.com")).isEqualTo("ATIVA");
        assertThat(usuario("cpf", "admin@farma.com")).isNull();

        assertThat(usuario("perfil", "cliente@farma.com")).isEqualTo("CLIENTE");
        assertThat(usuario("status", "cliente@farma.com")).isEqualTo("ATIVA");
        assertThat(usuario("cpf", "cliente@farma.com")).isEqualTo("52998224725");

        assertThat(usuario("perfil", "pendente@farma.com")).isEqualTo("CLIENTE");
        assertThat(usuario("status", "pendente@farma.com")).isEqualTo("PENDENTE");
        assertThat(usuario("cpf", "pendente@farma.com")).isEqualTo("11144477735");
    }

    @Test
    void usuariosDeTesteAceitaramOsTermosNaVersao1() {
        for (String email : EMAILS_DE_TESTE) {
            assertThat(usuario("termos_versao", email)).as(email).isEqualTo("1.0");
        }
    }

    @Test
    void senhaDosUsuariosDeTesteEFarmaArroba123() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        for (String email : EMAILS_DE_TESTE) {
            assertThat(encoder.matches("Farma@123", usuario("senha_hash", email))).as(email).isTrue();
        }
    }

    @Test
    void novalginaTemTresApresentacoesComPrecoEEstoqueDoPrototipo() {
        assertThat(contar("SELECT count(*) FROM apresentacao WHERE produto_id = " + ID_NOVALGINA)).isEqualTo(3);

        BigDecimal preco20 = jdbc.queryForObject("SELECT preco FROM apresentacao WHERE produto_id = "
                + ID_NOVALGINA + " AND descricao = '20 comprimidos'", BigDecimal.class);
        assertThat(preco20).isEqualByComparingTo("30.99");

        Integer estoque10 = jdbc.queryForObject("SELECT estoque FROM apresentacao WHERE produto_id = "
                + ID_NOVALGINA + " AND descricao = '10 comprimidos'", Integer.class);
        assertThat(estoque10).isZero();
    }

    @Test
    void apresentacaoPadraoDaNovalginaTemEstoque() {
        Integer estoque = jdbc.queryForObject("SELECT estoque FROM apresentacao WHERE produto_id = "
                + ID_NOVALGINA + " ORDER BY ordem LIMIT 1", Integer.class);
        assertThat(estoque).isPositive();
    }

    @Test
    void catalogoTemEntre15E20ProdutosEMarcasRepetidas() {
        assertThat(contar("SELECT count(*) FROM produto")).isBetween(15L, 20L);
        assertThat(contar("SELECT count(*) FROM "
                + "(SELECT marca FROM produto GROUP BY marca HAVING count(*) > 1) m")).isPositive();
    }

    @ParameterizedTest
    @ValueSource(strings = {"GENERICO", "SIMILAR", "REFERENCIA"})
    void existeMedicamentoDeCadaTipoComPrincipioAtivo(String tipo) {
        assertThat(contar("SELECT count(*) FROM produto WHERE tipo = ? AND principio_ativo IS NOT NULL", tipo))
                .isPositive();
    }

    @ParameterizedTest
    @ValueSource(strings = {"VERMELHA", "PRETA"})
    void existeProdutoComTarjaQueExigeReceita(String tarja) {
        assertThat(contar("SELECT count(*) FROM produto WHERE tarja = ? AND exige_receita", tarja)).isPositive();
    }

    @Test
    void existeProdutoAtivoSemEstoqueAlgumEProdutoInativo() {
        assertThat(contar("SELECT count(*) FROM produto p WHERE p.ativo AND NOT EXISTS "
                + "(SELECT 1 FROM apresentacao a WHERE a.produto_id = p.id AND a.estoque > 0)")).isPositive();
        assertThat(contar("SELECT count(*) FROM produto WHERE NOT ativo")).isPositive();
    }

    @Test
    void existemItensQueNaoSaoMedicamento() {
        assertThat(contar("SELECT count(*) FROM produto WHERE tipo IS NULL")).isGreaterThanOrEqualTo(4);
    }

    @Test
    void todoProdutoTemCategoriaApresentacaoEImagem() {
        assertThat(contar("SELECT count(*) FROM produto p WHERE NOT EXISTS "
                + "(SELECT 1 FROM produto_categoria c WHERE c.produto_id = p.id)")).isZero();
        assertThat(contar("SELECT count(*) FROM produto p WHERE NOT EXISTS "
                + "(SELECT 1 FROM apresentacao a WHERE a.produto_id = p.id)")).isZero();
        assertThat(contar("SELECT count(*) FROM produto p WHERE NOT EXISTS "
                + "(SELECT 1 FROM imagem_produto i WHERE i.produto_id = p.id)")).isZero();
    }

    @Test
    void categoriasTemRaizesComSubcategoriasEControladosEAntibioticosExigemReceita() {
        assertThat(contar("SELECT count(*) FROM categoria WHERE categoria_pai_id IS NULL")).isGreaterThanOrEqualTo(5);
        assertThat(contar("SELECT count(*) FROM categoria c WHERE c.categoria_pai_id IS NULL AND NOT EXISTS "
                + "(SELECT 1 FROM categoria f WHERE f.categoria_pai_id = c.id)")).isZero();

        for (String nome : new String[] {"Controlados", "Antibióticos"}) {
            Boolean exigeReceita = jdbc.queryForObject(
                    "SELECT exige_receita FROM categoria WHERE nome = ?", Boolean.class, nome);
            assertThat(exigeReceita).as(nome).isTrue();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"visualizacoes", "qtd_vendida"})
    void rankingsDaHomeMostramNovalginaEExercitamOsFiltros(String ordem) {
        String topAtivos = "(SELECT id, nome, exige_receita FROM produto WHERE ativo ORDER BY " + ordem + " DESC LIMIT 8) t";

        // Se o filtro "ativo" faltasse, o inativo apareceria no top 8: o seed precisa provar isso.
        assertThat(contar("SELECT count(*) FROM (SELECT ativo FROM produto ORDER BY " + ordem
                + " DESC LIMIT 8) t WHERE NOT ativo")).isPositive();
        assertThat(contar("SELECT count(*) FROM " + topAtivos + " WHERE nome LIKE 'Novalgina%'")).isEqualTo(1);
    }

    @Test
    void maisVistosMostraProdutoIndisponivelEProdutoQueExigeReceita() {
        String topAtivos = "(SELECT id, exige_receita FROM produto WHERE ativo ORDER BY visualizacoes DESC LIMIT 8) t";

        assertThat(contar("SELECT count(*) FROM " + topAtivos + " WHERE NOT EXISTS "
                + "(SELECT 1 FROM apresentacao a WHERE a.produto_id = t.id AND a.estoque > 0)")).isPositive();
        assertThat(contar("SELECT count(*) FROM " + topAtivos + " WHERE exige_receita")).isPositive();
    }

    @Test
    void temUmaOuDuasLojasParaRetirada() {
        assertThat(contar("SELECT count(*) FROM loja")).isBetween(1L, 2L);
    }

    @Test
    void todosOsEansSaoEan13Validos() {
        List<String> eans = jdbc.queryForList("SELECT ean FROM apresentacao", String.class);

        assertThat(eans).isNotEmpty();
        for (String ean : eans) {
            assertThat(ean13Valido(ean)).as(ean).isTrue();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"usuario", "categoria", "loja", "produto", "apresentacao", "imagem_produto"})
    void sequenceDoIdEstaAdiantadaDoMaiorIdDoSeed(String tabela) {
        Long maiorId = jdbc.queryForObject("SELECT max(id) FROM " + tabela, Long.class);
        Long proximo = jdbc.queryForObject(
                "SELECT nextval(pg_get_serial_sequence('" + tabela + "', 'id'))", Long.class);

        assertThat(proximo).isGreaterThan(maiorId);
    }

    @Test
    void inserirCategoriaSemIdFunciona() {
        int linhas = jdbc.update("INSERT INTO categoria (nome) VALUES ('Categoria criada pelo teste')");

        assertThat(linhas).isEqualTo(1);
    }
}
