-- Troca o placeholder do seed pelas fotos dos produtos. Só altera quem ainda está no placeholder,
-- para não apagar uma foto que o administrador já tenha trocado pela tela.
-- Continuam no placeholder: Omeprazol, Amoxicilina e Clonazepam (as farmácias não mostram a caixa
-- de medicamento sob prescrição, RDC 96/2008) e Neo Dipirona e Loratadina (sem foto da mesma apresentação).
UPDATE imagem_produto i
SET arquivo = novas.arquivo
FROM (VALUES
  (1, '/images/produtos/novalgina-dipirona-1g-20-comprimidos.webp'),
  (4, '/images/produtos/paracetamol-750mg-ems-20-comprimidos.webp'),
  (5, '/images/produtos/allegra-120mg-10-comprimidos.webp'),
  (9, '/images/produtos/vitamina-c-1g-aceviton-cimed-10-comprimidos-efervescentes.webp'),
  (10, '/images/produtos/vitamina-d3-2000ui-lavitan-cimed-30-comprimidos.webp'),
  (11, '/images/produtos/protetor-solar-neutrogena-sun-fresh-facial-fps-60.webp'),
  (12, '/images/produtos/shampoo-head-shoulders-limpeza-eficaz-200ml.webp'),
  (13, '/images/produtos/shampoo-pantene-hidratacao-400ml.webp'),
  (14, '/images/produtos/condicionador-pantene-hidratacao-400ml.webp'),
  (15, '/images/produtos/fralda-pampers-confort-sec-m-70-unidades.webp'),
  (16, '/images/produtos/lencos-umedecidos-huggies-recem-nascido-48-unidades.webp'),
  (17, '/images/produtos/alcool-gel-asseptgel-cristal.webp'),
  (18, '/images/produtos/termometro-digital-g-tech.webp')
) AS novas (produto_id, arquivo)
WHERE i.produto_id = novas.produto_id
  AND i.arquivo = '/images/produtos/placeholder.svg';
