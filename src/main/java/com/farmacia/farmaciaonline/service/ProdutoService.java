package com.farmacia.farmaciaonline.service;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.farmacia.farmaciaonline.model.Apresentacao;
import com.farmacia.farmaciaonline.model.Categoria;
import com.farmacia.farmaciaonline.model.ImagemProduto;
import com.farmacia.farmaciaonline.model.Produto;
import com.farmacia.farmaciaonline.model.ProdutoForm;
import com.farmacia.farmaciaonline.model.ProdutoForm.ApresentacaoForm;
import com.farmacia.farmaciaonline.model.enums.Tarja;
import com.farmacia.farmaciaonline.repository.ApresentacaoRepository;
import com.farmacia.farmaciaonline.repository.CategoriaRepository;
import com.farmacia.farmaciaonline.repository.ImagemProdutoRepository;
import com.farmacia.farmaciaonline.repository.ItemPedidoRepository;
import com.farmacia.farmaciaonline.repository.ProdutoRepository;
import com.farmacia.farmaciaonline.repository.UsuarioRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class ProdutoService {

    private static final int LINHAS_EM_BRANCO = 2;
    private static final BigDecimal PRECO_MAXIMO = new BigDecimal("99999999.99");

    private final ProdutoRepository produtoRepository;
    private final ApresentacaoRepository apresentacaoRepository;
    private final CategoriaRepository categoriaRepository;
    private final UsuarioRepository usuarioRepository;
    private final ImagemProdutoRepository imagemRepository;
    private final ItemPedidoRepository itemPedidoRepository;
    private final ArmazenamentoImagem armazenamento;

    /** @param status "ativos", "inativos" ou qualquer outro valor para todos */
    @Transactional(readOnly = true)
    public Page<Produto> buscar(String q, String status, Pageable pageable) {
        String termo = "%" + (q == null ? "" : q.trim().toLowerCase()) + "%";
        List<Boolean> estados = switch (status == null ? "" : status) {
            case "ativos" -> List.of(true);
            case "inativos" -> List.of(false);
            default -> List.of(true, false);
        };
        return produtoRepository.buscarAdmin(termo, estados, pageable);
    }

    @Transactional(readOnly = true)
    public ProdutoForm formDe(Long id) {
        Produto p = buscarProduto(id);
        ProdutoForm f = new ProdutoForm();
        f.setNome(p.getNome());
        f.setMarca(p.getMarca());
        f.setPrincipioAtivo(p.getPrincipioAtivo());
        f.setTipo(p.getTipo());
        f.setFormaFarmaceutica(p.getFormaFarmaceutica());
        f.setTarja(p.getTarja());
        f.setExigeReceita(p.isExigeReceita());
        f.setRegistroMs(p.getRegistroMs());
        f.setBulaUrl(p.getBulaUrl());
        f.setIndicacao(p.getIndicacao());
        f.setAcao(p.getAcao());
        f.setViaAdministracao(p.getViaAdministracao());
        f.setPublicoAlvo(p.getPublicoAlvo());
        f.setAtivo(p.isAtivo());
        p.getCategorias().forEach(c -> f.getCategoriaIds().add(c.getId()));
        for (Apresentacao a : p.getApresentacoes()) {
            ApresentacaoForm af = new ApresentacaoForm();
            af.setId(a.getId());
            af.setVersao(a.getVersao());
            af.setDescricao(a.getDescricao());
            af.setSku(a.getSku());
            af.setEan(a.getEan());
            af.setPreco(a.getPreco());
            af.setEstoque(a.getEstoque());
            af.setLimitePorPedido(a.getLimitePorPedido());
            af.setAtiva(a.isAtiva());
            f.getApresentacoes().add(af);
        }
        return f;
    }

    @Transactional(readOnly = true)
    public List<ImagemProduto> imagensDe(Long produtoId) {
        return imagemRepository.findByProdutoIdOrderByOrdemAsc(produtoId);
    }

    /** Garante linhas em branco no fim do formulário para cadastrar novas apresentações. */
    public void prepararLinhas(ProdutoForm form) {
        List<ApresentacaoForm> linhas = form.getApresentacoes();
        while (!linhas.isEmpty() && linhas.get(linhas.size() - 1).isVazia()) {
            linhas.remove(linhas.size() - 1);
        }
        for (int i = 0; i < LINHAS_EM_BRANCO; i++) {
            linhas.add(new ApresentacaoForm());
        }
    }

    public void salvar(Long id, ProdutoForm form, Long adminId) {
        Produto produto = id == null ? new Produto() : buscarProduto(id);
        List<String> erros = new ArrayList<>();

        List<Categoria> categorias = validarCategorias(form, erros);
        if (form.getTarja() == null) {
            erros.add("Selecione a tarja.");
        } else if (form.getTarja() != Tarja.SEM_TARJA && !form.isExigeReceita()) {
            erros.add("Produtos com tarja vermelha ou preta devem exigir receita.");
        }
        List<ApresentacaoForm> linhas = form.getApresentacoes().stream().filter(l -> !l.isVazia()).toList();
        List<Apresentacao> destinos = validarApresentacoes(produto, linhas, erros);
        List<MultipartFile> novas = form.getNovasImagens() == null ? List.of()
                : form.getNovasImagens().stream().filter(f -> !f.isEmpty()).toList();
        List<ImagemProduto> remover = new ArrayList<>();
        Apresentacao vinculo = validarImagens(produto, form, novas, remover, erros);
        if (!erros.isEmpty()) {
            throw new ProdutoException(erros);
        }

        produto.setNome(form.getNome().trim());
        produto.setMarca(form.getMarca().trim());
        produto.setPrincipioAtivo(texto(form.getPrincipioAtivo()));
        produto.setTipo(form.getTipo());
        produto.setFormaFarmaceutica(texto(form.getFormaFarmaceutica()));
        produto.setTarja(form.getTarja());
        produto.setExigeReceita(form.isExigeReceita());
        produto.setRegistroMs(texto(form.getRegistroMs()));
        produto.setBulaUrl(texto(form.getBulaUrl()));
        produto.setIndicacao(texto(form.getIndicacao()));
        produto.setAcao(texto(form.getAcao()));
        produto.setViaAdministracao(texto(form.getViaAdministracao()));
        produto.setPublicoAlvo(texto(form.getPublicoAlvo()));
        produto.setAtivo(form.isAtivo());
        produto.getCategorias().clear();
        produto.getCategorias().addAll(categorias);
        produto.setAtualizadoEm(Instant.now());
        produto.setAtualizadoPor(adminId == null ? null : usuarioRepository.getReferenceById(adminId));

        List<String> escritos = new ArrayList<>();
        try {
            produtoRepository.save(produto);
            for (int i = 0; i < linhas.size(); i++) {
                ApresentacaoForm l = linhas.get(i);
                Apresentacao a = destinos.get(i);
                a.setProduto(produto);
                a.setDescricao(l.getDescricao().trim());
                a.setSku(l.getSku().trim());
                a.setEan(l.getEan().trim());
                a.setPreco(l.getPreco().setScale(2, java.math.RoundingMode.HALF_UP));
                a.setEstoque(l.getEstoque());
                a.setLimitePorPedido(l.getLimitePorPedido());
                a.setAtiva(l.isAtiva());
                a.setOrdem(i);
                apresentacaoRepository.save(a);
            }
            apresentacaoRepository.flush();
            gravarImagens(produto, novas, vinculo, remover, escritos);
        } catch (IOException e) {
            escritos.forEach(armazenamento::excluir);
            throw new ProdutoException("Não foi possível gravar as imagens. Tente novamente.");
        } catch (OptimisticLockingFailureException e) {
            escritos.forEach(armazenamento::excluir);
            throw new ProdutoException("O estoque foi alterado por outra operação (ex.: uma compra). Recarregue e tente de novo.");
        } catch (DataIntegrityViolationException e) {
            escritos.forEach(armazenamento::excluir);
            throw new ProdutoException("Dados inválidos ou SKU/EAN já cadastrado.");
        } catch (RuntimeException e) {
            escritos.forEach(armazenamento::excluir);
            throw e;
        }
    }

    /**
     * Exclusão (UC12): produto com pedido só é inativado; sem pedido, é apagado de vez
     * (imagens e apresentações primeiro, porque o mapeamento não tem cascade).
     *
     * @return {@code true} se apagou, {@code false} se apenas inativou
     */
    public boolean excluir(Long id, Long adminId) {
        Produto produto = buscarProduto(id);
        if (itemPedidoRepository.existsByApresentacaoProdutoId(id)) {
            produto.setAtivo(false);
            produto.setAtualizadoEm(Instant.now());
            produto.setAtualizadoPor(adminId == null ? null : usuarioRepository.getReferenceById(adminId));
            produtoRepository.flush();
            return false;
        }
        List<ImagemProduto> imagens = imagemRepository.findByProdutoIdOrderByOrdemAsc(id);
        try {
            imagemRepository.deleteAll(imagens);
            apresentacaoRepository.deleteAll(apresentacaoRepository.findByProdutoId(id));
            apresentacaoRepository.flush();
            produtoRepository.delete(produto);
            produtoRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new ProdutoException("Não foi possível excluir: o produto está vinculado a outros registros (por exemplo, um pedido feito agora). Inative-o.");
        }
        apagarArquivosAposCommit(imagens);
        return true;
    }

    private Apresentacao validarImagens(Produto produto, ProdutoForm form, List<MultipartFile> novas,
            List<ImagemProduto> remover, List<String> erros) {
        for (Long imagemId : form.getRemoverImagemIds() == null ? List.<Long>of() : form.getRemoverImagemIds()) {
            ImagemProduto img = imagemRepository.findById(imagemId)
                    .filter(i -> produto.getId() != null && i.getProduto().getId().equals(produto.getId()))
                    .orElse(null);
            if (img == null) {
                erros.add("Imagem a remover não pertence a este produto.");
            } else {
                remover.add(img);
            }
        }
        for (MultipartFile f : novas) {
            String erro = armazenamento.validar(f);
            if (erro != null) {
                erros.add(erro);
            }
        }
        Long apId = form.getImagemApresentacaoId();
        if (apId == null || novas.isEmpty()) {
            return null;
        }
        Apresentacao ap = apresentacaoRepository.findById(apId)
                .filter(a -> produto.getId() != null && a.getProduto().getId().equals(produto.getId()))
                .orElse(null);
        if (ap == null) {
            erros.add("A apresentação escolhida para a imagem não pertence a este produto.");
        }
        return ap;
    }

    private void gravarImagens(Produto produto, List<MultipartFile> novas, Apresentacao vinculo,
            List<ImagemProduto> remover, List<String> escritos) throws IOException {
        imagemRepository.deleteAll(remover);
        int ordem = imagemRepository.findByProdutoIdOrderByOrdemAsc(produto.getId()).stream()
                .filter(i -> !remover.contains(i)).mapToInt(ImagemProduto::getOrdem).max().orElse(-1) + 1;
        for (MultipartFile f : novas) {
            String url = armazenamento.salvar(f);
            escritos.add(url);
            ImagemProduto img = new ImagemProduto();
            img.setProduto(produto);
            img.setApresentacao(vinculo);
            img.setArquivo(url);
            img.setOrdem(ordem++);
            imagemRepository.save(img);
        }
        imagemRepository.flush();
        apagarArquivosAposCommit(remover);
    }

    private void apagarArquivosAposCommit(List<ImagemProduto> imagens) {
        List<String> urls = imagens.stream().map(ImagemProduto::getArquivo).toList();
        if (urls.isEmpty()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                urls.forEach(armazenamento::excluir);
            }
        });
    }

    private List<Categoria> validarCategorias(ProdutoForm form, List<String> erros) {
        List<Categoria> categorias = categoriaRepository.findAllById(
                form.getCategoriaIds() == null ? List.of() : form.getCategoriaIds());
        if (categorias.isEmpty()) {
            erros.add("Selecione ao menos uma categoria ativa.");
        }
        for (Categoria c : categorias) {
            if (!c.isAtiva()) {
                erros.add("A categoria \"" + c.getNome() + "\" está inativa.");
            }
        }
        return categorias;
    }

    /** Valida as linhas e devolve, na mesma ordem, a entidade de cada uma (existente ou nova). */
    private List<Apresentacao> validarApresentacoes(Produto produto, List<ApresentacaoForm> linhas,
            List<String> erros) {
        if (linhas.isEmpty()) {
            erros.add("Informe ao menos uma apresentação.");
        }
        List<Apresentacao> destinos = new ArrayList<>();
        Set<String> skus = new HashSet<>();
        Set<String> eans = new HashSet<>();
        for (int i = 0; i < linhas.size(); i++) {
            ApresentacaoForm l = linhas.get(i);
            String r = "Apresentação " + (i + 1) + ": ";

            Apresentacao existente = null;
            if (l.getId() != null) {
                existente = apresentacaoRepository.findById(l.getId())
                        .filter(a -> produto.getId() != null && a.getProduto().getId().equals(produto.getId()))
                        .orElse(null);
                if (existente == null) {
                    erros.add(r + "não pertence a este produto.");
                } else if (l.getVersao() == null || !l.getVersao().equals(existente.getVersao())) {
                    erros.add(r + "foi alterada por outra operação (ex.: uma compra). Recarregue o formulário.");
                }
            }
            destinos.add(existente != null ? existente : new Apresentacao());

            if (vazio(l.getDescricao()) || l.getDescricao().trim().length() > 60) {
                erros.add(r + "informe a descrição (até 60 caracteres).");
            }
            String sku = l.getSku() == null ? "" : l.getSku().trim();
            if (sku.isEmpty() || sku.length() > 40) {
                erros.add(r + "informe o SKU (até 40 caracteres).");
            } else if (!skus.add(sku)) {
                erros.add(r + "SKU repetido no formulário.");
            } else if (apresentacaoRepository.findBySku(sku).filter(a -> !a.getId().equals(l.getId())).isPresent()) {
                erros.add(r + "SKU \"" + sku + "\" já cadastrado.");
            }
            String ean = l.getEan() == null ? "" : l.getEan().trim();
            if (!ean.matches("\\d{8,14}")) {
                erros.add(r + "o EAN deve ter de 8 a 14 dígitos.");
            } else if (!eans.add(ean)) {
                erros.add(r + "EAN repetido no formulário.");
            } else if (apresentacaoRepository.findByEan(ean).filter(a -> !a.getId().equals(l.getId())).isPresent()) {
                erros.add(r + "EAN \"" + ean + "\" já cadastrado.");
            }
            if (l.getPreco() == null || l.getPreco().signum() <= 0 || l.getPreco().compareTo(PRECO_MAXIMO) > 0) {
                erros.add(r + "o preço deve ser maior que zero.");
            }
            if (l.getEstoque() == null || l.getEstoque() < 0) {
                erros.add(r + "o estoque não pode ser negativo.");
            }
            if (l.getLimitePorPedido() == null || l.getLimitePorPedido() <= 0) {
                erros.add(r + "o limite por pedido deve ser maior que zero.");
            }
        }
        return destinos;
    }

    private Produto buscarProduto(Long id) {
        return produtoRepository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private static boolean vazio(String s) {
        return s == null || s.isBlank();
    }

    private static String texto(String s) {
        return vazio(s) ? null : s.trim();
    }
}
