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
   git clone <url-do-repositorio>
   cd farmacia-online
   ```

2. Subir o banco de dados PostgreSQL via Docker:
   ```bash
   docker compose up -d
   ```
   Isso sobe um Postgres em `localhost:5433` com um volume persistente
   (os dados não se perdem ao reiniciar o container).
   Ao abrir esse link no navegador, nenhuma página deve ser carregada: apenas irá aparecer um aviso do próprio navegador de que "localhost não enviou quaisquer dados".

3. Rodar a aplicação:
   ```bash
   ./mvnw spring-boot:run        # Linux/Mac
   ./mvnw.cmd spring-boot:run      # Windows
   ```
   Ou, pela IDE, rodar a classe principal `FarmaciaOnlineApplication`.

4. Acessar em [http://localhost:8080](http://localhost:8080).

5. Para parar o banco:
   ```bash
   docker compose down
   ```
   (os dados continuam salvos no volume; só sobem de novo com `docker compose up -d`)

## Dados de demonstração

Num banco novo, o Flyway aplica `V1__init.sql` (tabelas) e `V2__seed.sql` (categorias, produtos,
lojas e usuários de teste). Quem subir o banco do zero recebe os mesmos dados.

| E-mail | Perfil | Status | Senha |
|---|---|---|---|
| `admin@farma.com` | ADMIN | ATIVA | `Farma@123` |
| `cliente@farma.com` | CLIENTE | ATIVA | `Farma@123` |
| `pendente@farma.com` | CLIENTE | PENDENTE (para testar a confirmação de conta) | `Farma@123` |

Essas contas existem só para desenvolvimento e demonstração.

- **Recomeçar do zero:** `docker compose down -v` apaga o volume do banco; depois `docker compose up -d`
  e suba a aplicação. Quem aplicou uma versão antiga do `V2` localmente precisa disso.
- **Não edite uma migração que já está na `main`:** o Flyway guarda o checksum e a aplicação deixa de
  subir em quem já a aplicou. Para mudar dados, crie uma migração nova (`V3__...sql`).
- **Textos com várias linhas no seed:** use `E'linha 1\nlinha 2'`. Quebrar a linha dentro das aspas
  grava um fim de linha diferente no Windows e no Linux.
- **Imagens de produto:** ficam em `src/main/resources/static/images/produtos/`, em WebP de até 800 px,
  com nome descritivo em minúsculas e hífens (ex.: `dipirona-monoidratada-500mg-20-comprimidos.webp`).
  A coluna `imagem_produto.arquivo` guarda o caminho a partir da raiz do site (`/images/produtos/...`).
  Produto sem foto usa `placeholder.svg`. O texto alternativo (`alt`) vem do nome do produto.

## Testes e integração contínua

```bash
./mvnw verify          # Linux/Mac
./mvnw.cmd verify      # Windows
```

**Os testes precisam do Docker ligado** (Docker Desktop aberto no Windows/Mac). Eles usam o
[Testcontainers](https://java.testcontainers.org/): a cada execução sobe um PostgreSQL 16
descartável, aplica as migrações do Flyway e roda o `validate` do Hibernate. Ou seja, os testes
rodam contra um Postgres de verdade, **sem depender do banco do `docker compose`** (pode estar
desligado) e sem tocar nos seus dados. Na primeira vez o Docker baixa a imagem `postgres:16`.

- `FarmaciaOnlineApplicationTests.contextLoads` é o teste mais importante da Fase 1: falha se uma
  entidade JPA não bater com as tabelas criadas pelas migrações.
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
├── controller       (Controllers Thymeleaf)
├── model            (Entidades JPA)
├── repository       (Interfaces Spring Data JPA)
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
│   └── index.html
└── application.properties

docs
└── acessibilidade.md  (checklist de acessibilidade para PRs e como testar)

src/test/java/com/farmacia/farmaciaonline
├── TestcontainersConfiguration.java       (PostgreSQL 16 descartável via Docker, para testes com banco)
├── FarmaciaOnlineApplicationTests.java    (contextLoads: Flyway + validate do Hibernate num Postgres real)
└── SeedDemonstracaoTest.java              (confere o V2: usuários, catálogo, EAN-13, sequences)

.github/workflows
└── ci.yml             (CI: ./mvnw -B verify em PR e push na main)
```

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
campos de formulário com o fragment `fragments/campo`. Veja o checklist completo em
[`docs/acessibilidade.md`](docs/acessibilidade.md) e cole-o na descrição do PR. O teste
`LayoutTest` confere o layout das páginas existentes — adicione a nova página nele.

## Fluxo de Pull Request

1. Toda mudança entra por PR — ninguém commita direto na `main`.
2. PR precisa de descrição curta: o que foi feito e como testar localmente.
3. Pelo menos 1 aprovação de outro integrante antes do merge.
4. Preferir PRs pequenos e focados numa única funcionalidade.
5. Usar "Squash and merge" pra manter o histórico da `main` limpo.
6. O CI (GitHub Actions) precisa estar verde para o merge.
