-- As lojas de retirada do seed ficavam em São Paulo, mas a loja só atende Mato Grosso do Sul (frete demo).
-- Passam para Campo Grande/MS, com CEPs que o ViaCEP confirma para a numeração. Só muda a loja que ainda tem o
-- endereço do seed; pedidos antigos guardam a cópia do endereço (retirada_*) e não mudam.
UPDATE loja
SET cep = '79002336', logradouro = 'Rua Quatorze de Julho', numero = '2000', bairro = 'Centro',
    cidade = 'Campo Grande', uf = 'MS'
WHERE id = 1 AND cep = '01310100';

UPDATE loja
SET nome = 'Farma++ Jardim dos Estados', cep = '79020001', logradouro = 'Avenida Afonso Pena', numero = '4100',
    bairro = 'Jardim dos Estados', cidade = 'Campo Grande', uf = 'MS'
WHERE id = 2 AND cep = '04094050';
