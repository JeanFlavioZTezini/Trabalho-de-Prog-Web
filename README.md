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
   Isso sobe um Postgres em `localhost:5432` com um volume persistente
   (os dados não se perdem ao reiniciar o container).

3. Rodar a aplicação:
   ```bash
   ./mvnw spring-boot:run        # Linux/Mac
   mvnw.cmd spring-boot:run      # Windows
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
├── db/migration     (scripts Flyway: V1__init.sql, V2__seed.sql...)
├── static/css       (design tokens / estilos globais)
├── templates        (arquivos .html do Thymeleaf)
└── application.properties
```

## Fluxo de Pull Request

1. Toda mudança entra por PR — ninguém commita direto na `main`.
2. PR precisa de descrição curta: o que foi feito e como testar localmente.
3. Pelo menos 1 aprovação de outro integrante antes do merge.
4. Preferir PRs pequenos e focados numa única funcionalidade.
5. Usar "Squash and merge" pra manter o histórico da `main` limpo.

## Escopo do projeto

- Versão mínima garantida: `docs/Especificacao_Farmacia_ProgWeb.md`
- Versão meta (objetivo até 28/10): `docs/Especificacao_Farmacia_Meta.md`
- Guia de setup completo + diagramas: `docs/Guia_Setup_e_Diagramas.md`
