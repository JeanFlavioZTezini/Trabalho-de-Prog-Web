# Farmácia On-line

[![CI](https://github.com/JeanFlavioZTezini/Trabalho-de-Prog-Web/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/JeanFlavioZTezini/Trabalho-de-Prog-Web/actions/workflows/ci.yml)

Aplicação web de vendas de farmácia, desenvolvida como Trabalho Prático de
Programação Web (ProgWeb 2026/2), usando Spring Boot + Thymeleaf.

## Stack

- **Linguagem:** Java 17
- **Framework:** Spring Boot 3.5.16
- **Arquitetura:** MVC server-side rendering (Spring MVC + Thymeleaf) — Opção A do enunciado
- **Persistência:** Spring Data JPA + Hibernate
- **Banco de dados:** PostgreSQL (via Docker)
- **Migrações:** Flyway
- **Segurança:** Spring Security (login + roles)
- **Build:** Maven

## Pré-requisitos

- [JDK 17](https://adoptium.net/) instalado
- [Docker](https://www.docker.com/) e Docker Compose instalados — o Docker precisa estar **ligado** também para rodar os testes (ver [Testes e integração contínua](#testes-e-integração-contínua))
- Maven (ou usar o `mvnw`/`mvnw.cmd` incluído no projeto — não precisa instalar Maven à parte)

## Como rodar o projeto localmente

1. Clonar o repositório:
   ```bash
   git clone https://github.com/JeanFlavioZTezini/Trabalho-de-Prog-Web.git
   cd Trabalho-de-Prog-Web
   ```

2. Subir o banco de dados PostgreSQL via Docker (com o Docker ligado):
   ```bash
   docker compose up -d
   docker ps
   ```
   O `docker ps` deve mostrar `0.0.0.0:5433->5432/tcp`: é o Postgres em `localhost:5433`, com um volume
   persistente (os dados não se perdem ao reiniciar o container). Esse endereço não é uma página: o navegador
   não abre o banco.

3. Rodar a aplicação (a primeira vez baixa as dependências e demora alguns minutos):
   ```bash
   ./mvnw spring-boot:run          # Linux/Mac
   .\mvnw.cmd spring-boot:run      # Windows (PowerShell)
   ```
   Ou, pela IDE, rodar a classe principal `FarmaciaOnlineApplication`. Está pronto quando o log mostrar
   `Started FarmaciaOnlineApplication`.

4. Acessar [http://localhost:8080](http://localhost:8080). Para entrar como administrador, abra
   [http://localhost:8080/login](http://localhost:8080/login) e use `admin@farma.com` com a senha `Farma@123`:
   o login leva ao painel em `/admin`. Para entrar como cliente, use `cliente@farma.com` com a mesma senha.
   Todos os usuários de teste estão em [Dados de demonstração](#dados-de-demonstração).

5. Para parar a aplicação, `Ctrl+C` (no Windows, responda `S`). Para parar o banco:
   ```bash
   docker compose down
   ```
   (os dados continuam salvos no volume; só sobem de novo com `docker compose up -d`)

Os testes têm passo próprio: ver [Testes e integração contínua](#testes-e-integração-contínua).

## Dados de demonstração

Num banco novo, o Flyway aplica `V1__init.sql` (tabelas) e `V2__seed.sql` (categorias, produtos,
lojas e usuários de teste). Quem subir o banco do zero recebe os mesmos dados.

| E-mail | Perfil | Status | Senha |
|---|---|---|---|
| `admin@farma.com` | ADMIN | ATIVA | `Farma@123` |
| `cliente@farma.com` | CLIENTE | ATIVA | `Farma@123` |
| `pendente@farma.com` | CLIENTE | PENDENTE (para testar a confirmação de conta) | `Farma@123` |

Essas contas existem só para desenvolvimento e demonstração.

- **Recomeçar do zero:** ver [Como zerar o banco para a demonstração](#como-zerar-o-banco-para-a-demonstração).
  Quem aplicou uma versão antiga do `V2` localmente precisa disso.
- **Não edite uma migração que já está na `main`:** o Flyway guarda o checksum e a aplicação deixa de
  subir em quem já a aplicou. Para mudar dados, crie uma migração nova (`V3__...sql`).
- **Textos com várias linhas no seed:** use `E'linha 1\nlinha 2'`. Quebrar a linha dentro das aspas
  grava um fim de linha diferente no Windows e no Linux.
- **Imagens de produto:** ficam em `src/main/resources/static/images/produtos/`, em WebP de até 800 px,
  com nome descritivo em minúsculas e hífens (ex.: `dipirona-monoidratada-500mg-20-comprimidos.webp`).
  A coluna `imagem_produto.arquivo` guarda o caminho a partir da raiz do site (`/images/produtos/...`).
  Produto sem foto usa `placeholder.svg`. O texto alternativo (`alt`) vem do nome do produto.

## Como zerar o banco para a demonstração

Antes de apresentar, ou sempre que quiser o banco como no seed:
```bash
docker compose down -v
docker compose up -d
```
(Se o seu terminal aceitar `&&`, dá para juntar: `docker compose down -v && docker compose up -d`.)
Depois, suba a aplicação: o Flyway recria as tabelas (`V1`) e os dados de demonstração (`V2`).

O `-v` apaga o volume: cadastros, carrinhos e pedidos feitos nos testes somem. Sem o `-v`, o banco só é desligado
e os dados continuam lá.

## Segurança e papéis

Spring Security com dois papéis, `CLIENTE` e `ADMIN` (coluna `usuario.perfil`; viram `ROLE_CLIENTE` e
`ROLE_ADMIN`). As regras ficam em `config/SecurityConfig.java`:

| Rota | Quem acessa |
|---|---|
| `/`, `/produtos/**`, `/carrinho/**`, `/login`, `/cadastro/**`, `/confirmar-conta/**`, `/institucional/**`, `/css/**`, `/images/**`, `/js/**`, `/error` | Qualquer pessoa (visitante inclusive) |
| `/admin/**` | Só `ADMIN`. Visitante é mandado para `/login`; cliente logado recebe a página 403 |
| Todo o resto | Qualquer usuário logado |

**Login:** o campo aceita **e-mail ou CPF** (com ou sem pontos e traço). Se, ao tirar tudo que não é dígito,
sobrarem 11 dígitos, é CPF; texto com `@` é sempre e-mail. Contas `PENDENTE` e `EXCLUIDA` não entram, e conta
com `bloqueado_ate` no futuro também não. Depois de entrar, o `ADMIN` vai para `/admin` e o cliente volta para a
página que tentou abrir (ou `/`). O cabeçalho mostra o primeiro nome e um botão **Sair**.

**CSRF está ligado.** Todo formulário `POST` precisa do token, e o Thymeleaf o coloca sozinho **se o formulário
usar `th:action`** (nunca `action="..."` escrito à mão). Formulário sem token recebe 403. O logout é um `POST` pelo
mesmo motivo (o botão **Sair** do cabeçalho já é um formulário). Quem fizer `fetch`/AJAX com `POST` precisa enviar
o token no cabeçalho `X-CSRF-TOKEN`.

**Rota nova pública?** Qualquer URL que não esteja na tabela acima exige login. Se a sua página for aberta a
visitantes (ex.: uma nova página institucional), acrescente o caminho em `ROTAS_PUBLICAS` no `SecurityConfig`.
Rota de administração: coloque sob `/admin/**`.

**Classes:** `security/UsuarioDetailsService` (busca por e-mail ou CPF e recusa contas que não podem entrar),
`security/UsuarioAutenticado` (o principal na sessão: id, nome, perfil), `security/LoginSuccessHandler` e
`security/LoginFailureHandler` (para onde ir depois do login, e qual mensagem mostrar quando falha).

**Para a apresentação:** o enunciado descreve o usuário comum como "somente listagem/visualização", mas aqui o
sistema é uma loja virtual: o papel `CLIENTE` também mexe no **próprio** carrinho e nos **próprios** pedidos
(sem isso não existiria compra). O que é exclusivo do `ADMIN` é todo CRUD de catálogo e a moderação.

Fica para a Fase 2 (UC10): login social, recuperação de senha e o bloqueio automático após várias senhas erradas
(hoje o sistema só **respeita** o `bloqueado_ate` que já estiver preenchido).

## Home e menu de categorias

Tudo vem do banco; nada é fixo na tela.

- **Menu:** `NavegacaoAdvice` coloca `menuCategorias` (categorias raiz ativas e suas filhas, por `ordem`) no modelo de
  toda página renderizada por controller, e o `fragments/layout.html` monta o `<nav>`. Categoria nova ou inativada
  muda o menu no próximo carregamento. Quem tem subcategorias ganha um botão (`aria-expanded`) que abre com
  Enter ou clique e fecha com Esc ou clicando fora (`static/js/menu.js`).
- **Home:** `HomeController` envia `maisComprados` (por `qtdVendida`) e `maisVistos` (por `visualizacoes`), só com
  `ativo = true` e no máximo 8. O card mostra a primeira imagem (ou o `placeholder.svg`, se o produto não tiver
  nenhuma), o nome, a apresentação padrão com o preço em reais (`spring.web.locale=pt_BR` e
  `#numbers.formatCurrency`) e os selos "Indisponível" e "Exige receita".
- **Consultas:** `Produto.apresentacoes` e `Produto.imagens` usam `@BatchSize`, então a home não faz uma consulta por
  produto. O log do SQL vem desligado; para conferir, ligue-o só nesta execução (PowerShell):
  `$env:SPRING_JPA_SHOW_SQL="true"; .\mvnw.cmd spring-boot:run`, abra a home e conte as consultas.
- **Testes:** `FarmaciaOnlineApplicationTests` confere a home com o seed (Novalgina, preço, selos, menu) e
  `HomeIntegracaoTest` cobre produto sem imagem e categoria inativa.

## Testes e integração contínua

```bash
./mvnw verify          # Linux/Mac
.\mvnw.cmd verify      # Windows (PowerShell)
```

**Os testes precisam do Docker ligado** (Docker Desktop aberto no Windows/Mac). Eles usam o
[Testcontainers](https://java.testcontainers.org/): a cada execução sobe um PostgreSQL 16
descartável, aplica as migrações do Flyway e roda o `validate` do Hibernate. Ou seja, os testes
rodam contra um Postgres de verdade, **sem depender do banco do `docker compose`** (pode estar
desligado) e sem tocar nos seus dados. Na primeira vez o Docker baixa a imagem `postgres:16`.

- `FarmaciaOnlineApplicationTests.contextLoads` é o teste mais importante da Fase 1: falha se uma
  entidade JPA não bater com as tabelas criadas pelas migrações.
- Segurança (F1-09): `SegurancaTest`, `LoginPageTest`, `UsuarioDetailsServiceTest` e `LoginHandlersTest` não
  precisam de Docker. `LoginIntegracaoTest` faz o login de verdade contra o seed e precisa.
- Teste que precisa de banco: use `@Import(TestcontainersConfiguration.class)` (com `@SpringBootTest`,
  ou com `@DataJpaTest` + `@AutoConfigureTestDatabase(replace = Replace.NONE)`). Teste de
  tela/controller usa `@WebMvcTest` e não precisa de Docker. Não use H2: o schema usa recursos
  que ele não tem (`CHECK` com `~`, índices em expressão).

Se o teste falhar com `Could not find a valid Docker environment`, o Docker não está rodando.
Se aparecer `client version ... is too old` ou `too new`, atualize o Docker Desktop.

O **CI** (`.github/workflows/ci.yml`) roda `./mvnw -B verify` em todo PR e em todo push na `main`.
O resultado aparece no PR e na aba **Actions**; em caso de falha, os relatórios do Surefire ficam
disponíveis como artefato da execução.

## Configuração do IDE

### IntelliJ IDEA
- O suporte a Lombok já vem embutido nas versões recentes; se não reconhecer as
  anotações, instalar o plugin **Lombok** em `Settings → Plugins` e habilitar
  `Settings → Build, Execution, Deployment → Compiler → Annotation Processors →
  Enable annotation processing`.

### VS Code
- Instalar a extensão **Extension Pack for Java** (Microsoft) — o suporte a
  Lombok já vem embutido a partir da versão 1.9 da Language Server, não precisa
  de extensão separada.
- Se o Lombok não for reconhecido de primeira, rodar `Java: Clean Java Language
  Server Workspace` na paleta de comandos (`Ctrl+Shift+P`).

## Estrutura de pastas

```
com.farmacia.farmaciaonline
├── config          (SecurityConfig, WebConfig, etc.)
├── controller       (Controllers Thymeleaf; UsuarioLogadoAdvice entrega o primeiro nome ao cabeçalho)
├── model            (Entidades JPA)
├── repository       (Interfaces Spring Data JPA)
├── security         (UsuarioDetailsService, UsuarioAutenticado, handlers de login)
├── service          (Regras de negócio)
└── FarmaciaOnlineApplication.java

src/main/resources
├── db/migration       (scripts Flyway: V1__init.sql, V2__seed.sql...)
├── static
│   ├── css            (tokens.css = cores; style.css = estilos globais — carregar nessa ordem)
│   ├── images         (logo.png; produtos/ com as fotos e o placeholder.svg)
│   └── js             (menu.js: menu de categorias no mobile)
├── templates
│   ├── fragments      (layout.html: cabeçalho, rodapé e "Pular para o conteúdo" compartilhados;
│   │                   campo.html: campo de formulário acessível com rótulo, ajuda e erro)
│   ├── institucional  (páginas de texto, ex.: boas-praticas.html)
│   ├── admin          (painel do administrador, provisório)
│   ├── error          (403.html: o Spring Boot usa sozinho para o status 403)
│   ├── login.html
│   └── index.html
└── application.properties

docs
└── acessibilidade.md  (checklist de acessibilidade para PRs e como testar)

src/test/java/com/farmacia/farmaciaonline
├── controller/                            (LayoutTest, SegurancaTest, LoginPageTest: camada web, sem Docker)
├── security/                              (testes de login: service, handlers e integração com o seed)
├── TestcontainersConfiguration.java       (PostgreSQL 16 descartável via Docker, para testes com banco)
├── FarmaciaOnlineApplicationTests.java    (contextLoads: Flyway + validate do Hibernate num Postgres real)
└── SeedDemonstracaoTest.java              (confere o V2: usuários, catálogo, EAN-13, sequences)

.github/workflows
└── ci.yml             (CI: ./mvnw -B verify em PR e push na main)
```

## Modelo do catálogo

Entidades em `model` (`Categoria`, `Produto`, `Apresentacao`, `ImagemProduto`); o schema vem da `V1__init.sql`.

- **Preço, SKU, EAN e estoque ficam na `Apresentacao`** (10, 20 ou 30 comprimidos), nunca no `Produto`.
  O nome completo exibido ao cliente é `produto.nome + " " + apresentacao.descricao`.
- `Produto#getApresentacaoPadrao()` devolve a de menor `ordem` (sem olhar estoque nem se está ativa;
  `null` se o produto não tem apresentações). `Apresentacao#isDisponivel()` é "ativa e estoque > 0".
  `Produto#isIndisponivel()` é "nenhuma apresentação disponível" (sem apresentações também é indisponível).
- `ImagemProduto.apresentacao` nula significa que a imagem vale para todas as apresentações do produto.
- `Apresentacao.versao` (`@Version`) é o lock otimista do estoque. No checkout, duas compras simultâneas da
  mesma apresentação fazem a segunda falhar com `ObjectOptimisticLockingFailureException`: trate esse erro.
- As coleções de `Produto` (`categorias`, `apresentacoes`, `imagens`) são LAZY. Acesse dentro de transação ou
  com `JOIN FETCH`, senão dá `LazyInitializationException` ou consulta N+1. Não há cascade: grave cada
  `Apresentacao` e `ImagemProduto` pelo próprio repository.

## Como criar uma página nova

Toda página usa o layout `templates/fragments/layout.html` (cabeçalho com busca, menu de
categorias e área do usuário, rodapé e link "Pular para o conteúdo"). **Não copie cabeçalho nem
rodapé** — crie o arquivo assim:

```html
<!DOCTYPE html>
<html th:replace="~{fragments/layout :: layout(~{::title}, ~{::#pagina})}">
<head>
    <title>Minha página | Farma++</title>
</head>
<body>
<div id="pagina">
    <h1>Título da página</h1>
    <!-- conteúdo -->
</div>
</body>
</html>
```

Regras: um único `<h1>` por página (sem pular nível nos demais títulos); links com
`th:href="@{...}"`; toda imagem com `alt` (decorativa: `alt=""`); todo campo com `<label for>`;
cores sempre via `var(--cor-...)` do `tokens.css`, nunca hexadecimal solto; tamanhos em `rem`;
campos de formulário com o fragment `fragments/campo`; formulário `POST` sempre com `th:action` (o token CSRF vem
junto) e página aberta a visitantes precisa estar em `ROTAS_PUBLICAS` do `SecurityConfig`
(ver [Segurança e papéis](#segurança-e-papéis)). Veja o checklist completo em
[`docs/acessibilidade.md`](docs/acessibilidade.md) e cole-o na descrição do PR. O teste
`LayoutTest` confere o layout das páginas existentes — adicione a nova página nele.

## Fluxo de Pull Request

1. Toda mudança entra por PR — ninguém commita direto na `main`.
2. PR precisa de descrição curta: o que foi feito e como testar localmente.
3. Pelo menos 1 aprovação de outro integrante antes do merge.
4. Preferir PRs pequenos e focados numa única funcionalidade.
5. Usar "Squash and merge" pra manter o histórico da `main` limpo.
6. O CI (GitHub Actions) precisa estar verde para o merge.

O repositório tem um template (`.github/pull_request_template.md`): todo PR novo já abre com as seções
**O que muda**, **Como testar** e o checklist. Preencha tudo, inclusive o `Closes #<número da issue>`.

### Convenção de branch, commit e PR

- **Branch:** `tipo/f1-NN-descricao-curta`, em minúsculas e com hífens. Tipos: `feat`, `fix`, `docs`, `style`,
  `test` e `chore`. Ex.: `feat/f1-04-entidades-carrinho-endereco-cartao-loja`.
- **Commit:** `tipo: verbo no imperativo e o que mudou`, curto, um assunto por commit. Ex.:
  `feat: adiciona entidades de pedido` e `fix: corrige a ordem dos endereços`.
- **PR:** o título começa com o código da issue (`F1-05: Entidades e repositories de Pedido...`) e a descrição
  traz `Closes #<número da issue>`.

### Autoria dos commits

Para os commits aparecerem no seu perfil do GitHub, o nome e o e-mail do Git precisam ser os da sua conta.
Confira antes do primeiro commit:
```bash
git config user.name
git config user.email
```
Para corrigir (vale só neste repositório), use o e-mail cadastrado na sua conta do GitHub ou o endereço
`noreply` que ela mostra em *Settings > Emails*:
```bash
git config user.name "Seu Nome"
git config user.email "email-da-sua-conta-do-github"
```
A revisão é cruzada: quem aprova o PR é de outra trilha.

### Proteção da `main`

A `main` aceita mudanças só por PR: com 1 aprovação e o check `verify` (CI) verde, e com squash and merge.
Push direto na `main` é recusado. As regras ficam em *Settings > Rules* do repositório e quem as altera é o
dono do repositório.

## Quem fez o quê

Uma linha por módulo; **atualize esta tabela no seu PR** (o template lembra). Ela é o roteiro do item
"Divisão da equipe" da apresentação: cada integrante explica as linhas dele.

| Módulo | Issue | Integrante | PR |
|---|---|---|---|
| Banco: schema (`V1`), Docker e Flyway | F1-01 | _preencher_ | |
| Usuários: entidades e repositories | F1-02 (#2) | _preencher_ | |
| Catálogo: entidades e repositories | F1-03 (#7) | _preencher_ | |
| Compra: carrinho, endereço, cartão e loja | F1-04 (#13) | Kauê (@KaueAlves09) | |
| Pedido, pagamento, histórico e auditoria | F1-05 (#16) | Kauê (@KaueAlves09) | |
| Seed de demonstração (`V2`) | F1-06 (#8) | Kauê (@KaueAlves09) e _preencher_ (autor do `V2` original) | |
| Layout-base, cabeçalho, rodapé e acessibilidade da interface | F1-07 | Kauê (@KaueAlves09) | |
| Documento de acessibilidade | F1-08 | _preencher_ | |
| Login e papéis (Spring Security) | F1-09 | _preencher_ | |
| Docs e fluxo: template de PR, README e proteção da `main` | F1-10 (#4) | Kauê (@KaueAlves09) | |
| Testes com Testcontainers e CI | F1-11 (#9) | Kauê (@KaueAlves09) | |
| Home e menu de categorias pelo banco | F1-12 | _preencher_ | |
