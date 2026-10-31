-- F-05 (UC04): cada contato confirmado é registrado à parte. Confirmar o e-mail não confirma o celular.
ALTER TABLE usuario
    ADD COLUMN email_confirmado_em   TIMESTAMPTZ,
    ADD COLUMN celular_confirmado_em TIMESTAMPTZ;

-- Contas que já estavam ativas antes desta migration (as do seed) entravam pelo e-mail: ele conta como confirmado.
UPDATE usuario SET email_confirmado_em = criado_em WHERE status = 'ATIVA' AND email IS NOT NULL;
