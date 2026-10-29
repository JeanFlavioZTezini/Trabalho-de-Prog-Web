<!-- Título do PR: código da issue + descrição curta. Ex.: "F1-05: Entidades e repositories de Pedido" -->
Closes #

## O que muda
-

## Como testar
-

## Checklist
- [ ] Compila e `./mvnw verify` passa localmente (Docker ligado)
- [ ] CI verde neste PR
- [ ] Se tem tela: conferi o [checklist de acessibilidade](https://github.com/JeanFlavioZTezini/Trabalho-de-Prog-Web/blob/main/docs/acessibilidade.md) e anexei os resultados do Lighthouse/axe
- [ ] Se tem rota nova: está protegida corretamente (pública só em `ROTAS_PUBLICAS`; administração sob `/admin/**`)
- [ ] Sem senha, token ou outro segredo no código
- [ ] Se mudou migração (V1/V2): avisei o time para rodar `docker compose down -v`
- [ ] Atualizei a tabela "Quem fez o quê" do README
- [ ] Aprovado por 1 colega de outra trilha
- [ ] Consigo explicar o código do PR sem consultar
