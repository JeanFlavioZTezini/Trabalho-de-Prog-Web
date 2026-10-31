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
│   ├── enderecos      (lista, formulário e exclusão dos endereços do cliente)
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
- `ImagemProduto.apresentacao` nula significa que a imagem vale para todas as apresentações do produto.
- `Apresentacao.versao` (`@Version`) é o lock otimista do estoque. No checkout, duas compras simultâneas da
  mesma apresentação fazem a segunda falhar com `ObjectOptimisticLockingFailureException`: trate esse erro.
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

Entidades em `model` (`Pedido`, `ItemPedido`, `Pagamento`, `HistoricoStatusPedido`, `LogAuditoria`); o schema vem da `V1__init.sql`.

- **O pedido guarda cópias (snapshots).** O endereço de entrega vai no `@Embeddable EnderecoEntrega`, que o `Pedido`
  mapeia para as colunas `entrega_*`, e o `ItemPedido` guarda nome, descrição, SKU, preço e `exigeReceita` do momento
  da compra. Mudar um `Endereco` salvo ou o catálogo não altera pedidos antigos.
- **Entrega x retirada:** entrega tem `enderecoEntrega` e não tem `lojaRetirada`; retirada tem `lojaRetirada`, frete zero
  e `enderecoEntrega` nulo. O banco recusa as outras combinações.
- **Chame `Pedido#recalcularTotais()` antes de gravar.** O banco exige `total = subtotal + frete`. O método arredonda
  cada item e o frete para 2 casas (HALF_UP) e depois soma, e trata frete nulo como zero.
- **Regra que o banco não garante:** pedido com item que exige receita só pode ser RETIRADA. `Pedido#temItemQueExigeReceita()`
  só consulta; quem valida é o service do checkout (Fase 2), com teste próprio.
- `Pedido.itens` tem `cascade = ALL`: grave o pedido e os itens vão juntos. Pagamentos e histórico são gravados pelos
  próprios repositories.
- Um pedido tem várias tentativas de `Pagamento` (UC08-E2). Do cartão só ficam dados mascarados, e parcelas acima de 1
  só no crédito.
- `HistoricoStatusPedido.alteradoPor` nulo significa que a mudança foi do sistema.
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
  `cep/demo/DemoCepAdapter`, com 5 CEPs fictícios (`01310-100`, `04094-050`, `01001-000`, `20040-020`, `30130-010`).
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
| Catálogo público: busca, categoria, filtros e paginação (UC01) | F-01 | Pessoa A: _preencher_ | |
| Detalhes do produto e apresentação comprável (UC02) | F-02 | Pessoa A: _preencher_ | |
| Navegação integrada, páginas de termos e revisão de interface (UC03/UC15) | F-03 | Pessoa A: _preencher_ | |
| Cadastro com validação e conta pendente (UC03) | F-04 | Kauê (@KaueAlves09), pessoa B | |
| Serviço de códigos e confirmação de conta com envio simulado (UC04) | F-05 | Kauê (@KaueAlves09), pessoa B | |
| Recuperação de senha sem login e revogação de sessões (UC14, UC10-A4) | F-06 | Kauê (@KaueAlves09), pessoa B | |
| CRUD de categorias hierárquicas (UC13) | F-07 | Pessoa C: _preencher_ | |
| CRUD de produtos, apresentações, estoque e imagens (UC12) | F-08 | Pessoa C: _preencher_ | |
| Consulta administrativa de clientes com auditoria (UC11) | F-09 | Pessoa C: _preencher_ | |
| Carrinho de visitante e cliente, com mesclagem no login (UC05) | F-10 | Pessoa D: _preencher_ | |
| CRUD de endereços e seleção no checkout (UC16) | F-11 | Pessoa D: _preencher_ | |
| Edição de dados pessoais e senha autenticada (UC07) | F-12 | Pessoa D: _preencher_ | |
| Gateway de pagamento simulado e retorno idempotente (UC08/UC09) | F-13 | Pessoa E: _preencher_ | |
| Checkout transacional, entrega ou retirada, estoque e expiração (UC08/UC09) | F-14 | Pessoa E: _preencher_ | |
| Contratos e modelos de pedidos e migrations de compatibilidade (UC06/UC08/UC14/UC17) | F-15 | Pessoa F: _preencher_, com Kauê (contatos confirmados) e a pessoa E | |
| Histórico e detalhe de pedidos do cliente (UC06) | F-16 | Pessoa F: _preencher_ | |
| Administração de pedidos: transições, receita e histórico (UC17) | F-17 | Pessoa F: _preencher_ | |
| Proteção do login e logout validado nos novos fluxos (UC10/UC15) | F-18 | Pessoa F: _preencher_ | |
| Integração, teste do percurso completo e entrega reproduzível | F-19 | Todos | |
