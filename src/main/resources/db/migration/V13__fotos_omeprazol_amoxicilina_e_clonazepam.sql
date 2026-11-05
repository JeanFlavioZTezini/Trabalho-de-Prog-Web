-- Fotos dos três medicamentos que ainda estavam no placeholder: Omeprazol, Amoxicilina e Clonazepam.
-- Decisão revista em 2026-11-04: a V7 e a V10 deixavam os dois sob prescrição sem a foto da caixa (as farmácias mostram
-- imagem ilustrativa, RDC 96/2008); agora o catálogo de demonstração mostra a caixa de todos.
-- As marcas passam a ser as das caixas fotografadas, como a Loratadina na V10; SKUs, preços e estoque não mudam.
-- Como na V7, só troca quem ainda está no placeholder e quem ainda tem a marca do seed, para não apagar o que o
-- administrador já alterou.
UPDATE imagem_produto i
SET arquivo = novas.arquivo
FROM (VALUES
  (6, '/images/produtos/omeprazol-20mg-avivia-14-capsulas.webp'),
  (7, '/images/produtos/amoxicilina-500mg-mintlab-21-capsulas.webp'),
  (8, '/images/produtos/clonazepam-2mg-milab-30-comprimidos.webp')
) AS novas (produto_id, arquivo)
WHERE i.produto_id = novas.produto_id
  AND i.arquivo = '/images/produtos/placeholder.svg';

UPDATE produto SET marca = 'Avivia' WHERE id = 6 AND marca = 'Medley';
UPDATE produto SET marca = 'Mintlab' WHERE id = 7 AND marca = 'Medley';
UPDATE produto SET marca = 'Milab' WHERE id = 8 AND marca = 'Eurofarma';
