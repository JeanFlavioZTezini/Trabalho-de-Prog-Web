-- F-17: guardar a conferência presencial da receita com responsável e instante, e não afirmar
-- que um estorno foi concluído quando o gateway ainda não oferece essa operação.
ALTER TABLE pedido
    ADD COLUMN receita_conferida_em TIMESTAMPTZ,
    ADD COLUMN receita_conferida_por_id BIGINT REFERENCES usuario (id),
    ADD COLUMN estorno_pendente BOOLEAN NOT NULL DEFAULT FALSE,
    ADD CONSTRAINT ck_pedido_conferencia_receita CHECK (
        (receita_conferida_em IS NULL AND receita_conferida_por_id IS NULL)
        OR (receita_conferida_em IS NOT NULL AND receita_conferida_por_id IS NOT NULL)
    );
