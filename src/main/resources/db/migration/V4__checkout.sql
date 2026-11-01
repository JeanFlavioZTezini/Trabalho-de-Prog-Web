-- F-14 (UC08/UC09): ajustes de pedido e pagamento para o checkout. Colunas novas ficam anuláveis porque pedidos
-- anteriores a esta migration não têm esses dados.

ALTER TABLE pedido
    ADD COLUMN versao                   BIGINT NOT NULL DEFAULT 0,  -- @Version: atualização concorrente de status (F-17)
    ADD COLUMN chave_idempotencia       UUID,                       -- gerada ao mostrar o resumo: mesmo envio, mesmo pedido
    ADD COLUMN resumo_enviado_em        TIMESTAMPTZ,                -- NULL: resumo ainda não enviado (permite reenviar)
    ADD COLUMN retirada_nome            VARCHAR(100),               -- cópia da loja: mudar a loja não altera o pedido
    ADD COLUMN retirada_cep             VARCHAR(8),
    ADD COLUMN retirada_logradouro      VARCHAR(200),
    ADD COLUMN retirada_numero          VARCHAR(20),
    ADD COLUMN retirada_bairro          VARCHAR(100),
    ADD COLUMN retirada_cidade          VARCHAR(100),
    ADD COLUMN retirada_uf              VARCHAR(2),
    ADD COLUMN retirada_horario         VARCHAR(200);

CREATE UNIQUE INDEX ux_pedido_chave ON pedido (usuario_id, chave_idempotencia);
CREATE INDEX ix_pedido_expiracao ON pedido (expira_em) WHERE status = 'AGUARDANDO_PAGAMENTO';

ALTER TABLE pagamento
    ADD COLUMN chave_idempotencia UUID,          -- mesma tentativa reenviada não cobra de novo
    ADD COLUMN mensagem           VARCHAR(255);  -- último retorno do gateway, para orientar a pessoa

CREATE UNIQUE INDEX ux_pagamento_chave ON pagamento (pedido_id, chave_idempotencia);
CREATE UNIQUE INDEX ux_pagamento_transacao ON pagamento (transacao_gateway_id);

-- Um pedido tem no máximo uma tentativa em aberto ou aprovada: o banco impede a dupla aprovação.
CREATE UNIQUE INDEX ux_pagamento_ativo ON pagamento (pedido_id) WHERE status IN ('PENDENTE', 'APROVADO');
