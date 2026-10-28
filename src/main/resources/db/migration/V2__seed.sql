
-- Destino: src/main/resources/db/migration/V2__seed.sql
--
-- Usuários de teste (senha de todos: Farma@123):
--   admin@farma.com     ADMIN    ATIVA     sem CPF
--   cliente@farma.com   CLIENTE  ATIVA     CPF 52998224725
--   pendente@farma.com  CLIENTE  PENDENTE  CPF 11144477735   (UC04-A3)


-- 1. USUÁRIOS
INSERT INTO usuario (id, cpf, nome, data_nascimento, genero, celular, email, senha_hash,
                     perfil, status, termos_versao, termos_aceitos_em) VALUES
  (1, NULL, 'Administrador Farma++', DATE '1990-01-15', NULL, NULL, 'admin@farma.com', '$2a$10$U.QJ3Cn.DKAR7tAw4PdtBufnRt8qSQRv3M7hlFSINY92GvcM8fnTm', 'ADMIN', 'ATIVA', '1.0', now()),
  (2, '52998224725', 'Cliente Teste', DATE '1995-05-20', 'FEMININO', '11987654321', 'cliente@farma.com', '$2a$10$U.QJ3Cn.DKAR7tAw4PdtBufnRt8qSQRv3M7hlFSINY92GvcM8fnTm', 'CLIENTE', 'ATIVA', '1.0', now()),
  (3, '11144477735', 'Cliente Pendente', DATE '1998-08-10', 'MASCULINO', '11912345678', 'pendente@farma.com', '$2a$10$U.QJ3Cn.DKAR7tAw4PdtBufnRt8qSQRv3M7hlFSINY92GvcM8fnTm', 'CLIENTE', 'PENDENTE', '1.0', now());



-- 2. CATEGORIAS
INSERT INTO categoria (id, nome, descricao, categoria_pai_id, ordem, exige_receita) VALUES
  (1, 'Medicamentos', 'Medicamentos isentos e sujeitos a prescrição', NULL, 1, FALSE),
  (2, 'Saúde e bem-estar', 'Vitaminas, primeiros socorros e aparelhos de saúde', NULL, 2, FALSE),
  (3, 'Beleza e cuidados pessoais', 'Cabelos, pele e proteção solar', NULL, 3, FALSE),
  (4, 'Mamãe e bebê', 'Fraldas, higiene e cuidados com o bebê', NULL, 4, FALSE),
  (5, 'Casa e outros', 'Limpeza e utilidades para o dia a dia', NULL, 5, FALSE);

INSERT INTO categoria (id, nome, descricao, categoria_pai_id, ordem, exige_receita) VALUES
  (11, 'Dor e febre', NULL, 1, 1, FALSE),
  (12, 'Gripe e resfriado', NULL, 1, 2, FALSE),
  (13, 'Alergias', NULL, 1, 3, FALSE),
  (14, 'Estômago e digestão', NULL, 1, 4, FALSE),
  (15, 'Antibióticos', 'Venda mediante receita (tarja vermelha)', 1, 5, TRUE),
  (16, 'Controlados', 'Medicamentos de tarja preta, com retenção de receita', 1, 6, TRUE),
  (21, 'Vitaminas e suplementos', NULL, 2, 1, FALSE),
  (22, 'Primeiros socorros', NULL, 2, 2, FALSE),
  (23, 'Aparelhos e monitoramento', NULL, 2, 3, FALSE),
  (24, 'Saúde bucal', NULL, 2, 4, FALSE),
  (31, 'Cabelos', NULL, 3, 1, FALSE),
  (32, 'Pele e rosto', NULL, 3, 2, FALSE),
  (33, 'Proteção solar', NULL, 3, 3, FALSE),
  (34, 'Higiene pessoal', NULL, 3, 4, FALSE),
  (41, 'Fraldas', NULL, 4, 1, FALSE),
  (42, 'Higiene do bebê', NULL, 4, 2, FALSE),
  (43, 'Alimentação infantil', NULL, 4, 3, FALSE),
  (44, 'Gestantes e amamentação', NULL, 4, 4, FALSE),
  (51, 'Limpeza e desinfecção', NULL, 5, 1, FALSE),
  (52, 'Conveniência', NULL, 5, 2, FALSE),
  (53, 'Utilidades', NULL, 5, 3, FALSE);

