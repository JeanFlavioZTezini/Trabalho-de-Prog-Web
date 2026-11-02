-- F-25 (UC16 opcional): endereço principal do cliente, pré-selecionado no checkout.
-- No máximo um principal por cliente (índice único parcial). Pedidos não mudam: guardam a cópia do endereço.
ALTER TABLE endereco ADD COLUMN principal BOOLEAN NOT NULL DEFAULT FALSE;

CREATE UNIQUE INDEX ux_endereco_principal ON endereco (usuario_id) WHERE principal;

-- Quem já tem endereço ganha como principal o mais recente (era o que o checkout já pré-selecionava).
UPDATE endereco SET principal = TRUE
WHERE id IN (SELECT DISTINCT ON (usuario_id) id FROM endereco ORDER BY usuario_id, criado_em DESC, id DESC);
