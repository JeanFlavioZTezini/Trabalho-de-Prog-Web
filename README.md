# Farmácia On-line (Farma++)

[![CI](https://github.com/JeanFlavioZTezini/Trabalho-de-Prog-Web/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/JeanFlavioZTezini/Trabalho-de-Prog-Web/actions/workflows/ci.yml)

Loja virtual de farmácia feita como Trabalho Prático de Programação Web (ProgWeb 2026/2), com Spring Boot e Thymeleaf.
É um ambiente de demonstração acadêmica: a farmácia é fictícia e nenhuma compra, pagamento ou entrega é real.

- **Detalhes de cada funcionalidade** (regras, limites, rotas): [`docs/funcionalidades.md`](docs/funcionalidades.md)
- **Para quem vai mexer no código** (migrations, testes, convenções, página nova): [`docs/desenvolvimento.md`](docs/desenvolvimento.md)
- **Acessibilidade** (contraste, checklist de PR): [`docs/acessibilidade.md`](docs/acessibilidade.md)

## Funcionalidades

| Área | O que tem | Issues |
|---|---|---|
| Catálogo | Home com mais vendidos e mais vistos, menu de categorias, busca sem acento e com "Você quis dizer", filtros, ordenação e detalhe do produto com apresentações | F-01, F-02, F-22 |
| Conta | Cadastro com conta pendente, confirmação por código (e-mail ou SMS simulados), login por e-mail ou CPF com bloqueio após 5 erros, recuperação de senha, dados pessoais, troca de senha, e-mail e celular, login social simulado e exclusão da conta | F-04, F-05, F-06, F-12, F-18, F-20, F-21, F-24 |
| Compra | Carrinho de visitante juntado ao da conta no login, endereços com CEP, checkout com entrega ou retirada, cartão, Pix e boleto simulados, estoque reservado, cartão salvo e "comprar novamente" | F-10, F-11, F-13, F-14, F-25 |
| Pedidos | Histórico e detalhe para o cliente, filtros por situação e período | F-15, F-16, F-23 |
| Administração | Categorias, produtos (apresentações, estoque, fotos), clientes com auditoria e CSV, pedidos com mudança de status e conferência de receita | F-07, F-08, F-09, F-17, F-23 |

## Stack

Java 17, Spring Boot 3.5.16 (Spring MVC + Thymeleaf, renderização no servidor: opção A do enunciado), Spring Security,
Spring Data JPA, PostgreSQL 16 (Docker), Flyway, Lombok e Maven. Testes com JUnit 5, MockMvc e Testcontainers. CSS e
JavaScript sem framework.

## Como rodar

**Pré-requisitos:** [JDK 17](https://adoptium.net/) e [Docker](https://www.docker.com/) ligado. O Maven não precisa
ser instalado: o projeto traz o `mvnw`.

```bash
git clone https://github.com/JeanFlavioZTezini/Trabalho-de-Prog-Web.git
cd Trabalho-de-Prog-Web
docker compose up -d              # PostgreSQL em localhost:5433, com volume persistente
.\mvnw.cmd spring-boot:run        # Windows; no Linux/Mac: ./mvnw spring-boot:run
```

Abra [http://localhost:8080](http://localhost:8080) quando o log mostrar `Started FarmaciaOnlineApplication` (a primeira
vez baixa as dependências e demora alguns minutos). Na primeira subida, o Flyway cria as tabelas e os dados de
demonstração.

- **Parar:** `Ctrl+C` na aplicação e `docker compose down` no banco (os dados continuam no volume).
- **Zerar o banco** (volta ao seed; apaga cadastros, carrinhos e pedidos feitos depois):
  `docker compose down -v`, `docker compose up -d` e suba a aplicação de novo.
- **Atualizar um banco que já existe:** basta subir a aplicação; o Flyway aplica só as migrações novas e mantém os dados.

## Dados de demonstração

Todas as contas usam a senha `Farma@123`:

| E-mail | Perfil | Situação |
|---|---|---|
| `admin@farma.com` | ADMIN | ativa (vai para o painel `/admin`) |
| `cliente@farma.com` | CLIENTE | ativa |
| `pendente@farma.com` | CLIENTE | pendente (para testar a confirmação da conta) |

O perfil `demo` vem ligado no `application.properties`. Com ele, os serviços externos são simulados, exceto o CEP:

| Serviço | Na demonstração |
|---|---|
| E-mail e SMS (códigos, resumo do pedido) | As mensagens ficam em [`/caixa-demo`](http://localhost:8080/caixa-demo) (as últimas 50, na memória). Cada conta vê as próprias; os códigos de recuperação de senha e de vínculo social só aparecem para o admin |
| Pagamento | Cartão `4111 1111 1111 1111` aprova e `4111 1111 1111 1210` recusa (validade futura, qualquer código de segurança). Pix e boleto ficam pendentes até os botões "Simular pagamento aprovado" ou "Simular expiração" do pedido |
| CEP | API pública do ViaCEP (precisa de internet; se falhar, a tela pede o endereço à mão). Sem internet: `app.cep.provedor=demo` usa CEPs fixos, como `79002-000`; `99999-999` simula falha |
| Frete | Só Mato Grosso do Sul: Campo Grande (79000-000 a 79129-999) R$ 9,90 em 2 dias; demais cidades R$ 14,90 em 3 dias. Retirada é grátis, nas duas lojas de Campo Grande |
| Login com Google ou Facebook | Tela "Provedor simulado", sem OAuth real |

Sem o perfil `demo`, a `/caixa-demo`, os botões de simulação e o login social respondem 404. Remédio que exige receita
obriga a retirada em loja, e o produto "Termômetro Digital" está sem estoque de propósito, para mostrar o selo
"Indisponível".

**Roteiro curto da demonstração:**
1. Visitante busca `dipirna` (com erro de digitação), abre o produto e adiciona ao carrinho.
2. "Criar conta" com um CPF fora do seed, código em `/caixa-demo`, confirma e entra: o carrinho veio junto.
3. Checkout com entrega (CEP `79002-336`) e cartão `4111 1111 1111 1111`: pedido aprovado e resumo em `/caixa-demo`.
4. Como `admin@farma.com`, abre o pedido e o passa para "Em separação" e "Enviado"; o cliente vê o mesmo em "Meus pedidos".
5. No admin, edita o estoque de um produto e consulta um cliente (CPF mascarado na lista, consulta auditada).
6. O cliente tenta abrir `/admin` (403), sai da conta, e "Meus pedidos" pede login de novo.

## Testes

```bash
.\mvnw.cmd verify      # Windows; no Linux/Mac: ./mvnw verify
```

Precisa do Docker ligado: o [Testcontainers](https://java.testcontainers.org/) sobe um PostgreSQL 16 descartável a cada
execução, aplica as migrações e roda os testes contra ele, sem tocar no banco do `docker compose`. O CI
(`.github/workflows/ci.yml`) roda o mesmo comando em todo PR e em todo push na `main`.

Cenários de aceite da entrega e onde são testados:

| Cenário | Testes |
|---|---|
| Visitante busca, escolhe a apresentação, adiciona; o login junta o carrinho | `PercursoCompletoIntegracaoTest.visitanteAteEntrega`, `MesclagemCarrinhoNoLoginIntegracaoTest` |
| Cadastro, código no canal demo, confirmação, login e compra | `PercursoCompletoIntegracaoTest.cadastroAteCompra`, `ConfirmacaoContaIntegracaoTest` |
| Entrega com frete, pagamento aprovado, pedido e estoque | `CheckoutFluxoWebIntegracaoTest`, `CheckoutServiceIntegracaoTest` |
| Remédio controlado: retirada obrigatória e entrega só após conferir a receita | `PercursoCompletoIntegracaoTest.controladoAteEntregue`, `AdminPedidosServiceTest` |
| Pix ou boleto expira e devolve o estoque uma vez; recusa permite tentar de novo | `PagamentoPedidoIntegracaoTest`, `CheckoutFluxoWebIntegracaoTest` |
| Duas compras da última unidade; duplo clique não duplica o pedido | `CheckoutServiceIntegracaoTest`, `PagamentoPedidoIntegracaoTest` |
| Admin mexe em produto e categoria, consulta cliente com auditoria e move o pedido | `ProdutoServiceIntegracaoTest`, `CategoriaServiceIntegracaoTest`, `ClienteAdminServiceIntegracaoTest` |
| Endereço ou pedido de outra pessoa dá 404; cliente em rota de admin dá 403 | `AcessoEntreContasIntegracaoTest`, `PedidosClienteIntegracaoTest` |
| Recuperação invalida o código e as sessões; logout fecha a área protegida | `RecuperacaoSenhaIntegracaoTest`, `LoginProtecaoIntegracaoTest` |

Lighthouse e axe não rodam no CI. A acessibilidade é coberta por testes de HTML (um `h1`, rótulos, erro ligado ao campo)
e pelo `ContrasteTokensTest`.

## Arquitetura

MVC no servidor: o controller só orquestra, as regras ficam nos services e cada tela recebe um `*Form` com Bean
Validation (nunca a entidade). Cada integração externa é uma interface com um adaptador de demonstração em `demo/`.

```
com.farmacia.farmaciaonline
├── config        SecurityConfig, uploads, cache
├── controller    telas Thymeleaf e advices do layout (menu, carrinho, usuário)
├── service       regras de negócio (checkout, estoque, códigos, contas...)
├── repository    Spring Data JPA
├── model         entidades JPA, forms e enums
├── security      login, tentativas, revogação de sessões, carrinho no login
├── validacao     CPF e política de senha
├── cep           ConsultaCep: viacep/ e demo/
├── gateway       GatewayDePagamento: demo/
├── mensagem      EnvioDeMensagem: demo/ (caixa-demo)
└── social        ProvedorIdentidade: demo/

src/main/resources
├── db/migration  Flyway (V1 schema, V2 seed, V3 em diante)
├── static        css (tokens.css + style.css), js, images
└── templates     fragments/layout.html (layout único) e uma pasta por área
```

## Segurança

- Dois papéis, `CLIENTE` e `ADMIN`. Páginas públicas ficam em `ROTAS_PUBLICAS` (`config/SecurityConfig`); `/admin/**` é
  só do `ADMIN` (cliente recebe 403); todo o resto exige login. O administrador não compra.
- Senhas e códigos de verificação em BCrypt; código, senha e CPF completo nunca vão para log. CSRF ligado em todo `POST`.
- Recurso de outra pessoa (pedido, endereço, cartão) responde 404. O id da conta em fluxos públicos fica na sessão,
  nunca na URL, e os formulários não aceitam perfil, status nem id.
- Login, recuperação e retomada não revelam se a conta existe. Cinco senhas erradas bloqueiam o login por 15 minutos.
- Trocar a senha encerra as outras sessões da conta.

O enunciado descreve o usuário comum como "somente listagem/visualização". Aqui o sistema é uma loja: o `CLIENTE` também
mexe no próprio carrinho e nos próprios pedidos (sem isso não haveria compra). O que é exclusivo do `ADMIN` é o cadastro
do catálogo e a administração de clientes e pedidos.

## Riscos conhecidos

Limites assumidos de propósito neste trabalho acadêmico:

- **O perfil `demo` é só para a apresentação.** Ele liga a `/caixa-demo`, o provedor social simulado e os botões que
  deixam o próprio cliente simular a aprovação ou a expiração do pagamento. Em produção, o perfil deve ser desligado.
- **Bloqueio por terceiros:** quem souber o e-mail ou CPF de alguém pode errar a senha cinco vezes e bloquear o login
  dessa conta por 15 minutos. Não há CAPTCHA nem limite por IP.
- **Tempo de resposta do bloqueio:** conta real bloqueada é recusada sem conferir a senha; identificador inexistente
  passa por um hash fictício. A mensagem é a mesma, só o tempo difere.
- **Provedor social simulado:** marcar "e-mail verificado" cria conta ativa com um e-mail que ninguém confirmou, e quem
  souber o identificador externo de uma identidade vinculada entra na conta dona dele. Só existe no perfil `demo`.
- **Cancelar pedido com Pix ou boleto gerado:** o gateway simulado não cancela a cobrança. Num gateway real, ela
  precisaria ser cancelada lá também.
- **Dependências:** a base OSV (consulta de 2026-11-04) aponta avisos no Tomcat, Jackson, Spring Web MVC, driver do
  PostgreSQL e Log4j API, todos na versão do Spring Boot 3.5.16 (a última da linha 3.5). Nenhum atinge recursos que o app
  usa.
- **Contadores na memória:** o limite de pedidos de recuperação, o de tentativas para identificador sem conta e o aviso
  de item tirado do carrinho recomeçam quando a aplicação reinicia e não são compartilhados entre instâncias.

## Quem fez o quê

### Fase 1: fundação

| Módulo | Issue | Integrante | PR |
|---|---|---|---|
| Banco: schema (`V1`), Docker e Flyway | F1-01 | Cássio (@cassioqm) | #11 |
| Usuários: entidades e repositories | F1-02 (#2) | João (@Joaomarceloap) | #12 |
| Catálogo: entidades e repositories | F1-03 (#7) | Jean (@JeanFlavioZTezini) | #17 |
| Compra: carrinho, endereço, cartão e loja | F1-04 (#13) | Kauê (@KaueAlves09) | #18, #44 |
| Pedido, pagamento, histórico e auditoria | F1-05 (#16) | Kauê (@KaueAlves09) | #19, #44 |
| Seed de demonstração (`V2`) | F1-06 (#8) | Aieser (autor do `V2` original), ajustes por Kauê (@KaueAlves09) | #15 |
| Layout-base, cabeçalho, rodapé e acessibilidade da interface | F1-07 | Kauê (@KaueAlves09) | #5 |
| Documento de acessibilidade | F1-08 | Nakao (@FernandoNakao) | #10 |
| Login e papéis (Spring Security) | F1-09 | Jean (@JeanFlavioZTezini) | #20 |
| Docs e fluxo: template de PR, README e proteção da `main` | F1-10 (#4) | Kauê (@KaueAlves09) | #23 |
| Testes com Testcontainers e CI | F1-11 (#9) | Kauê (@KaueAlves09) | #14 |
| Home e menu de categorias pelo banco | F1-12 | Cássio (@cassioqm), ajustes por Kauê (@KaueAlves09) | #22, #43 |

### Onda final: entrega e estabilização

| Módulo | Issue | Integrante | PR |
|---|---|---|---|
| Catálogo público: busca, categoria, filtros e paginação (UC01) | F-01 | Cássio (@cassioqm) | #55 |
| Detalhes do produto e apresentação comprável (UC02) | F-02 | Cássio (@cassioqm) | #58 |
| Navegação integrada, páginas de termos e revisão de interface (UC03/UC15) | F-03 (#42) | Cássio (@cassioqm) | #66 |
| Cadastro com validação e conta pendente (UC03) | F-04 | Kauê (@KaueAlves09) | #50 |
| Serviço de códigos e confirmação de conta com envio simulado (UC04) | F-05 | Kauê (@KaueAlves09) | #51 |
| Recuperação de senha sem login e revogação de sessões (UC14, UC10-A4) | F-06 | Kauê (@KaueAlves09) | #53 |
| CRUD de categorias hierárquicas (UC13) | F-07 | Nakao (@FernandoNakao) | #45 |
| CRUD de produtos, apresentações, estoque e imagens (UC12) | F-08 (#31) | Nakao (@FernandoNakao), testes e revisão visual por Kauê (@KaueAlves09) | #47, #62, #63 |
| Consulta administrativa de clientes com auditoria (UC11) | F-09 (#32) | Kauê (@KaueAlves09), repassada da trilha C | #60, #65 |
| Carrinho de visitante e cliente, com mesclagem no login (UC05) | F-10 | Aieser, integração por Kauê (@KaueAlves09) | #56 |
| CRUD de endereços e seleção no checkout (UC16) | F-11 | Aieser, integração por Kauê (@KaueAlves09) | #49 |
| Edição de dados pessoais e senha autenticada (UC07) | F-12 (#35) | Aieser, integração por Kauê (@KaueAlves09) | #57 |
| Gateway de pagamento simulado e retorno idempotente (UC08/UC09) | F-13 | João (@Joaomarceloap) | #46 |
| Checkout transacional, entrega ou retirada, estoque e expiração (UC08/UC09) | F-14 (#37) | João (@Joaomarceloap) | #52 |
| Contratos e modelos de pedidos e migrations de compatibilidade (UC06/UC08/UC14/UC17) | F-15 | Jean (@JeanFlavioZTezini), com Kauê (contatos confirmados) e João | #59 |
| Histórico e detalhe de pedidos do cliente (UC06) | F-16 | Jean (@JeanFlavioZTezini) | #61 |
| Administração de pedidos: transições, receita e histórico (UC17) | F-17 | Jean (@JeanFlavioZTezini) | #64 |
| Proteção do login e logout validado nos novos fluxos (UC10/UC15) | F-18 | Jean (@JeanFlavioZTezini), correções por Kauê (@KaueAlves09) | #68 |
| Integração, teste do percurso completo e entrega reproduzível | F-19 (#26) | Todos; integração por Kauê (@KaueAlves09) | #79 |
| Login social demonstrável com vínculo seguro de CPF (UC10) | F-20 | Kauê (@KaueAlves09) | #77 |
| Alteração de e-mail/celular com confirmação do novo contato (UC07) | F-21 | Kauê (@KaueAlves09) | #67 |
| Correção de busca e refinamentos do catálogo (UC01) | F-22 | Kauê (@KaueAlves09) | #75 |
| Filtros completos de pedidos/clientes e exportação CSV (UC06/UC11) | F-23 | Kauê (@KaueAlves09) | #75 |
| Excluir a própria conta preservando histórico necessário (regra I14) | F-24 | Kauê (@KaueAlves09) | #78 |
| Facilidades explicitamente opcionais | F-25 | Kauê (@KaueAlves09) | #76 |

### Polimento depois da entrega

| Módulo | Integrante | PR |
|---|---|---|
| Revisão visual e de UX (base, compra, conta e admin) | Kauê (@KaueAlves09) | #80, #81, #82, #83 |
| Correções de segurança da demonstração, checkout e carrinho | Kauê (@KaueAlves09) | #84, #85, #86, #87 |
| Formulários mantêm os dados após erro; limite de partes do formulário de produto | Kauê (@KaueAlves09) | #88, #89 |
| Lojas de retirada em Campo Grande e CEP pelo ViaCEP | João (@Joaomarceloap) | #90 |
| Auditoria: edição simultânea no admin, páginas de erro, cache, testes do admin, ajustes, SEO e fotos | Kauê (@KaueAlves09) | #91 a #97 |