-- 3. LOJAS PARA RETIRADA (I3)
INSERT INTO loja (id, nome, cep, logradouro, numero, bairro, cidade, uf, horario_funcionamento) VALUES
  (1, 'Farma++ Centro',   '01310100', 'Avenida Paulista',      '1000', 'Bela Vista',  'São Paulo', 'SP', 'Seg a Sáb, 8h às 22h; Dom, 9h às 18h'),
  (2, 'Farma++ Zona Sul', '04094050', 'Avenida Ibirapuera',    '2000', 'Moema',       'São Paulo', 'SP', 'Todos os dias, 8h às 20h');


-- 4. PRODUTOS
INSERT INTO produto (id, nome, marca, principio_ativo, tipo, forma_farmaceutica, tarja, exige_receita,
                     indicacao, acao, via_administracao, publico_alvo, ativo,
                     visualizacoes, qtd_vendida, atualizado_por_id) VALUES
  (1, 'Novalgina Dipirona 1g', 'Sanofi', 'dipirona monoidratada', 'REFERENCIA', 'comprimido', 'SEM_TARJA', FALSE, E'- Alívio da dor\n- Redução da febre', 'Analgésico e antitérmico', 'Via oral', 'Adultos e adolescentes acima de 15 anos', TRUE, 9800, 1520, 1),
  (2, 'Dipirona Monoidratada 500mg', 'Prati, Donaduzzi', 'dipirona monoidratada', 'GENERICO', 'comprimido', 'SEM_TARJA', FALSE, E'- Alívio da dor\n- Redução da febre', 'Analgésico e antitérmico', 'Via oral', 'Adultos e crianças acima de 15 anos', TRUE, 7200, 1340, 1),
  (3, 'Neo Dipirona 500mg', 'Neo Química', 'dipirona sódica', 'SIMILAR', 'comprimido', 'SEM_TARJA', FALSE, E'- Alívio da dor\n- Redução da febre', 'Analgésico e antitérmico', 'Via oral', 'Adultos e crianças acima de 15 anos', TRUE, 3100, 610, 1),
  (4, 'Paracetamol 750mg', 'EMS', 'paracetamol', 'GENERICO', 'comprimido', 'SEM_TARJA', FALSE, E'- Dor leve a moderada\n- Febre\n- Sintomas de gripe', 'Analgésico e antitérmico', 'Via oral', 'Adultos e crianças acima de 12 anos', TRUE, 5400, 980, 1),
  (5, 'Allegra 120mg', 'Sanofi', 'cloridrato de fexofenadina', 'REFERENCIA', 'comprimido', 'SEM_TARJA', FALSE, E'- Rinite alérgica\n- Urticária', 'Anti-histamínico', 'Via oral', 'Adultos e crianças acima de 12 anos', TRUE, 2600, 450, 1),
  (6, 'Omeprazol 20mg', 'Medley', 'omeprazol', 'GENERICO', 'cápsula', 'SEM_TARJA', FALSE, E'- Azia\n- Refluxo gastroesofágico', 'Inibidor da bomba de prótons', 'Via oral', 'Adultos', TRUE, 4100, 870, 1),
  (7, 'Amoxicilina 500mg', 'Medley', 'amoxicilina tri-hidratada', 'GENERICO', 'cápsula', 'VERMELHA', TRUE, '- Infecções bacterianas, conforme prescrição médica', 'Antibiótico', 'Via oral', 'Uso adulto, sob prescrição', TRUE, 7500, 320, 1),
  (8, 'Clonazepam 2mg', 'Eurofarma', 'clonazepam', 'GENERICO', 'comprimido', 'PRETA', TRUE, '- Uso sob prescrição médica com retenção de receita', 'Ansiolítico e anticonvulsivante', 'Via oral', 'Uso adulto, sob prescrição', TRUE, 1700, 150, 1),
  (9, 'Vitamina C 1g Efervescente', 'Cimed', NULL, NULL, 'comprimido efervescente', 'SEM_TARJA', FALSE, '- Suplemento de vitamina C', 'Suplemento vitamínico', 'Dissolver em água e tomar', 'Adultos', TRUE, 6300, 1100, 1),
  (10, 'Vitamina D3 2000 UI', 'Cimed', NULL, NULL, 'cápsula', 'SEM_TARJA', FALSE, '- Suplemento de vitamina D', 'Suplemento vitamínico', 'Via oral', 'Adultos', TRUE, 3900, 540, 1),
  (11, 'Protetor Solar Facial FPS 60', 'Neutrogena', NULL, NULL, 'creme', 'SEM_TARJA', FALSE, E'- Proteção contra raios UVA e UVB\n- Toque seco', 'Proteção solar facial', 'Aplicar sobre a pele limpa', 'Adultos', TRUE, 8800, 1210, 1),
  (12, 'Shampoo Anticaspa Clássico', 'Head & Shoulders', NULL, NULL, 'shampoo', 'SEM_TARJA', FALSE, '- Controle da caspa', 'Limpeza do couro cabeludo', 'Uso externo', 'Adultos', TRUE, 3300, 760, 1),
  (13, 'Shampoo Hidratação Pantene', 'Pantene', NULL, NULL, 'shampoo', 'SEM_TARJA', FALSE, '- Hidratação dos fios', 'Limpeza capilar', 'Uso externo', 'Adultos', TRUE, 2900, 690, 1),
  (14, 'Condicionador Hidratação Pantene', 'Pantene', NULL, NULL, 'condicionador', 'SEM_TARJA', FALSE, '- Hidratação e maciez dos fios', 'Condicionamento capilar', 'Uso externo', 'Adultos', TRUE, 2200, 480, 1),
  (15, 'Fralda Descartável Confort Sec', 'Pampers', NULL, NULL, 'fralda', 'SEM_TARJA', FALSE, '- Absorção por até 12 horas', 'Fralda descartável infantil', 'Uso externo', 'Bebês', TRUE, 7700, 1430, 1),
  (16, 'Lenços Umedecidos Sem Perfume', 'Huggies', NULL, NULL, 'lenço umedecido', 'SEM_TARJA', FALSE, '- Higiene delicada do bebê', 'Limpeza da pele do bebê', 'Uso externo', 'Bebês', TRUE, 4500, 920, 1),
  (17, 'Álcool em Gel 70%', 'Asseptgel', NULL, NULL, 'gel', 'SEM_TARJA', FALSE, '- Higienização das mãos', 'Antisséptico', 'Uso externo', 'Todos os públicos', TRUE, 5100, 1650, 1),
  (18, 'Termômetro Digital', 'G-Tech', NULL, NULL, 'aparelho', 'SEM_TARJA', FALSE, '- Medição de temperatura corporal', 'Aparelho de medição', 'Uso externo', 'Todos os públicos', TRUE, 6900, 0, 1),
  (19, 'Loratadina 10mg', 'EMS', 'loratadina', 'GENERICO', 'comprimido', 'SEM_TARJA', FALSE, '- Rinite alérgica', 'Anti-histamínico', 'Via oral', 'Adultos e crianças acima de 12 anos', FALSE, 9000, 1250, 1);


