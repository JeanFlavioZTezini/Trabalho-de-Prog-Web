-- F-22: busca sem acento (unaccent) e "você quis dizer" (pg_trgm: similarity).
-- Extensões "trusted" no PostgreSQL 13+: o dono do banco cria sem superusuário.
-- Sem índice funcional: unaccent() não é IMMUTABLE; o catálogo é pequeno.
CREATE EXTENSION IF NOT EXISTS unaccent;
CREATE EXTENSION IF NOT EXISTS pg_trgm;
