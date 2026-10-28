# Farmácia On-line

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
- [Docker](https://www.docker.com/) e Docker Compose instalados
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
│   ├── images
│   └── js             (menu.js: menu de categorias no mobile)
├── templates
│   ├── fragments      (layout.html: cabeçalho, rodapé e "Pular para o conteúdo" compartilhados;
│   │                   campo.html: campo de formulário acessível com rótulo, ajuda e erro)
│   ├── institucional  (páginas de texto, ex.: boas-praticas.html)
│   └── index.html
└── application.properties

docs
└── acessibilidade.md  (checklist de acessibilidade para PRs e como testar)
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
campos de formulário com o fragment `fragments/campo`. Veja o checklist completo em
[`docs/acessibilidade.md`](docs/acessibilidade.md) e cole-o na descrição do PR. O teste
`LayoutTest` confere o layout das páginas existentes — adicione a nova página nele.

## Fluxo de Pull Request

1. Toda mudança entra por PR — ninguém commita direto na `main`.
2. PR precisa de descrição curta: o que foi feito e como testar localmente.
3. Pelo menos 1 aprovação de outro integrante antes do merge.
4. Preferir PRs pequenos e focados numa única funcionalidade.
5. Usar "Squash and merge" pra manter o histórico da `main` limpo.
