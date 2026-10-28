package com.farmacia.farmaciaonline.model;

import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Cartão salvo de um {@link Usuario}. Guarda só o token devolvido pelo gateway de pagamento e
 * os dados mascarados para exibição (bandeira, 4 últimos dígitos, validade e nome impresso).
 * <p>
 * Nunca grave o número completo nem o CVV (nem em log). A LGPD pede apenas os dados
 * necessários (art. 6º, III), e o PCI DSS proíbe guardar o código de segurança depois da
 * autorização. O token só funciona no gateway, então um vazamento do banco não expõe o cartão.
 * Sem {@code @ToString}: a relação {@code usuario} dispararia lazy loading.
 */
@Entity
@Table(name = "cartao_salvo")
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(of = "id")
public class CartaoSalvo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    /** Token do gateway que representa o cartão. */
    @Column(nullable = false, length = 100)
    private String tokenGateway;

    @Column(nullable = false, length = 20)
    private String bandeira;

    /** Últimos 4 dígitos, só números. */
    @Column(nullable = false, length = 4)
    private String ultimos4;

    /** De 1 a 12. */
    @Column(nullable = false)
    private int validadeMes;

    @Column(nullable = false)
    private int validadeAno;

    @Column(nullable = false, length = 100)
    private String nomeImpresso;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant criadoEm;
}
