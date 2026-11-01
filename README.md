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

Num banco novo, o Flyway aplica `V1__init.sql` (tabelas), `V2__seed.sql` (categorias, produtos,
lojas e usuários de teste) e as migrações seguintes. Quem subir o banco do zero recebe os mesmos dados.

| E-mail | Perfil | Status | Senha |
|---|---|---|---|
| `admin@farma.com` | ADMIN | ATIVA | `Farma@123` |
| `cliente@farma.com` | CLIENTE | ATIVA | `Farma@123` |
| `pendente@farma.com` | CLIENTE | PENDENTE (para testar a confirmação de conta) | `Farma@123` |

Essas contas existem só para desenvolvimento e demonstração.

- **Recomeçar do zero:** ver [Como zerar o banco para a demonstração](#como-zerar-o-banco-para-a-demonstração).
  Quem aplicou uma versão antiga do `V2` localmente precisa disso.
- **Não edite uma migração que já está na `main`:** o Flyway guarda o checksum e a aplicação deixa de
  subir em quem já a aplicou. Para mudar dados, crie uma migração nova com o próximo número livre (hoje, `V6`).
- **Numeração das migrações:** duas branches com o mesmo número quebram o Flyway no merge. Antes de abrir PR com
  migração, confira a `main` e as branches abertas e avise o grupo do número escolhido.

  | Versão | Conteúdo | Issue |
  |---|---|---|
  | `V1` | schema | fundação |
  | `V2` | seed de demonstração | fundação |
  | `V3` | `usuario.email_confirmado_em` e `celular_confirmado_em` | F-05 |
  | `V4` | checkout: `pedido.versao`, chave de idempotência, cópia da loja (`retirada_*`), índices de pagamento | F-14 |
  | `V5` | `historico_status_pedido.status_anterior` | F-15 |
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

**Formulário para visitante (sem sessão):** o token CSRF cria a sessão na hora em que o formulário é montado. Se o
Thymeleaf já tiver enviado parte da página ao navegador, a sessão não pode mais ser criada e o `/login` quebra para
quem abre numa aba anônima (`Cannot create a session after the response has been committed`). Por isso o
`application.properties` tem `spring.thymeleaf.servlet.produce-partial-output-while-processing=false`: a página só é
enviada depois de pronta. Não remova essa linha. O `LoginAnonimoTest` abre o `/login` num servidor de verdade, sem sessão.

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
├── cep              (ConsultaCep: porta de consulta de CEP; demo/DemoCepAdapter)
├── config           (SecurityConfig, WebConfig, etc.)
├── controller       (Controllers Thymeleaf; UsuarioLogadoAdvice entrega o primeiro nome ao cabeçalho)
├── model            (Entidades JPA)
├── repository       (Interfaces Spring Data JPA)
├── security         (UsuarioDetailsService, UsuarioAutenticado, handlers de login)
├── service          (Regras de negócio)
├── validacao        (CpfValido: validação do CPF pelos dígitos verificadores)
├── mensagem         (EnvioDeMensagem: envio de e-mail e SMS; demo/DemoEnvioAdapter guarda na memória)
└── FarmaciaOnlineApplication.java

src/main/resources
├── db/migration       (scripts Flyway: V1__init.sql, V2__seed.sql, V3 a V5...)
├── static
│   ├── css            (tokens.css = cores; style.css = estilos globais — carregar nessa ordem)
│   ├── images         (logo.png; produtos/ com as fotos e o placeholder.svg)
│   └── js             (menu.js: menu de categorias no mobile)
├── templates
│   ├── fragments      (layout.html: cabeçalho, rodapé e "Pular para o conteúdo" compartilhados;
│   │                   campo.html: campo de formulário acessível com rótulo, ajuda e erro)
│   ├── institucional  (páginas de texto, ex.: boas-praticas.html)
│   ├── admin          (painel do administrador, provisório)
│   ├── enderecos      (lista, formulário e exclusão dos endereços do cliente)
│   ├── checkout       (escolha, resumo e tela do pedido)
│   ├── error          (403.html, 404.html e 5xx.html: o Spring Boot usa sozinho conforme o status)
│   ├── login.html
│   └── index.html
└── application.properties

docs
└── acessibilidade.md  (tabela de contraste, checklist de acessibilidade para PRs e como testar)

src/test/java/com/farmacia/farmaciaonline
├── controller/                            (LayoutTest, SegurancaTest, LoginPageTest: camada web, sem Docker)
├── security/                              (testes de login: service, handlers e integração com o seed)
├── TestcontainersConfiguration.java       (PostgreSQL 16 descartável via Docker, para testes com banco)
├── FarmaciaOnlineApplicationTests.java    (contextLoads: Flyway + validate do Hibernate num Postgres real)
├── SeedDemonstracaoTest.java              (confere o V2: usuários, catálogo, EAN-13, sequences)
├── MapeamentoDasTabelasTest.java          (toda tabela da V1 tem uma entidade JPA: pega entidade que sumiu)
├── model/                                 (testes unitários puros, sem Spring nem banco)
└── repository/                            (testes de integração com @DataJpaTest contra o Postgres real:
                                            use CatalogoRepositoryTest e CompraRepositoryTest como modelo)

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
- A vitrine e `GET /produtos` exibem o preço da primeira apresentação ativa por ordem
  (`Produto#getApresentacaoReferenciaAtiva()`). Produtos sem estoque continuam no catálogo, a menos
  que o filtro "Somente disponíveis" seja selecionado. A listagem aceita termo (`q`), categoria,
  preço mínimo/máximo, disponibilidade, ordenação por nome/preço e paginação; os links de página
  preservam os filtros. A busca também consulta a indicação cadastrada, mas isso não é diagnóstico
  nem substitui orientação profissional. Sem termo, a listagem mostra o catálogo ativo sem busca textual.
- `ImagemProduto.apresentacao` nula significa que a imagem vale para todas as apresentações do produto.
- `Apresentacao.versao` (`@Version`) protege a edição do produto no admin: quem abriu a tela antes de uma compra não
  sobrescreve o estoque. O checkout não depende dela para disputar estoque: usa atualização condicional
  (`ApresentacaoRepository#reservarEstoque`), que também sobe a versão.
- As coleções de `Produto` (`categorias`, `apresentacoes`, `imagens`) são LAZY. Acesse dentro de transação ou
  com `JOIN FETCH`, senão dá `LazyInitializationException` ou consulta N+1. Não há cascade: grave cada
  `Apresentacao` e `ImagemProduto` pelo próprio repository.

## Modelo de compra

Entidades em `model` (`Carrinho`, `ItemCarrinho`, `Endereco`, `CartaoSalvo`, `Loja`); o schema vem da `V1__init.sql`.

- **O carrinho tem um dono só:** um `Usuario` (logado) **ou** um `token` UUID guardado em cookie (visitante).
  O banco recusa carrinho sem dono ou com os dois; `Carrinho#isDeVisitante()` diz qual é. Cada usuário tem no
  máximo um carrinho, e cada apresentação aparece uma vez por carrinho (adicionar de novo soma a quantidade).
  No login, o carrinho do token é mesclado ao do usuário (I12).
- **Itens:** `Carrinho.itens` tem `cascade = ALL` e `orphanRemoval = true`. Para adicionar, coloque o item na lista
  (com `item.setCarrinho(carrinho)`); para remover, tire da lista. Os itens vêm ordenados por `id`.
- **`ItemCarrinho.precoVisto`** é o preço no momento em que o item entrou no carrinho. `getSubtotal()` usa
  esse preço; comparar com `Apresentacao.preco` detecta que o preço mudou (UC05-E3).
- **`CartaoSalvo` guarda só o token do gateway e dados mascarados.** Nunca grave número completo nem CVV (LGPD e PCI DSS).
- `Endereco` salvo não é o endereço do pedido: o pedido guarda uma cópia, então editar ou excluir um endereço não
  altera pedidos antigos.
- `Loja` é o ponto de retirada. Produto que exige receita só pode ser retirado em loja (I3).

## Modelo de pedido

Entidades em `model` (`Pedido`, `ItemPedido`, `Pagamento`, `HistoricoStatusPedido`, `LogAuditoria`); o schema vem da `V1__init.sql`,
com os acréscimos da `V4` (checkout) e da `V5` (`status_anterior`).

- **O pedido guarda cópias (snapshots).** O endereço de entrega vai no `@Embeddable EnderecoEntrega`, que o `Pedido`
  mapeia para as colunas `entrega_*`, e o `ItemPedido` guarda nome, descrição, SKU, preço e `exigeReceita` do momento
  da compra. Mudar um `Endereco` salvo ou o catálogo não altera pedidos antigos.
- **Entrega x retirada:** entrega tem `enderecoEntrega` e não tem `lojaRetirada`; retirada tem `lojaRetirada`, frete zero
  e `enderecoEntrega` nulo. O banco recusa as outras combinações.
- **Chame `Pedido#recalcularTotais()` antes de gravar.** O banco exige `total = subtotal + frete`. O método arredonda
  cada item e o frete para 2 casas (HALF_UP) e depois soma, e trata frete nulo como zero.
- **Regra que o banco não garante:** pedido com item que exige receita só pode ser RETIRADA. `Pedido#temItemQueExigeReceita()`
  só consulta; quem valida é o `CheckoutService`, com teste próprio.
- `Pedido.itens` tem `cascade = ALL`: grave o pedido e os itens vão juntos. Pagamentos e histórico são gravados pelos
  próprios repositories.
- Um pedido tem várias tentativas de `Pagamento` (UC08-E2). Do cartão só ficam dados mascarados, e parcelas acima de 1
  só no crédito.
- `HistoricoStatusPedido.alteradoPor` nulo significa que a mudança foi do sistema.
- `HistoricoStatusPedido.statusAnterior` é o status de onde o pedido saiu (UC17). É nulo no registro de criação do pedido e
  nos eventos gravados antes da `V5`, que não guardaram a origem: a migration não a reconstitui. Quem muda o status lê o
  anterior **antes** de chamar `setStatus` e o passa ao registrar o histórico (`EstadoPedidoService#registrarHistorico`);
  a F-17 deve seguir o mesmo caminho.
- `PedidoRepository#findByIdAndUsuarioId` é o jeito de buscar um pedido para o cliente: de outro usuário, volta vazio (UC06).
- `PRONTO_PARA_RETIRADA` é o status do pedido de retirada em loja. Quem decide as transições entre status é o service (Fase 2).

## Endereços do cliente (F-11 / UC16)

Telas em `/enderecos` (lista, `novo`, `{id}/editar`, `{id}/excluir`); exigem login (regra "todo o resto" do
`SecurityConfig`). Código: `EnderecoController`, `EnderecoService`, `EnderecoForm`, `EnderecoRepository`.

- **Dono é sempre o usuário da sessão.** Nenhum método lê id de usuário da requisição, e o formulário não tem
  `id` nem usuário. `EnderecoRepository#findByIdAndUsuarioId` é o único jeito de buscar para o cliente: endereço
  de outra pessoa responde **404** (ler, editar, excluir, selecionar, buscar CEP e POST direto). Conta que não está
  `ATIVA` recebe 403.
- **Validação** (`EnderecoForm`): destinatário, CEP, logradouro, número, bairro, cidade e UF obrigatórios; CEP com 8
  dígitos (com ou sem traço, gravado só com dígitos); UF é uma das 27 siglas; complemento e referência opcionais.
- **Consulta de CEP** por adaptador: a tela depende da interface `cep/ConsultaCep`, e hoje só existe o
  `cep/demo/DemoCepAdapter`, com 7 CEPs fictícios (`01310-100`, `04094-050`, `01001-000`, `20040-020`, `30130-010`, `79002-000`, `79800-001`).
  CEP fora da lista = "não localizado"; `99999-999` simula o serviço fora do ar. Nos dois casos o formulário
  continua com o que já foi digitado e a pessoa preenche à mão. O botão "Buscar CEP" é um `POST` (funciona sem JavaScript). O Enter dentro do
  formulário **salva** (há um botão de envio escondido antes dele, porque o Enter aciona o primeiro botão do formulário).
- **Área atendida:** o endereço fora dela **pode ser salvo**; quem bloqueia a compra é o checkout.
- **Exclusão** pede confirmação (`/{id}/excluir`). Editar ou excluir **não altera pedidos antigos**: o pedido guarda a
  cópia `EnderecoEntrega`. `EnderecoService#copiaParaEntrega(usuarioId, enderecoId)` entrega essa cópia (só de
  endereço do próprio usuário) para o checkout/F-14 gravar no pedido.
- **Integração com o checkout:** o checkout abre `/enderecos?retorno=checkout` (ou `/enderecos/novo?retorno=checkout`).
  Só o valor exato `checkout` é aceito e o destino é fixo (`/checkout`), então nada que vem da URL vira
  redirecionamento. Salvar ou "Usar este endereço" volta para `/checkout?enderecoId=<id>`; cancelar volta para `/checkout`.
  **Quem fizer o checkout deve ler `enderecoId` e buscar o endereço por `EnderecoService`** (nunca por
  `EnderecoRepository#findById`), para manter a regra de 404 para endereço alheio.
- **Máscara do CEP:** `static/js/cep.js` coloca o hífen enquanto a pessoa digita e formata o CEP salvo ao editar. É só conforto: sem JavaScript o campo aceita com ou sem hífen.
- **Tela pequena:** a tabela da lista rola dentro da própria caixa (`.tabela-rolavel`), sem fazer a página rolar para o lado.
- **Testes:** `EnderecoFormTest` e `DemoCepAdapterTest` (sem Spring), `EnderecoControllerTest` (camada web, sem Docker)
  e `EnderecoServiceIntegracaoTest` (Docker ligado: isolamento entre clientes e pedido antigo intacto).

## Checkout (F-14 / UC08 e UC09)

Telas em `/checkout`; exigem login e conta `CLIENTE` `ATIVA` (administrador e conta pendente recebem 403). O carrinho de
quem está logado é a origem dos itens. A F-10 só precisa levar a pessoa para `/checkout` e manter `ItemCarrinho.precoVisto`.

| Rota | O que faz |
|---|---|
| `GET /checkout` | Escolha de entrega ou retirada, endereço, loja, pagamento e parcelas. Aceita `?enderecoId=` na volta de `/enderecos` |
| `GET /checkout/resumo` | Resumo calculado no servidor, antes de confirmar |
| `POST /checkout/precos` | Aceita os preços novos dos itens |
| `POST /checkout/confirmar` | Registra o pedido e faz a primeira cobrança |
| `GET /checkout/pedidos/{id}` | Confirmação e situação do pedido (pedido alheio responde 404) |
| `POST /checkout/pedidos/{id}/pagamento` | Nova tentativa no mesmo pedido |
| `POST /checkout/pedidos/{id}/atualizar` | "Já paguei": consulta o gateway |
| `POST /checkout/pedidos/{id}/resumo` | Reenvia o resumo que não foi enviado |
| `POST /checkout/pedidos/{id}/demo/{aprovar,expirar}` | Simula o evento de Pix/boleto. Só no perfil `demo` e só para o dono do pedido |

**Frete de demonstração** (`service/FreteDemo`). Só Mato Grosso do Sul é atendido. O CEP e a UF precisam combinar; o que não está na tabela não é atendido e
bloqueia a entrega. Retirada tem frete zero.

| UF | Faixa de CEP | Frete | Prazo |
|---|---|---|---|
| MS | 79000-000 a 79129-999 (Campo Grande) | R$ 9,90 | 2 dias |
| MS | 79130-000 a 79999-999 (demais cidades) | R$ 14,90 | 3 dias |

**Receita:** qualquer item que exige receita obriga a retirada em loja, inclusive em `POST` direto. A tela avisa que a
receita é apresentada na loja. Não há envio nem análise de receita pelo site (decisão do grupo).

**O servidor recalcula tudo.** Do navegador só vêm as escolhas. Não existe campo de total: um `total=` na requisição é
ignorado. O formulário leva uma `conferencia` (impressão digital do resumo mostrado): se o recalculado for diferente, a
pessoa volta ao resumo. Se `ItemCarrinho.precoVisto` difere do preço atual, a tela mostra o aviso e só deixa confirmar
depois de aceitar o preço novo.

**Política de pagamento e estoque** (uma só, em `EstadoPedidoService`; todo evento trava a linha do pedido antes de agir):

| Situação | Pedido | Estoque |
|---|---|---|
| Pedido registrado, ou Pix/boleto pendente | `AGUARDANDO_PAGAMENTO` | Reservado (já saiu de `estoque`) |
| Aprovação | `PAGAMENTO_APROVADO` | A reserva é consumida; soma em `qtd_vendida` |
| Recusa | Continua `AGUARDANDO_PAGAMENTO` | Continua reservado; nova tentativa no mesmo pedido, até `expira_em` |
| Expiração ou cancelamento | `CANCELADO` | Devolvido uma vez |

- **Validade da reserva:** Pix 30 minutos e boleto 3 dias (do gateway); nos demais casos (cartão recusado, resposta
  perdida), `app.checkout.validade-reserva` (30 minutos). O `ExpiracaoPedidosJob` roda a cada minuto
  (`app.checkout.expiracao.cron`) e, antes de expirar, consulta o gateway: Pix pago no último minuto vira aprovação.
- **Última unidade:** `ApresentacaoRepository#reservarEstoque` é um `UPDATE` condicional (`estoque >= quantidade` e preço
  igual ao confirmado). O banco trava a linha, então só uma das compras passa; a outra volta ao resumo sem pedido.
- **Envio repetido:** o resumo gera uma `chave` por exibição. O mesmo envio (duplo clique, voltar e reenviar) devolve o
  mesmo pedido, sem nova reserva nem cobrança: a linha do carrinho põe os envios em fila e `ux_pedido_chave` é a garantia final.
- **Dupla aprovação:** o índice `ux_pagamento_ativo` só permite uma tentativa pendente ou aprovada por pedido. Só tentativa
  pendente muda de estado, então evento repetido ou atrasado não faz nada.
- **Gateway fora da transação** (`PagamentoPedidoService`, sem `@Transactional`): grava a tentativa pendente (commit), chama
  o gateway, grava o retorno (outro commit). Se a resposta se perder, a tentativa fica pendente e a tela oferece repetir com
  a mesma chave, que o gateway reconhece e não cobra de novo. Pagamento de pedido cancelado que chegue depois é ignorado.
- **Resumo por e-mail** (`ResumoPedidoService`): sai pelo `EnvioDeMensagem` depois do commit, quando o pedido é aceito
  (aprovado, ou Pix/boleto gerado). Se falhar, o pedido continua valendo e a tela oferece **Reenviar resumo**. No perfil demo,
  a mensagem aparece em `/caixa-demo`.

**Para testar na demonstração:** os cartões fictícios `4111 1111 1111 1111` (aprova) e `4111 1111 1111 1210` (recusa), com
validade futura. Pix e boleto mostram os botões **Simular pagamento aprovado** e **Simular expiração**. Como o gateway demo
guarda as transações na memória, reiniciar a aplicação faz o Pix pendente responder como recusado (a pessoa tenta de novo).
Esses botões e o `POST /demo/pagamento/forcar-status` (só `ADMIN`) existem apenas no perfil `demo`; fora dele, respondem 404.

**Contratos para outras issues:**

- **F-16 (pedidos do cliente):** entregue em "Meus pedidos" (seção própria, abaixo). A confirmação continua em
  `/checkout/pedidos/{id}`, que também cuida do pagamento (Pix, boleto, nova tentativa); o detalhe em `/pedidos/{id}` leva até ela
  enquanto o pedido aguarda pagamento.
- **F-17 (administração):** cancelar é `EstadoPedidoService#cancelar(pedidoId, autor, observacao)`, que devolve o estoque uma
  vez e funciona até `EM_SEPARACAO`; registrar a necessidade de estorno fica com a F-17. `Pedido.versao` (`@Version`) já existe
  para a atualização concorrente. Pagamento aprovado só vem de `PagamentoPedidoService#processarEvento`.
- **F-15:** a `V4` criou o que o checkout precisa (`pedido.versao` e as colunas `retirada_*`) e a `V5` acrescentou o
  `status_anterior` ao histórico, que o `EstadoPedidoService` já preenche (aprovação, expiração e cancelamento).
  `PRONTO_PARA_RETIRADA` está nas constraints desde a `V1`.

**Testes:** `FreteDemoTest` e `CartaoFormTest` (sem banco); `CheckoutServiceIntegracaoTest` (acesso, total, frete, receita, preço
alterado, última unidade e envio repetido, com threads de verdade); `PagamentoPedidoIntegracaoTest` (recusa, evento repetido,
expiração, cancelamento, gateway fora do ar e falha no envio); `CheckoutFluxoWebIntegracaoTest` (o percurso pelas telas);
`CheckoutDemoSemPerfilTest` (sem o perfil demo, os botões não existem). A base `CheckoutTesteBase` não usa `@Transactional` de
propósito: um rollback no fim do teste esconderia os commits e as travas, que são o assunto.

**Fora do escopo:** escolher um cartão salvo no pagamento (opcional, F-25).

## Meus pedidos (F-16 / UC06)

O cliente acompanha os próprios pedidos em `/pedidos` (lista) e `/pedidos/{id}` (detalhe). O link **Meus pedidos** aparece no
cabeçalho só para o perfil `CLIENTE`. Código: `PedidosClienteController`, `PedidosClienteService`, `templates/pedidos/`.

- **Só leitura.** Não há POST, PUT nem DELETE em `/pedidos`: o cliente não altera status nem dados do pedido (responde 405).
  Quem muda o status é o administrador (F-17); a tela reflete o que o `EstadoPedidoService` gravou no histórico.
- **De quem é o pedido.** O serviço sempre recebe o id da **sessão** (nunca um id da URL ou do formulário) e busca o pedido por
  `id` **e** dono (`findByIdAndUsuarioId`). Pedido de outra pessoa responde igual a pedido inexistente: **404**, sem revelar que
  existe. Administrador, conta pendente e conta excluída recebem **403**. Visitante e sessão expirada vão para o login.
- **Lista:** do mais recente para o mais antigo (desempate pelo número), 10 por página, com número, data, total de unidades,
  total e situação. As unidades vêm de uma consulta agrupada, não item a item. Página além do fim cai na última; página
  negativa, na primeira. Sem pedidos: mensagem e link para o catálogo. Falha ao carregar: aviso com "Tentar novamente" e
  status 503, sem mostrar a mensagem técnica.
- **Detalhe:** itens com nome, apresentação, preço e quantidade **da hora da compra** (snapshots: mudar o preço do produto não
  altera pedido antigo), subtotal, frete, total, a **cópia** do endereço de entrega ou da loja de retirada (editar ou excluir o
  endereço salvo não muda pedidos já feitos), pagamento e histórico de status. Pedido que exige receita lembra de levar a
  receita à loja; pedido aguardando pagamento leva à tela `/checkout/pedidos/{id}`.
- **Pagamento mascarado:** método, situação, valor, parcelas e, no cartão, bandeira e final (`VISA final 1111`). O motivo da
  recusa aparece só quando recusado. Nunca aparecem número do cartão, CVV, código do Pix/boleto nem o id do gateway.
- **Linha do tempo:** do passo mais antigo ao mais recente, o último marcado como atual, incluindo `PRONTO_PARA_RETIRADA`.
  Mostra o status e o horário; **não** mostra o autor da mudança (é um administrador) nem a observação (pode ser anotação
  interna da F-17). Pedido sem histórico gravado mostra ao menos a situação atual.
- **Para a F-09/F-17:** `PedidosClienteService` é a consulta **do cliente sobre si mesmo**; ela recusa administrador de
  propósito. O histórico de um cliente na tela do administrador continua sendo uma consulta própria, atrás de `/admin/**`.
  Reaproveite os métodos `findByUsuarioIdOrderByCriadoEmDescIdDesc` e `findByPedidoIdOrderByCriadoEmAscIdAsc`, que desempatam
  por id.
- **Fica para a F-23:** filtros por período e por status (UC06-A2). "Comprar novamente" (UC06-A3) é opcional (F-25).

## Carrinho (F-10 / UC05)

Entidades `Carrinho` e `ItemCarrinho` (as tabelas e unicidades já vinham da `V1`: um item por apresentação em cada
carrinho; o dono é o usuário **ou** o token, nunca os dois). Regras em `service/CarrinhoService`, telas em
`CarrinhoController` + `templates/carrinho*.html`.

| Rota | O que faz |
|---|---|
| `GET /carrinho` | lista o carrinho, com total recalculado no servidor (`BigDecimal`) |
| `POST /carrinho/itens` (`apresentacaoId`, `quantidade`) | adiciona; se já existe, **soma** |
| `POST /carrinho/itens/{id}/quantidade` | altera; quantidade menor que 1 leva à confirmação de remoção |
| `GET/POST /carrinho/itens/{id}/remover` | confirmação e remoção |
| `POST /carrinho/itens/{id}/aceitar-preco` | aceita o preço novo (UC05-E3) |
| `POST /carrinho/esvaziar` | esvazia |
| `GET /carrinho/finalizar` | revalida e segue para `/checkout`; visitante vai ao login e **volta aqui** depois |

- **De quem é o carrinho:** quem decide é o servidor. Usuário logado: o id do login. Visitante: o cookie `carrinho_token`
  (UUID, `HttpOnly`, `SameSite=Lax`, 30 dias; `security/CarrinhoCookie`). Nenhuma rota recebe "id de carrinho"; o id de item
  é procurado só dentro do carrinho do dono, então id alheio (inclusive por POST direto) dá "não está no seu carrinho".
- **Revalidação a cada operação e a cada exibição:** produto ativo, apresentação ativa, estoque e limite por pedido.
  Passou do limite ou do estoque ao adicionar: o servidor ajusta e avisa. Item que ficou indisponível depois fica
  destacado, fora do total, e **bloqueia** o "Finalizar" até ser corrigido ou removido.
- **Login:** `security/MesclagemCarrinhoNoLogin` ouve o evento de login bem-sucedido (publicado antes do
  `LoginSuccessHandler`, que **não foi alterado**, para não conflitar com a F-18). Une por apresentação, soma
  respeitando limite e estoque, guarda os avisos na sessão (aparecem em `/carrinho`), apaga o carrinho do visitante e o
  cookie. Falha na mesclagem não impede o login. Sair não apaga o carrinho da conta.
- **Para a página do produto (UC02) e o checkout (F-14):** adicionar é `POST /carrinho/itens`; o destino do
  "Finalizar" é a constante `CarrinhoController.DESTINO_CHECKOUT` (`/checkout`, F-14). Administrador e conta pendente
  chegam lá e recebem 403. A home já tem o botão "Adicionar ao carrinho" na apresentação padrão disponível de cada card.
- **Adicionar sem sair da página:** `static/js/carrinho-rapido.js` intercepta o envio de `form.card-carrinho` e faz o mesmo
  `POST /carrinho/itens` pedindo JSON (`Accept: application/json`). A resposta traz a mensagem, os avisos, o item
  (nome, apresentação, imagem, quantidade, preço, subtotal), `totalUnidades` e `total`; recusa de regra volta como 422 com
  `{"erro": ...}`. Um painel pequeno mostra o item e tem "Continuar comprando" (fecha e devolve o foco ao botão) e "Ver
  carrinho"; Esc também fecha, e ele não some sozinho. Sem JavaScript, ou se a chamada falhar, o formulário segue o caminho
  normal e redireciona para `/carrinho` (a página do produto, F-02, continua assim).
- **Contador e aviso no cabeçalho:** `CarrinhoContagemAdvice` entrega ao `layout.html` o número de unidades (`Carrinho (3)`,
  com " itens no carrinho" em `.visually-hidden`; sem número quando vazio; conta todas as unidades, inclusive de item
  indisponível, ao contrário do total de `/carrinho`). No login, `MesclagemCarrinhoNoLogin` guarda na sessão "Você tem N
  itens no seu carrinho." (também quando não havia cookie, se a conta já tinha itens) e o layout mostra o aviso **uma vez**,
  na primeira página realmente exibida (redirect não consome). Quem veio de "Finalizar" ainda cai onde o login mandar: voltar
  a `/checkout` depois do cadastro é da F-18.
- **Layout do card:** `.card` virou coluna flex e o botão fica preso ao fundo (`margin-top: auto`), em vez de vazar por cima
  do card da linha de baixo.

## Detalhes do produto (F-02 / UC02)

- `GET /produtos/{id}` mostra dados do produto, indicações em tópicos, bula quando há um endereço HTTP(S) válido
  (link cadastrado inválido é sinalizado e não aberto),
  avisos, informações de pagamento e imagens. O contador de visualizações sobe uma vez por abertura válida da página.
- A apresentação inicial é a primeira ativa por ordem; a seleção mostra preço, estoque e imagens gerais ou específicas
  daquela apresentação. Apresentações inativas ou pertencentes a outro produto não são aceitas na URL.
- O botão envia `apresentacaoId` e quantidade por `POST /carrinho/itens` (contrato da F-10). Estoque e limite são
  revalidados no servidor pelo `CarrinhoService`; sem estoque, a compra fica desabilitada. Produto inexistente/inativo
  retorna 404 com orientação para o catálogo.

## Cadastro de clientes (F-04 / UC03)

- **Telas:** `/cadastro` (formulário e POST) e `/confirmar-conta` (página provisória até a F-05). Quem já está logado é
  mandado para a home.
- **Regras** (`CadastroForm` e `CadastroService`): CPF pelos dígitos verificadores (`@CpfValido`), 18 anos ou mais,
  celular com DDD e 9 dígitos, e-mail válido, senha de 8 caracteres ou mais com letra e número (até 72 bytes, o limite do
  BCrypt) e CPF e e-mail ainda sem conta, sem diferenciar maiúsculas. Se dois cadastros iguais chegarem ao mesmo tempo,
  o índice único do banco recusa o segundo e a tela mostra o mesmo aviso de duplicidade.
- **A conta nasce PENDENTE e CLIENTE**, com senha em BCrypt e a versão dos termos (`app.termos.versao`, hoje `1.0`) e a
  data do aceite. O navegador não manda nada disso: o `CadastroForm` não tem esses campos, então `perfil=ADMIN` ou
  `status=ATIVA` na requisição são ignorados (há teste).
- **Duplicidade:** a tela avisa que já existe conta e oferece entrar (`/login`) ou recuperar a senha (`/recuperar-senha`,
  da F-06).
- **Máscaras:** o CPF e o celular ganham pontuação enquanto a pessoa digita (`static/js/mascaras.js`, pelo atributo
  `data-mascara="cpf"` ou `"celular"`). Sem JavaScript o formulário continua funcionando: o servidor grava só os dígitos.
- **Retorno ao carrinho:** `/cadastro?retorno=carrinho` ou `?retorno=checkout` (só essas duas palavras; qualquer outra coisa é
  ignorada). O valor fica na sessão (`retornoCadastro`); `CadastroController.destinoDoRetorno` converte para `/carrinho`
  ou `/checkout`. Quem montar o botão "Criar conta" do carrinho (F-10) usa esse parâmetro.
- **Contrato com a confirmação (F-05):** depois de gravar, o cadastro guarda na **sessão** o id da conta
  (`contaEmConfirmacao`) e redireciona para `/confirmar-conta`. A F-05 lê o id daí (nunca da URL) e, ao final, usa o
  `retornoCadastro`.
- **Testes:** `CpfValidatorTest`, `CadastroFormTest`, `CadastroControllerTest` (sem banco), `CadastroServiceIntegracaoTest`
  e `CadastroIntegracaoTest` (PostgreSQL real; este último inclui a tentativa de elevar o perfil e o login da conta pendente).

## Confirmação da conta e códigos de verificação (F-05 / UC04)

- **Fluxo:** depois do cadastro, `/confirmar-conta` mostra o e-mail e o celular mascarados (`m***@dominio.com`,
  `(67) 9****-8888`). A pessoa escolhe o canal, recebe um código de 6 números e o digita. Com o código certo, aquele
  contato fica confirmado (`usuario.email_confirmado_em` ou `celular_confirmado_em`, migration `V3`), a conta vira `ATIVA`
  e a pessoa volta ao login com o aviso "Conta confirmada". Confirmar o e-mail não confirma o celular, e vice-versa.
- **Retomada:** o login de conta pendente mostra o link "Confirmar minha conta" (`/confirmar-conta/retomar`). Para
  retomar, a pessoa informa e-mail ou CPF **e a senha**; quem sabe só o e-mail de alguém não abre nada. A conta em
  confirmação fica sempre na sessão (`contaEmConfirmacao`), nunca na URL.
- **Códigos (`CodigoVerificacaoService`):** gerados com `SecureRandom`, guardados só como hash BCrypt, de uso único e
  separados por finalidade (`CADASTRO`, `ALTERAR_EMAIL`, `ALTERAR_CELULAR`, `RECUPERAR_SENHA`): código de uma finalidade
  não vale para outra. A F-06 (recuperação) e a F-21 (troca de contato) usam o mesmo serviço. Recusas voltam como
  resultado, sem exceção, para a tentativa errada ficar gravada.
- **Limites** (em `app.codigos.*` no `application.properties`):

  | Configuração | Valor | O que faz |
  |---|---|---|
  | `app.codigos.validade` | `10m` | tempo de vida do código |
  | `app.codigos.tentativas-maximas` | `5` | erros até o código ser bloqueado |
  | `app.codigos.intervalo-reenvio` | `60s` | espera mínima para pedir outro código (o novo invalida o anterior) |
  | `app.codigos.envios-por-hora` | `5` | envios na última hora antes de começar o bloqueio |
  | `app.codigos.bloqueio-envio` | `15m` | depois do limite, cada novo envio só sai esse tempo depois do anterior |

- **Envio de demonstração e perfil demo:** não há serviço real de e-mail ou SMS. O `DemoEnvioAdapter` guarda as
  mensagens na memória (as últimas 50, somem ao reiniciar) e a página `/caixa-demo` as mostra. Essa página só existe com
  o perfil `demo`, que vem ligado no `application.properties` (`spring.profiles.active=demo`). Para desligar, apague a
  linha ou suba com outro perfil (`$env:SPRING_PROFILES_ACTIVE="producao"`): aí `/caixa-demo` dá 404. O código nunca vai
  para log nem para resposta fora dessa página.
- **Contrato de envio para outras issues:** `mensagem/EnvioDeMensagem.enviar(canal, destino, assunto, texto)`. O resumo
  do pedido (F-14) usa a mesma interface com o canal `EMAIL`.
- **Testes:** `ContatoMascaradoTest` e `DemoEnvioAdapterTest` (unitários), `CaixaDemoControllerTest` e
  `CaixaDemoSemPerfilTest` (camada web, com e sem o perfil demo), `CodigoVerificacaoServiceIntegracaoTest` (expirado,
  incorreto, uso repetido, finalidade errada, bloqueio por tentativas, reenvio, limite por hora, código fora do log e
  tentativa gravada mesmo com a recusa) e `ConfirmacaoContaIntegracaoTest` (fluxo completo, SMS, retomada e caixa demo).

## Recuperação de senha e revogação de sessões (F-06 / UC14)

- **Fluxo, sem login:** `/recuperar-senha` (e-mail ou CPF, mais o canal preferido) → `/recuperar-senha/codigo` (o código
  de 6 números) → `/recuperar-senha/nova-senha`. A tela de login tem o atalho "Esqueci minha senha". A conta em
  recuperação fica na sessão, nunca na URL, e a nova senha só é aceita depois de um código válido.
- **Resposta sempre igual:** para conta elegível, inexistente, pendente, excluída ou sem contato confirmado, o pedido mostra
  a mesma mensagem ("Se houver uma conta com esses dados..."), e o código errado também. O código só vai para um contato
  **já confirmado** (`email_confirmado_em` ou `celular_confirmado_em`), do canal preferido ou, se ele não estiver
  confirmado, do outro.
- **Limites:** o código usa a finalidade `RECUPERAR_SENHA` e as regras da F-05 (`app.codigos.*`: 10 min, 5 tentativas,
  uso único, 60 s entre pedidos, 5 por hora). Além disso, o `LimiteDeSolicitacoes` aplica os mesmos 60 s e 5 por hora
  **por identificador digitado**, exista a conta ou não, para o "aguarde" também não denunciar contas. Esse limite fica
  na memória e zera quando a aplicação reinicia.
- **Nova senha:** mesma regra do cadastro (`validacao/PoliticaDeSenha`: 8 ou mais, letra e número, até 72 bytes), gravada
  em BCrypt, e precisa ser diferente da atual. Cliente e administrador recuperam do mesmo jeito; o vínculo com Google ou Facebook não é apagado, e conta
  sem senha local passa a ter uma.
- **Sessões antigas:** ao trocar a senha, `RevogacaoDeSessoes` encerra todas as sessões da conta, em qualquer navegador.
  A `SecurityConfig` registra as sessões (`SessionRegistry`, sem limite de sessões por pessoa); a sessão encerrada é
  barrada na próxima requisição e vai para `/login?expirada`. A troca de senha logado (F-12) e o login (F-18) podem
  reaproveitar o mesmo `RevogacaoDeSessoes`.
- **Testes:** `LimiteDeSolicitacoesTest` e `RevogacaoDeSessoesTest` (unitários), `RecuperacaoSenhaServiceIntegracaoTest`
  (elegibilidade, resposta igual, limite por identificador, canal confirmado, troca, reutilização, administrador, conta
  social, sessões) e `RecuperacaoSenhaIntegracaoTest` (fluxo completo, código reutilizado, mensagens iguais, aguardar,
  sessão aberta antes da troca perde o acesso, senha fraca, administrador e o atalho no login).

## Minha conta: dados pessoais e senha (F-12 / parte do UC07)

Área do cliente logado para ver e editar os próprios dados e trocar a senha. Regras em `service/ContaService`, telas em
`ContaController` + `templates/conta/*.html`. As rotas não estão em `ROTAS_PUBLICAS`: exigem login (regra "todo o resto").

| Rota | O que faz |
|---|---|
| `GET /conta` | dados pessoais (CPF, nascimento, gênero) e contatos (e-mail e celular, com "confirmado" ou "não confirmado") |
| `GET/POST /conta/editar` | altera **nome, data de nascimento e gênero**; "Cancelar" é um link para `/conta` e não salva nada |
| `GET/POST /conta/senha` | troca a senha: exige a senha atual, a nova e a confirmação |

- **Mesmas regras do cadastro (F-04/F-06):** nome obrigatório (até 150), nascimento válido (de 1900 até hoje, 18 anos ou
  mais) e gênero obrigatório. A nova senha segue `validacao/PoliticaDeSenha` (8 caracteres ou mais, com letra e número, até
  72 bytes), é gravada em BCrypt com o mesmo `PasswordEncoder` e precisa ser diferente da atual.
- **O que o formulário não consegue alterar:** `ContaForm` só tem `nome`, `dataNascimento` e `genero`, e o controller
  libera só esses campos no binder. CPF, e-mail, perfil, status e id enviados à força no POST são ignorados; o dono da
  conta é sempre o usuário da sessão (nenhuma rota recebe id). Só conta `ATIVA` usa estas telas (cliente ou administrador).
- **Cabeçalho:** depois de salvar os dados, `security/SessaoAutenticada` recria o principal da sessão com o nome novo, então
  o primeiro nome do cabeçalho muda sem novo login. O link "Minha conta" fica no cabeçalho, ao lado de "Meus endereços".
- **Sessões na troca de senha:** `RevogacaoDeSessoes.encerrarOutrasSessoesDe(usuarioId, idDaSessaoAtual)` encerra as
  **outras** sessões da conta (outro navegador ou aparelho; vão para `/login?expirada`). A sessão em que a senha foi trocada
  continua logada, com o id da sessão renovado (`changeSessionId`).
- **Contatos:** e-mail e celular são só exibidos. A troca com confirmação por código é a F-21 e **continua sendo lacuna**:
  esta entrega não completa o UC07.
- **Testes:** `ContaFormsTest` e `DadosDaContaTest` (unitários), `ContaControllerTest` (camada web),
  `ContaServiceIntegracaoTest`, `ContaIntegracaoTest` e `ContaSessoesServidorRealTest` (Tomcat de verdade, dois
  "navegadores" com cookies separados, para provar a revogação das outras sessões) (PostgreSQL real; incluem campos forjados ignorados, senha atual
  errada, nova igual à atual, senha acima de 72 bytes, cancelar sem gravar, nome novo no cabeçalho e sessões revogadas).

## Consulta de clientes (F-09 / UC11)

Consulta administrativa **somente leitura** de clientes, com auditoria. Regras em `service/ClienteAdminService`, telas em
`AdminClienteController` + `templates/admin/clientes.html` e `cliente-detalhe.html`. Fica sob `/admin/**` (só `ADMIN`:
visitante vai para `/login`, cliente logado recebe 403, inclusive por URL direta). Não há rota de escrita: o controller só
tem `GET`, e não existe edição administrativa de conta.

| Rota | O que faz |
|---|---|
| `GET /admin/clientes` | lista paginada (20 por página, por nome) com busca `q` e filtro `status` (todos, pendente, ativa, excluída) |
| `GET /admin/clientes/{id}` | detalhe do cliente e seus 10 pedidos mais recentes |

- **Busca:** `q` procura em nome e e-mail (sem diferenciar maiúsculas) e em CPF (aceita pontuação; só vale com 3 dígitos ou
  mais). `%`, `_` e `!` digitados são texto comum, não curinga. Status desconhecido vira "todos". Só contas `CLIENTE`
  aparecem; um id de administrador no detalhe responde 404, como id inexistente.
- **O que aparece:** a lista mostra o CPF **mascarado** (`***.982.247-**`, `ContatoMascarado.cpf`). O detalhe mostra o CPF
  completo (a consulta é auditada), nascimento, gênero, e-mail e celular com "confirmado" ou "não confirmado", status e data
  de cadastro. Nunca aparecem senha, hash, tentativas de login, termos, códigos de verificação nem dados de pagamento.
- **Histórico:** lista mínima (número, data, total e status) dos 10 pedidos mais recentes, lida do `PedidoRepository`.
  A F-16 entregou só a consulta **do próprio cliente** (`/pedidos`), que recusa administrador; esta lista continua usando o
  `PedidoRepository` por ser uma consulta de administrador, auditada.
- **Auditoria (`log_auditoria`):** cada consulta grava o log **na mesma transação** da leitura; se o log falhar, a consulta
  falha. Sem administrador identificado na sessão também não há consulta.

  | Ação | Quando | `alvo_tipo` / `alvo_id` | `detalhes` |
  |---|---|---|---|
  | `CLIENTE_LISTAR` | abrir a lista (com ou sem filtro) | vazios | `status=<ATIVA\|...\|TODOS>, busca=sim\|nao, pagina=<n>` |
  | `CLIENTE_VISUALIZAR` | abrir o detalhe de um cliente | `USUARIO` / id do cliente | vazio |

  Todos gravam autor, IP (`getRemoteAddr()`; `X-Forwarded-For` não é confiável) e data. O texto buscado **nunca** vai para o
  log (pode ser CPF ou e-mail). Detalhe de cliente inexistente ou de administrador responde 404 e não grava log.
- **Fora do escopo:** exportação CSV e filtro por período (F-23, que pode reaproveitar `ClienteAdminService` e gravar
  `CLIENTE_EXPORTAR`).
- **Sem migration:** a tabela `log_auditoria` já existe desde a V1.
- **Testes:** `ContatoMascaradoTest` (unitário), `AdminClienteSegurancaTest` (visitante, cliente, sem rota de escrita),
  `AdminClienteTelaTest` (HTML: um único h1, CPF mascarado, tabela em região rolável, detalhe sem formulário) e
  `ClienteAdminServiceIntegracaoTest` (PostgreSQL real: busca por nome, e-mail e CPF, status, só cliente, paginação,
  curingas como texto, auditoria sem o termo buscado, 404 sem log, pedidos recentes, log que falha derruba a consulta).

## Produtos, apresentações, estoque e imagens (F-08 / UC12)

Cadastro administrativo de produtos. Regras em `service/ProdutoService`, telas em `AdminProdutoController` +
`templates/admin/produtos.html` e `produto-form.html`; fotos em `service/ArmazenamentoImagem` e `config/UploadsConfig`.
Fica sob `/admin/**` (só `ADMIN`: visitante vai para `/login`, cliente logado recebe 403, inclusive em POST direto).

| Rota | O que faz |
|---|---|
| `GET /admin/produtos` | lista paginada (20 por página, por nome) com busca `q` (nome, marca, princípio ativo, SKU ou EAN) e filtro `status` (ativos, inativos) |
| `GET /admin/produtos/novo`, `POST /admin/produtos` | formulário e cadastro |
| `GET /admin/produtos/{id}/editar`, `POST /admin/produtos/{id}` | edição |
| `POST /admin/produtos/{id}/excluir` | exclusão (ver abaixo) |

- **Regras do formulário:** nome e marca obrigatórios; ao menos uma categoria **ativa**; tarja vermelha ou preta exige
  receita; ao menos uma apresentação, cada uma com descrição (até 60), SKU (até 40) e EAN (8 a 14 dígitos) **únicos**, preço
  maior que zero, estoque não negativo e limite por pedido maior que zero. Toda violação volta ao formulário com a mensagem
  no alerta de erro (regras de negócio) ou junto ao campo, com `aria-invalid` (validação do formulário), sem gravar nada.
- **Preço e estoque ficam em `Apresentacao`.** Apresentação não é removida: desmarca-se "Ativa". Linhas em branco no fim
  do formulário cadastram novas apresentações.
- **Estoque e compra concorrente:** cada linha carrega a `versao` lida ao abrir o formulário. Se uma compra (ou
  cancelamento) mexeu no estoque depois, o salvamento é recusado com "foi alterada por outra operação"; o estoque da compra
  não é sobrescrito. A conferência final vale também contra a corrida entre validar e gravar (lock otimista).
- **Quem e quando:** `atualizado_por` (o administrador da sessão, nunca um campo do formulário) e `atualizado_em`.
- **Exclusão:** produto com pedido (`item_pedido`) só é **inativado**, e o histórico fica intacto; sem pedido, é apagado de
  vez (imagens e apresentações primeiro, porque o mapeamento não tem cascade). Se surgir um vínculo no meio do caminho, a
  tela pede para inativar. Produto inativado sai do catálogo (F-01/F-02).
- **Fotos:** JPG, PNG ou WebP de até 2 MB, conferidas pela extensão, pelo tipo enviado **e** pelo conteúdo do arquivo. O nome
  gravado é um UUID (o nome enviado nunca vira caminho). Ficam em `app.uploads.dir` (padrão `uploads/`, fora do JAR) e são
  servidas em `/uploads/produtos/**` (rota pública, só leitura). A foto nova vale para todas as apresentações ou para uma
  delas, e a apresentação escolhida precisa ser do mesmo produto. Sem foto, o site usa a imagem padrão. Se a gravação
  falha, os arquivos já escritos são apagados; arquivos de fotos removidas são apagados só depois do commit.
- **Limites de envio:** `spring.servlet.multipart.max-file-size=10MB` e `max-request-size=30MB` (o limite de 2 MB por
  foto é do serviço); passar do limite do servidor mostra a mensagem de `UploadExcedidoAdvice`.
- **Bula:** a URL é opcional, mas, se informada, precisa ser um endereço `http://` ou `https://` válido (mesma regra da página do produto, F-02, que continua tratando URL inválida já gravada).
- **Testes:** `ProdutoServiceIntegracaoTest` (PostgreSQL real: cadastro aparece no catálogo e grava o administrador,
  violações com mensagem, estoque alterado por compra não é sobrescrito, foto vinculada à apresentação certa, exclusão
  definitiva e só inativação com pedido), `ArmazenamentoImagemTest`, `UploadExcedidoAdviceTest`,
  `AdminProdutoSegurancaTest` (visitante, cliente, administrador da sessão gravado, CSRF) e `AdminProdutoTelaTest` (HTML:
  um único h1, rótulos, erro ligado ao campo por `aria-describedby`, tabela em região rolável).

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

### Fase 1: fundação

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

### Onda final: entrega e estabilização

Os códigos `F-NN` são os do plano da onda final, não os números do GitHub: ao criar a issue, anote o número ao lado
(por exemplo `F-04 (#52)`). As letras A a F são as pessoas do plano; troque cada uma pelo nome e pelo usuário do GitHub.

| Módulo | Issue | Integrante | PR |
|---|---|---|---|
| Catálogo público: busca, categoria, filtros e paginação (UC01) | F-01 | Cássio (@cassioqm) | |
| Detalhes do produto e apresentação comprável (UC02) | F-02 | Cássio (@cassioqm) | |
| Navegação integrada, páginas de termos e revisão de interface (UC03/UC15) | F-03 | Pessoa A: _preencher_ | |
| Cadastro com validação e conta pendente (UC03) | F-04 | Kauê (@KaueAlves09) | |
| Serviço de códigos e confirmação de conta com envio simulado (UC04) | F-05 | Kauê (@KaueAlves09) | |
| Recuperação de senha sem login e revogação de sessões (UC14, UC10-A4) | F-06 | Kauê (@KaueAlves09) | |
| CRUD de categorias hierárquicas (UC13) | F-07 | Pessoa C: _preencher_ | |
| CRUD de produtos, apresentações, estoque e imagens (UC12) | F-08 (#31) | Nakao (@FernandoNakao) | |
| Consulta administrativa de clientes com auditoria (UC11) | F-09 (#32) | Kauê (@KaueAlves09), repassada da trilha C | |
| Carrinho de visitante e cliente, com mesclagem no login (UC05) | F-10 | Pessoa D: _preencher_ | |
| CRUD de endereços e seleção no checkout (UC16) | F-11 | Pessoa D: _preencher_ | |
| Edição de dados pessoais e senha autenticada (UC07) | F-12 (#35) | Aieser (pessoa D), integração por Kauê (@KaueAlves09) | |
| Gateway de pagamento simulado e retorno idempotente (UC08/UC09) | F-13 | Pessoa E: _preencher_ | |
| Checkout transacional, entrega ou retirada, estoque e expiração (UC08/UC09) | F-14 (#37) | @Joaomarceloap | |
| Contratos e modelos de pedidos e migrations de compatibilidade (UC06/UC08/UC14/UC17) | F-15 | Pessoa F: _preencher_, com Kauê (contatos confirmados) e a pessoa E | |
| Histórico e detalhe de pedidos do cliente (UC06) | F-16 | Pessoa F: _preencher_ | |
| Administração de pedidos: transições, receita e histórico (UC17) | F-17 | Pessoa F: _preencher_ | |
| Proteção do login e logout validado nos novos fluxos (UC10/UC15) | F-18 | Pessoa F: _preencher_ | |
| Integração, teste do percurso completo e entrega reproduzível | F-19 | Todos | |
