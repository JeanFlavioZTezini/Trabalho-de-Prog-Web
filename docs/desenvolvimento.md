# Desenvolvimento

Guia para quem vai mexer no código. Para rodar o projeto, veja o [README](../README.md); para as regras de cada tela,
[funcionalidades.md](funcionalidades.md).

## Ambiente

- **IntelliJ IDEA:** o Lombok já vem embutido; se as anotações não forem reconhecidas, habilite
  `Settings → Build, Execution, Deployment → Compiler → Annotation Processors → Enable annotation processing`.
- **VS Code:** extensão **Extension Pack for Java**. Se o Lombok não for reconhecido, rode
  `Java: Clean Java Language Server Workspace` (`Ctrl+Shift+P`).
- Para ver o SQL de uma execução (PowerShell): `$env:SPRING_JPA_SHOW_SQL="true"; .\mvnw.cmd spring-boot:run`.

## Banco e migrations

O Flyway aplica `src/main/resources/db/migration` na subida. O Hibernate só valida (`ddl-auto=validate`).

| Versão | Conteúdo | Origem |
|---|---|---|
| `V1` | schema | fundação |
| `V2` | seed de demonstração (contas, categorias, produtos, lojas) | fundação |
| `V3` | `usuario.email_confirmado_em` e `celular_confirmado_em` | F-05 |
| `V4` | checkout: `pedido.versao`, chave de idempotência, cópia da loja (`retirada_*`), índices de pagamento | F-14 |
| `V5` | `historico_status_pedido.status_anterior` | F-15 |
| `V6` | conferência da receita e estorno pendente no pedido | F-17 |
| `V7` | fotos dos produtos do seed | F-08 |
| `V8` | extensões `unaccent` e `pg_trgm` da busca tolerante | F-22 |
| `V9` | `endereco.principal` | F-25 |
| `V10` | fotos da Neo Dipirona e da Loratadina (marca da Loratadina passa a Cimed) | revisão visual |
| `V11` | lojas de retirada em Campo Grande/MS | lojas e ViaCEP |
| `V12` | `produto.versao` e `categoria.versao` (edição simultânea no admin) | polimento |
| `V13` | fotos de Omeprazol, Amoxicilina e Clonazepam (marcas Avivia, Mintlab e Milab) | polimento |

- **Nunca edite uma migration que já está na `main`:** o Flyway guarda o checksum e a aplicação deixa de subir em quem já
  a aplicou. Para mudar dados, crie a próxima (hoje, `V14`).
- **Duas branches com o mesmo número quebram o Flyway no merge.** Antes de abrir PR com migration, confira a `main` e os
  PRs abertos. Conflito se resolve renumerando a migration da branch, não apagando o banco.
- Texto com várias linhas no SQL: `E'linha 1\nlinha 2'` (quebrar a linha dentro das aspas grava fins de linha diferentes
  no Windows e no Linux).
- `MigracaoUpgradeIntegracaoTest` parte de um banco na `V2` com dados e aplica todas as migrations sem perder nada.

**Imagens de produto:** WebP de até 800 px em `src/main/resources/static/images/produtos/`, nome em minúsculas com hífens
(ex.: `dipirona-monoidratada-500mg-20-comprimidos.webp`). A coluna `imagem_produto.arquivo` guarda o caminho a partir da
raiz do site; produto sem foto usa `placeholder.svg` e o `alt` vem do nome do produto. As fotos do seed vêm de lojas e
fabricantes e servem só para a demonstração acadêmica.

## Modelo de dados: o que não é óbvio

- **Apresentação** guarda preço, SKU, EAN e estoque. `ImagemProduto.apresentacao` nula vale para todas as apresentações.
  As coleções de `Produto` são LAZY e sem cascade: grave `Apresentacao` e `ImagemProduto` pelos próprios repositories.
  As telas leem essas coleções durante a renderização (`spring.jpa.open-in-view=true`); `@BatchSize` evita uma consulta
  por produto na home.
- **Carrinho** tem um dono só: o usuário ou o token do visitante (o banco recusa os dois ou nenhum). `Carrinho.itens` tem
  `cascade = ALL` e `orphanRemoval`. `ItemCarrinho.precoVisto` é o preço na hora em que o item entrou.
- **Pedido** guarda cópias: endereço (`EnderecoEntrega`, colunas `entrega_*`), loja (`retirada_*`) e, nos itens, nome,
  SKU, preço e receita da hora da compra. Chame `Pedido#recalcularTotais()` antes de gravar (o banco exige
  `total = subtotal + frete`). A regra "receita só com retirada" é do `CheckoutService`, não do banco.