-- Todo produto em pelo menos 1 categoria
INSERT INTO produto_categoria (produto_id, categoria_id) VALUES
  (1, 11),
  (2, 11),
  (3, 11),
  (4, 11),
  (4, 12),
  (5, 13),
  (6, 14),
  (7, 15),
  (8, 16),
  (9, 21),
  (10, 21),
  (11, 33),
  (11, 32),
  (12, 31),
  (13, 31),
  (14, 31),
  (15, 41),
  (16, 42),
  (17, 22),
  (17, 51),
  (18, 23),
  (19, 13);

-- 5. APRESENTAÇÕES

INSERT INTO apresentacao (id, produto_id, descricao, sku, ean, preco, estoque, limite_por_pedido, ordem) VALUES
  (1, 1, '10 comprimidos', 'NOV-DIP1G-10', '7891000000014', 20.99, 0, 10, 1),
  (2, 1, '20 comprimidos', 'NOV-DIP1G-20', '7891000000021', 30.99, 48, 10, 0),
  (3, 1, '30 comprimidos', 'NOV-DIP1G-30', '7891000000038', 41.49, 25, 10, 2),
  (4, 2, '20 comprimidos', 'PRA-DIP500-20', '7891000000045', 12.90, 120, 10, 0),
  (5, 3, '10 comprimidos', 'NEQ-DIP500-10', '7891000000052', 6.90, 80, 10, 0),
  (6, 3, '20 comprimidos', 'NEQ-DIP500-20', '7891000000069', 10.50, 60, 10, 1),
  (7, 4, '20 comprimidos', 'EMS-PARA750-20', '7891000000076', 14.90, 90, 10, 0),
  (8, 5, '10 comprimidos', 'SAN-ALLE120-10', '7891000000083', 39.90, 35, 10, 0),
  (9, 6, '14 cápsulas', 'MED-OMEP20-14', '7891000000090', 11.90, 70, 10, 0),
  (10, 6, '28 cápsulas', 'MED-OMEP20-28', '7891000000106', 19.90, 40, 10, 1),
  (11, 7, '21 cápsulas', 'MED-AMOX500-21', '7891000000113', 29.90, 30, 2, 0),
  (12, 8, '30 comprimidos', 'EUR-CLON2-30', '7891000000120', 18.50, 20, 2, 0),
  (13, 9, '10 comprimidos efervescentes', 'CIM-VITC1G-10', '7891000000137', 14.90, 150, 10, 0),
  (14, 9, '30 comprimidos efervescentes', 'CIM-VITC1G-30', '7891000000144', 34.90, 60, 10, 1),
  (15, 10, '30 cápsulas', 'CIM-VITD2000-30', '7891000000151', 39.90, 45, 10, 0),
  (16, 10, '60 cápsulas', 'CIM-VITD2000-60', '7891000000168', 69.90, 18, 10, 1),
  (17, 11, '40 g', 'NEU-SOLFAC60-40', '7891000000175', 59.90, 55, 10, 0),
  (18, 12, '200 ml', 'HDS-SHAMPANT-200', '7891000000182', 17.90, 100, 10, 0),
  (19, 12, '400 ml', 'HDS-SHAMPANT-400', '7891000000199', 29.90, 85, 10, 1),
  (20, 13, '400 ml', 'PAN-SHAMHID-400', '7891000000205', 24.90, 90, 10, 0),
  (21, 14, '400 ml', 'PAN-CONDHID-400', '7891000000212', 24.90, 75, 10, 0),
  (22, 15, 'Tamanho M, 68 unidades', 'PAM-CONFSEC-M68', '7891000000229', 79.90, 40, 5, 0),
  (23, 15, 'Tamanho G, 60 unidades', 'PAM-CONFSEC-G60', '7891000000236', 79.90, 32, 5, 1),
  (24, 16, '48 unidades', 'HUG-LENCSP-48', '7891000000243', 12.90, 130, 10, 0),
  (25, 17, '500 g', 'ASP-ALCGEL70-500', '7891000000250', 14.90, 200, 10, 0),
  (26, 18, '1 unidade', 'GTE-TERMDIG-01', '7891000000267', 24.90, 0, 10, 0),
  (27, 19, '12 comprimidos', 'EMS-LORA10-12', '7891000000274', 7.90, 65, 10, 0);



