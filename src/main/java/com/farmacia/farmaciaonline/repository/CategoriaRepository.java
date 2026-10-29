package com.farmacia.farmaciaonline.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.farmacia.farmaciaonline.model.Categoria;

@Repository
public interface CategoriaRepository extends JpaRepository<Categoria, Long> {

    /** Categorias raiz (sem pai) ativas, na ordem de exibição do menu. */
    List<Categoria> findByCategoriaPaiIsNullAndAtivaTrueOrderByOrdemAsc();

    /** Subcategorias ativas de uma categoria, na ordem de exibição. */
    List<Categoria> findByCategoriaPaiIdAndAtivaTrueOrderByOrdemAsc(Long categoriaPaiId);

    /** Subcategorias ativas das categorias raiz, ordenadas por categoria e ordem de exibição. */
    List<Categoria> findByCategoriaPaiIdInAndAtivaTrueOrderByCategoriaPaiIdAscOrdemAsc(
            Collection<Long> categoriaPaiIds);
}