- **Status do pedido** só muda pelo `EstadoPedidoService` (checkout, expiração e admin), que trava a linha e grava o
  histórico com o status anterior. Para o cliente, busque com `PedidoRepository#findByIdAndUsuarioId`.
- **Endereço do cliente** se busca com `EnderecoService#buscar(usuarioId, id)` (nunca `findById`), para manter o 404 de
  endereço alheio. `CartaoSalvo` guarda só token e dados mascarados: nunca número completo nem CVV.

## Segurança: regras para código novo

- **Rota nova** exige login por padrão. Página aberta a visitantes entra em `ROTAS_PUBLICAS` no `SecurityConfig`; rota de
  administração fica sob `/admin/**`.
- **CSRF:** todo formulário `POST` usa `th:action` (o Thymeleaf coloca o token). Formulário com `action` escrito à mão
  recebe 403. `fetch` com `POST` envia o token no cabeçalho `X-CSRF-TOKEN`.
- Não remova `spring.thymeleaf.servlet.produce-partial-output-while-processing=false`: sem ela, o token CSRF não consegue
  criar a sessão de quem abre o `/login` numa aba anônima (`LoginAnonimoTest`).
- Entrada de tela é sempre um `*Form` enxuto: nunca receba perfil, status, senha pronta ou id de dono do formulário, e
  cubra com um teste de "campo forjado é ignorado". Recurso de outra pessoa responde 404.
- Senha, código de verificação e CPF completo nunca vão para log.

## Testes

```bash
.\mvnw.cmd verify                        # tudo (Docker ligado)
.\mvnw.cmd test -Dtest=NomeDoTeste       # um teste
```

- **Unitário** (`*Test`): sem Spring nem banco.
- **Tela:** `@WebMvcTest(Controller.class)` + `@Import(SecurityConfig.class)`, com os services simulados pelo Mockito e
  `@MockBean CategoriaRepository` (o menu do layout precisa dele). Não precisa de Docker.
- **Integração:** `@SpringBootTest` + `@Import(TestcontainersConfiguration.class)` (PostgreSQL 16 de verdade). Não use H2:
  o schema usa recursos que ele não tem. O seed existe em todo teste de integração.
- Os testes do checkout (`CheckoutTesteBase`) não são `@Transactional` de propósito: testam commits e travas, e criam os
  próprios dados sem apagar depois. Não assuma um banco limpo.
- Em teste de tela, confira o elemento de erro (`id="campo-erro"`), não só a frase: a ajuda do campo pode ter o mesmo texto.
- `Could not find a valid Docker environment`: o Docker está desligado. `client version ... is too old`: atualize o
  Docker Desktop.

## Página nova

Toda página usa o layout `templates/fragments/layout.html` (cabeçalho, menu, rodapé e "Pular para o conteúdo"):

```html
<!DOCTYPE html>
<html th:replace="~{fragments/layout :: layout(~{::title}, ~{::#pagina})}">
<head>
    <title>Minha página | Farma++</title>
</head>
<body>
<div id="pagina" class="pagina-texto">
    <h1>Título da página</h1>
    <!-- conteúdo -->
</div>
</body>
</html>
```

- Um único `<h1>`, títulos sem pular nível, links com `th:href="@{...}"`, imagem com `alt`, campo com `<label for>`.
- Campos com o fragmento `fragments/campo`; erro com `aria-invalid`, `aria-describedby` e `role="alert"`.
- Checkbox dentro de `<label class="campo__marcador">`; tabela larga dentro de `.tabela-rolavel`.
- Cores só pelos tokens de `static/css/tokens.css` (`var(--cor-...)`), tamanhos em `rem`.
- Sem rolagem horizontal em 360 px. Checklist completo em [acessibilidade.md](acessibilidade.md); acrescente a página ao
  `LayoutTest`.

## Fluxo de trabalho

- Combinado do grupo: toda mudança entra por PR (nada de push direto na `main`), com 1 aprovação de alguém de outra
  trilha e o CI (`verify`) verde antes do merge.
- **Branch:** `tipo/tema-curto` (`feat`, `fix`, `style`, `refactor`, `test`, `docs`, `chore`), um tema por branch.
- **Commit:** `tipo: o que mudou`, curto e em português.
- **PR:** o template (`.github/pull_request_template.md`) traz "O que muda", "Como testar" e o checklist. Atualize a
  tabela "Quem fez o quê" do README quando o PR for de uma parte nova.
