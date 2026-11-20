package io.github.daiaaaa.motorrisco.dominio.modelo;

/**
 * O que o analista de fraude decide sobre uma transação em revisão. Não existe "REVISAR":
 * a revisão manual é a última palavra.
 */
public enum DecisaoAnalista {
    APROVAR,
    NEGAR
}