-- 6. IMAGENS
INSERT INTO imagem_produto (id, produto_id, apresentacao_id, arquivo, ordem) VALUES
  (1, 1, NULL, '/images/produtos/placeholder.svg', 0),
  (2, 2, NULL, '/images/produtos/dipirona-monoidratada-500mg-20-comprimidos.webp', 0),
  (3, 3, NULL, '/images/produtos/placeholder.svg', 0),
  (4, 4, NULL, '/images/produtos/placeholder.svg', 0),
  (5, 5, NULL, '/images/produtos/placeholder.svg', 0),
  (6, 6, NULL, '/images/produtos/placeholder.svg', 0),
  (7, 7, NULL, '/images/produtos/placeholder.svg', 0),
  (8, 8, NULL, '/images/produtos/placeholder.svg', 0),
  (9, 9, NULL, '/images/produtos/placeholder.svg', 0),
  (10, 10, NULL, '/images/produtos/placeholder.svg', 0),
  (11, 11, NULL, '/images/produtos/placeholder.svg', 0),
  (12, 12, NULL, '/images/produtos/placeholder.svg', 0),
  (13, 13, NULL, '/images/produtos/placeholder.svg', 0),
  (14, 14, NULL, '/images/produtos/placeholder.svg', 0),
  (15, 15, NULL, '/images/produtos/placeholder.svg', 0),
  (16, 16, NULL, '/images/produtos/placeholder.svg', 0),
  (17, 17, NULL, '/images/produtos/placeholder.svg', 0),
  (18, 18, NULL, '/images/produtos/placeholder.svg', 0),
  (19, 19, NULL, '/images/produtos/placeholder.svg', 0);


-- 7. SEQUENCES (IDs fixos acima: avança cada IDENTITY para o MAX(id))
SELECT setval(pg_get_serial_sequence('usuario', 'id'), (SELECT COALESCE(MAX(id), 1) FROM usuario));
SELECT setval(pg_get_serial_sequence('categoria', 'id'), (SELECT COALESCE(MAX(id), 1) FROM categoria));
SELECT setval(pg_get_serial_sequence('loja', 'id'), (SELECT COALESCE(MAX(id), 1) FROM loja));
SELECT setval(pg_get_serial_sequence('produto', 'id'), (SELECT COALESCE(MAX(id), 1) FROM produto));
SELECT setval(pg_get_serial_sequence('apresentacao', 'id'), (SELECT COALESCE(MAX(id), 1) FROM apresentacao));
SELECT setval(pg_get_serial_sequence('imagem_produto', 'id'), (SELECT COALESCE(MAX(id), 1) FROM imagem_produto));
