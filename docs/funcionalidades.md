# Funcionalidades

Regras, limites e rotas de cada funcionalidade do Farma++. Para rodar o projeto e ver as contas de teste, veja o
[README](../README.md); para convenções de código, migrations e testes, veja [desenvolvimento.md](desenvolvimento.md).

Sumário: [Catálogo](#catálogo-f-01-f-02-f-22) · [Cadastro](#cadastro-f-04) ·
[Confirmação e códigos](#confirmação-da-conta-e-códigos-f-05) · [Recuperação de senha](#recuperação-de-senha-f-06) ·
[Login](#login-e-proteção-f-18) · [Minha conta](#minha-conta-f-12-f-21-f-24) · [Login social](#login-social-simulado-f-20) ·
[Carrinho](#carrinho-f-10) · [Endereços](#endereços-f-11) · [Checkout](#checkout-f-13-f-14) ·
[Meus pedidos](#meus-pedidos-f-16-f-23) · [Facilidades opcionais](#facilidades-opcionais-f-25) ·
[Admin: categorias](#admin-categorias-f-07) · [Admin: produtos](#admin-produtos-f-08) ·
[Admin: clientes](#admin-clientes-f-09-f-23) · [Admin: pedidos](#admin-pedidos-f-17)

## Catálogo (F-01, F-02, F-22)

- **Home e menu** vêm do banco: o menu mostra as categorias raiz ativas e as filhas, por `ordem`; a home mostra até 8
  "mais comprados" e 8 "mais vistos", só de produtos ativos, com o preço da apresentação padrão e os selos
  "Indisponível" e "Exige receita".
- **Preço, SKU, EAN e estoque ficam na apresentação** (ex.: caixa com 10 ou 20 comprimidos), nunca no produto. Produto
  sem nenhuma apresentação disponível continua no catálogo como "Indisponível", a menos que o filtro "Somente
  disponíveis" esteja marcado.
- **Busca** (`GET /produtos?q=`): ignora acento e maiúsculas e procura em nome, marca, princípio ativo, indicação e EAN.
  Quando não acha nada, sugere "Você quis dizer" trocando cada palavra pela mais parecida (`pg_trgm`, limite
  `app.catalogo.sugestao-similaridade=0.4`). O texto digitado não vai para o log.
- **Filtros e ordenação:** categoria, preço mínimo e máximo, disponibilidade, marca, princípio ativo, forma, tipo
  (genérico, similar, referência) e receita; ordenação por nome, preço, mais vendidos ou relevância. A paginação mantém
  os filtros. Não há avaliação de produtos.
- **Detalhe** (`GET /produtos/{id}`): dados, indicações, link da bula (só endereço `http`/`https` válido), imagens e a
  escolha da apresentação, com preço e estoque. Migalhas de Início até a categoria mais funda do produto. Produto
  inexistente ou inativo responde 404. Cada abertura soma uma visualização.

## Cadastro (F-04)

- `/cadastro`: CPF válido pelos dígitos verificadores, 18 anos ou mais, gênero masculino ou feminino, celular com DDD e
  nono dígito (gravado só com dígitos), e-mail válido, senha com 8 caracteres ou mais, letra e número (até 72 bytes,
  limite do BCrypt) e aceite dos termos. CPF e e-mail não podem ter outra conta (sem diferenciar maiúsculas); cadastros
  simultâneos iguais são barrados pelo índice único do banco.
- **A conta nasce PENDENTE e CLIENTE**, com senha em BCrypt, a versão dos termos (`app.termos.versao`, hoje `1.0`) e a
  data do aceite. O formulário não tem perfil, status nem senha pronta: `perfil=ADMIN` enviado à força é ignorado.
- Depois de gravar, o id da conta vai para a **sessão** (`contaEmConfirmacao`) e a pessoa segue para `/confirmar-conta`.
- `?retorno=carrinho` ou `?retorno=checkout` (só essas duas palavras) faz a pessoa voltar para lá depois do login.
- Os documentos `/institucional/termos-de-uso` e `/institucional/privacidade` mostram a mesma versão gravada no aceite
  (F-03). Mudou o texto, atualize `app.termos.versao`.

## Confirmação da conta e códigos (F-05)

- `/confirmar-conta` mostra o e-mail e o celular mascarados; a pessoa escolhe o canal, recebe um código de 6 números e o
  digita. Com o código certo, aquele contato fica confirmado e a conta vira ATIVA. Confirmar o e-mail não confirma o
  celular, e vice-versa.
- **Retomar depois:** o login de conta pendente mostra "Confirmar minha conta" (`/confirmar-conta/retomar`), que pede
  e-mail ou CPF **e a senha**.
- **Códigos** (`CodigoVerificacaoService`): gerados com `SecureRandom`, guardados só como hash, de uso único e separados
  por finalidade (`CADASTRO`, `ALTERAR_EMAIL`, `ALTERAR_CELULAR`, `RECUPERAR_SENHA`, `VINCULO_SOCIAL`). Tentativa errada
  fica gravada mesmo com a recusa, e duas requisições simultâneas com o código certo não passam as duas.

  | Configuração | Valor | O que faz |
  |---|---|---|
  | `app.codigos.validade` | `10m` | tempo de vida do código |
  | `app.codigos.tentativas-maximas` | `5` | erros até o código ser bloqueado |
  | `app.codigos.intervalo-reenvio` | `60s` | espera para pedir outro (o novo invalida o anterior) |
  | `app.codigos.envios-por-hora` | `5` | envios na última hora antes do bloqueio |
  | `app.codigos.bloqueio-envio` | `15m` | depois do limite, intervalo mínimo entre envios |

- **Caixa de demonstração:** sem serviço real de e-mail ou SMS, as mensagens ficam em `/caixa-demo` (perfil `demo`). Cada
  mensagem guarda a conta dona: a pessoa vê as da conta logada e as da conta em confirmação na sessão; o administrador vê
  todas. Por isso os códigos de recuperação de senha e de vínculo social só aparecem para o administrador (quem pede o
  envio não prova ser o dono da conta). O código nunca vai para log nem para outra resposta.

## Recuperação de senha (F-06)

- `/recuperar-senha` (e-mail ou CPF e o canal) → `/recuperar-senha/codigo` → `/recuperar-senha/nova-senha`. A conta em
  recuperação fica na sessão.
- **Resposta sempre igual** para conta existente, inexistente, pendente ou excluída, e para código errado. O código só
  vai para um contato já confirmado.
- **Limites:** os da F-05 e, além deles, 60 s e 5 pedidos por hora **por identificador digitado**, exista a conta ou não
  (na memória; zera ao reiniciar).
- **Nova senha:** mesma regra do cadastro e diferente da atual. Trocar a senha encerra todas as sessões da conta
  (`RevogacaoDeSessoes`) e zera o bloqueio de login.

## Login e proteção (F-18)

- O campo aceita **e-mail ou CPF** (com ou sem pontuação). Conta pendente tem mensagem própria com o link de retomada;
  qualquer outro erro mostra a mesma mensagem genérica. O ADMIN vai para `/admin`; o cliente volta para a página que
  tentou abrir.
- **Bloqueio:** cinco senhas erradas seguidas bloqueiam o login por 15 minutos. Identificador sem conta ativa é contado
  na memória com o mesmo limite, para a sexta tentativa responder igual em conta real e em e-mail inventado.
- **Sessão:** expira após 30 minutos sem uso. O logout é `POST /logout` e as páginas protegidas saem com
  `Cache-Control: no-store` ("voltar" não as mostra de novo).
- Erro de login devolve o e-mail ou CPF digitado (pela sessão, nunca pela URL); a senha volta vazia.

## Minha conta (F-12, F-21, F-24)

| Rota | O que faz |
|---|---|
| `GET /conta` | dados pessoais e contatos, com "confirmado" ou "não confirmado" |
| `GET/POST /conta/editar` | altera nome, nascimento e gênero (CPF, e-mail, perfil e status enviados à força são ignorados) |
| `GET/POST /conta/senha` | troca a senha com a senha atual; encerra as **outras** sessões da conta |
| `GET /conta/contato/{email\|celular}` e `POST .../enviar`, `.../confirmar` | troca o e-mail ou o celular |
| `GET/POST /conta/excluir` | exclui a própria conta |

- **Senha atual errada** em qualquer dessas telas conta como falha de login (cinco bloqueiam por 15 minutos).
- **Troca de contato:** pede a senha atual, manda o código ao contato **novo** e só troca depois do código certo. Trocar
  o e-mail (que é login) encerra as outras sessões.
- **Exclusão:** só conta CLIENTE ativa, com a senha atual e a palavra `EXCLUIR`. Pedido em andamento impede a exclusão.
  Endereços, cartões salvos, carrinho, vínculos sociais e códigos são apagados; nome, e-mail, CPF e celular são
  anonimizados; pedidos, pagamentos e histórico ficam (registro de vendas), e a auditoria grava `CONTA_EXCLUIR`. O e-mail
  e o CPF ficam livres para um cadastro novo. Conta que só entra pelo login social precisa definir uma senha antes.

## Login social simulado (F-20)

- Google e Facebook são **simulados** (`social/demo/DemoProvedorIdentidade`, só no perfil `demo`; sem ele os botões somem
  e `/login/social/**` responde 404). Não há OAuth real.
- O botão leva à tela "Provedor simulado" (identificador, nome, e-mail opcional e "e-mail verificado", desmarcado por
  padrão). O retorno confere um `state` guardado na sessão.
- Identidade já vinculada a conta ativa entra direto. Sem vínculo, a pessoa completa CPF, nascimento, gênero, celular e
  termos: CPF novo cria uma conta CLIENTE sem senha (ativa só se o e-mail veio verificado); CPF de conta existente só
  vincula com a **senha dessa conta** ou com um código mandado ao e-mail dela. Administrador nunca entra por aqui.
- Logado, "Vincular Google/Facebook" em Minha conta liga a identidade à própria conta.

## Carrinho (F-10)

| Rota | O que faz |
|---|---|
| `GET /carrinho` | lista, com total recalculado no servidor |
| `POST /carrinho/itens` | adiciona (`apresentacaoId`, `quantidade`); se já existe, soma |
| `POST /carrinho/itens/{id}/quantidade` | altera; menos de 1 pede confirmação de remoção |
| `GET/POST /carrinho/itens/{id}/remover`, `GET/POST /carrinho/esvaziar` | confirmação e remoção |
| `POST /carrinho/itens/{id}/aceitar-preco` | aceita o preço novo de um item |
| `GET /carrinho/finalizar` | revalida e segue para `/checkout`; visitante vai ao login e volta |

- **Dono:** a conta logada ou, para visitante, o cookie `carrinho_token` (UUID, `HttpOnly`, 30 dias). Nenhuma rota recebe
  id de carrinho; item de outro carrinho dá "não está no seu carrinho".
- **Revalida a cada operação e exibição:** produto e apresentação ativos, estoque e limite por pedido. Item que ficou
  indisponível sai do total e bloqueia o "Finalizar"; produto desativado ou excluído pela farmácia gera aviso no topo.
- **Login junta os carrinhos** por apresentação, respeitando estoque e limite, e avisa "Você tem N unidades no seu
  carrinho". O administrador não compra: não vê carrinho e o login dele não junta nada.
- Na home e na busca, "Adicionar ao carrinho" funciona sem sair da página (`carrinho-rapido.js`, com painel de
  confirmação); sem JavaScript, o formulário leva ao `/carrinho`.

## Endereços (F-11)

- `/enderecos` (lista, novo, editar, excluir com confirmação, "Tornar principal"). Só conta CLIENTE ativa; endereço de
  outra pessoa responde 404.
- Destinatário, CEP, logradouro, número, bairro, cidade e UF obrigatórios. O CEP com 8 números preenche o resto pelo
  ViaCEP (`cep.js`; sem JavaScript, o botão "Buscar CEP"). CEP inexistente ou ViaCEP fora do ar: a tela avisa e a
  pessoa preenche à mão.
- Endereço fora da área de entrega pode ser salvo; quem bloqueia é o checkout. O pedido guarda uma **cópia** do endereço,
  então editar ou excluir depois não muda pedidos antigos.
- O primeiro endereço nasce principal, e o checkout abre com o principal marcado.

## Checkout (F-13, F-14)

Só conta CLIENTE ativa (administrador e conta pendente recebem 403).

| Rota | O que faz |
|---|---|
| `GET /checkout` | escolha de entrega ou retirada, endereço, loja, pagamento e parcelas |
| `GET /checkout/resumo` | resumo calculado no servidor |
| `POST /checkout/confirmar` | registra o pedido e faz a primeira cobrança |
| `GET /checkout/pedidos/{id}` | situação do pedido (de outra pessoa: 404), nova tentativa de pagamento e "Já paguei" |
| `POST /checkout/pedidos/{id}/demo/{aprovar,expirar}` | simula o evento de Pix ou boleto (só perfil `demo`) |

- **O servidor recalcula tudo:** do navegador só vêm as escolhas. Se o resumo mudou desde que foi mostrado (preço,
  estoque, endereço), a pessoa volta ao resumo antes de confirmar.
- **Frete** só para Mato Grosso do Sul (CEP e UF precisam combinar): Campo Grande R$ 9,90 em 2 dias, demais cidades
  R$ 14,90 em 3 dias. Retirada não tem frete. **Remédio com receita obriga a retirada** (a receita é apresentada na loja).
- **Estoque:**

  | Situação | Pedido | Estoque |
  |---|---|---|
  | Pedido registrado, ou Pix/boleto pendente | `AGUARDANDO_PAGAMENTO` | reservado |
  | Pagamento aprovado | `PAGAMENTO_APROVADO` | reserva consumida |
  | Pagamento recusado | continua aguardando | continua reservado; pode tentar de novo até expirar |
  | Expiração ou cancelamento | `CANCELADO` | devolvido uma vez |

- **Validade da reserva:** Pix 30 minutos, boleto 3 dias e os demais casos `app.checkout.validade-reserva` (30 min). O
  `ExpiracaoPedidosJob` roda a cada minuto e consulta o gateway antes de expirar.
- **Concorrência:** a reserva é um `UPDATE` condicional, então duas compras da última unidade não passam as duas; o
  mesmo envio repetido (duplo clique) devolve o mesmo pedido, sem nova cobrança.
- **Gateway fora da transação:** se a resposta se perder, a tentativa fica pendente e pode ser repetida com a mesma
  chave, sem cobrar de novo.
- O resumo do pedido sai por e-mail simulado (`/caixa-demo`) quando o pedido é aceito. Erro no cartão mantém validade,
  nome e forma de pagamento; número e código de segurança nunca voltam.

## Meus pedidos (F-16, F-23)

- `/pedidos` (lista, 10 por página, do mais recente) e `/pedidos/{id}` (detalhe). Só leitura; pedido de outra pessoa
  responde 404 e administrador recebe 403.
- O detalhe mostra itens, preços e endereço **da hora da compra**, pagamento mascarado (`VISA final 1111`) e a linha do
  tempo do status, sem o autor nem a observação interna do administrador.
- Filtros por situação e período (`de` e `ate`, inclusivos, horário de São Paulo); data inicial depois da final mostra
  erro e não consulta.

## Facilidades opcionais (F-25)

- **Comprar novamente:** no detalhe do pedido, leva os itens ao carrinho com preço e estoque de agora; item recusado vira
  aviso e não impede os outros. Nada é comprado direto.
- **Endereço principal:** ver [Endereços](#endereços-f-11).
- **Cartão salvo:** "Salvar este cartão" só vale se o pagamento for aprovado; o banco guarda o token do gateway, a
  bandeira, os 4 últimos números, a validade e o nome. Pagar com cartão salvo pede o código de segurança de novo. Remover
  em "Meus cartões" (`/conta/cartoes`). No gateway simulado o token fica na memória: depois de reiniciar, o cartão salvo é
  recusado e basta informar o cartão de novo.

## Admin: categorias (F-07)

- `/admin/categorias`: lista em árvore, nova, editar, excluir com confirmação, inativar e reatribuir.
- Dois níveis (raiz e subcategoria), sem ciclo e sem nome repetido sob o mesmo pai. Inativar uma categoria inativa as
  subcategorias junto. Categoria com subcategorias não é excluída; com produtos, é preciso reatribuir os produtos a
  outra categoria ativa ou inativá-la.
- Dois administradores na mesma categoria: quem salva por último é avisado para recarregar, em vez de apagar o que o
  outro gravou.

## Admin: produtos (F-08)

| Rota | O que faz |
|---|---|
| `GET /admin/produtos` | lista com busca (nome, marca, princípio ativo, SKU ou EAN) e filtro ativos/inativos |
| `GET /admin/produtos/novo`, `POST /admin/produtos` | cadastro |
| `GET /admin/produtos/{id}/editar`, `POST /admin/produtos/{id}` | edição |
| `POST /admin/produtos/{id}/excluir` | exclusão |

- Nome e marca obrigatórios, ao menos uma categoria ativa, tarja vermelha ou preta exige receita. Cada apresentação tem
  descrição, SKU e EAN únicos, preço maior que zero, estoque e limite por pedido. Apresentação não é apagada: é desativada.
- **Edição concorrente:** se uma compra mexeu no estoque depois que o formulário foi aberto, ou outro administrador salvou
  o mesmo produto, o salvamento é recusado com aviso para recarregar.
- **Exclusão:** produto com pedido é só inativado (o histórico fica); sem pedido, é apagado de vez. A confirmação abre numa
  janela da própria página.
- **Fotos:** JPG, PNG ou WebP de até 2 MB, conferidas pela extensão, pelo tipo e pelo conteúdo; gravadas com nome UUID em
  `app.uploads.dir` (padrão `uploads/`, fora do JAR) e servidas em `/uploads/produtos/**`. O envio aceita até 10 MB por
  arquivo e 30 MB no total, e `server.tomcat.max-part-count=120` (o formulário passa das 50 partes do padrão).
- Mensagens de sucesso e erro do admin aparecem como aviso no canto da tela, que some em cerca de 6 segundos.

## Admin: clientes (F-09, F-23)

- `/admin/clientes` (20 por página, busca por nome, e-mail ou CPF, filtro de situação e período de cadastro) e
  `/admin/clientes/{id}` (dados e os 10 pedidos mais recentes). Somente leitura.
- A lista mostra o CPF mascarado; o detalhe mostra o CPF completo. Senha, tentativas de login, códigos e dados de
  pagamento nunca aparecem.
- **Auditoria** na mesma transação da consulta (se o log falhar, a consulta falha): `CLIENTE_LISTAR`,
  `CLIENTE_VISUALIZAR` e `CLIENTE_EXPORTAR`, com autor, IP e data, sem o texto buscado.
- **CSV** (`/admin/clientes/exportar.csv`): mesmos filtros da tela, até 5.000 linhas, UTF-8 com BOM e separador `;` (abre
  no Excel em português), CPF, e-mail e celular mascarados, e proteção contra fórmula na planilha.

## Admin: pedidos (F-17)

- `/admin/pedidos` (filtros por número, situação, cliente e período) e `/admin/pedidos/{id}` (itens, pagamento,
  endereço ou loja, histórico, mudança de status e conferência da receita).
- **Transições permitidas:**

  | De | Para |
  |---|---|
  | Aguardando pagamento | Cancelado |
  | Pagamento aprovado | Em separação, Cancelado |
  | Em separação | Enviado (entrega) ou Pronto para retirada (retirada), Cancelado |
  | Enviado | Entregue |
  | Pronto para retirada | Entregue (com receita: só depois de registrar a conferência presencial) |

- Cancelar devolve o estoque uma vez; pedido já pago cancelado fica marcado com estorno pendente. A mudança usa a versão
  do pedido: dois administradores ao mesmo tempo não se sobrescrevem. Cada mudança entra no histórico com o autor e a
  observação.
