-- F-15 (UC17): o histórico passa a guardar também o status de origem de cada mudança.
--
-- Por que V5: a V3 (contatos confirmados, F-05) e a V4 (checkout, F-14) já estão na main. A V1 já aceita
-- PRONTO_PARA_RETIRADA nas duas constraints de status, e a V4 já criou pedido.versao e as colunas retirada_*;
-- por isso esta migration só acrescenta o que ainda faltava.
--
-- A coluna é anulável de propósito:
--   * o primeiro registro de um pedido (criação) não tem status de origem;
--   * eventos gravados antes desta migration não guardaram o estado anterior, e inventá-lo a partir da ordem
--     cronológica seria registrar um dado que o sistema nunca registrou. Ficam NULL.
ALTER TABLE historico_status_pedido
    ADD COLUMN status_anterior VARCHAR(25),
    ADD CONSTRAINT ck_historico_status_anterior CHECK (status_anterior IS NULL OR status_anterior IN
        ('AGUARDANDO_PAGAMENTO', 'PAGAMENTO_APROVADO', 'EM_SEPARACAO', 'ENVIADO', 'PRONTO_PARA_RETIRADA', 'ENTREGUE', 'CANCELADO'));
