-- Lock otimista na edição pelo admin (UC12, UC13): dois administradores com o mesmo produto ou a mesma categoria aberta
-- não sobrescrevem um ao outro em silêncio; o segundo a salvar é avisado para recarregar. Os contadores de vendas e de
-- visualizações são UPDATEs diretos e não mexem nesta coluna.
ALTER TABLE produto ADD COLUMN versao BIGINT NOT NULL DEFAULT 0;
ALTER TABLE categoria ADD COLUMN versao BIGINT NOT NULL DEFAULT 0;
