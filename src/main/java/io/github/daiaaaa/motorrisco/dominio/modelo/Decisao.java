package io.github.daiaaaa.motorrisco.dominio.modelo;

public enum Decisao {
    /** Risco baixo: a transação pode seguir. */
    APROVAR,
    /** Risco médio: um analista precisa revisar (fluxo Camunda na fase 6). */
    REVISAR,
    /** Risco alto ou regra bloqueante: a transação é recusada. */
    NEGAR
}
