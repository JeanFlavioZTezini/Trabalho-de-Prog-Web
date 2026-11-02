-- Revisão visual: foto para os dois produtos sem receita que ficaram no placeholder na V7.
-- Neo Dipirona: foto da dipirona 500mg da Neo Química (mesma marca e dosagem; a embalagem fotografada é a cartela).
-- Loratadina: não há loratadina genérica da EMS à venda; a marca passa a ser Cimed, a da foto (12 comprimidos).
-- Continuam no placeholder Omeprazol, Amoxicilina e Clonazepam: as farmácias mostram imagem ilustrativa, e não a
-- caixa, de medicamento sob prescrição (RDC 96/2008).
-- Como na V7, só troca quem ainda está no placeholder, para não apagar uma foto trocada pelo administrador.
UPDATE imagem_produto i
SET arquivo = novas.arquivo
FROM (VALUES
  (3, '/images/produtos/neo-dipirona-500mg-neo-quimica.webp'),
  (19, '/images/produtos/loratadina-10mg-cimed-12-comprimidos.webp')
) AS novas (produto_id, arquivo)
WHERE i.produto_id = novas.produto_id
  AND i.arquivo = '/images/produtos/placeholder.svg';

UPDATE produto SET marca = 'Cimed' WHERE id = 19 AND marca = 'EMS';
