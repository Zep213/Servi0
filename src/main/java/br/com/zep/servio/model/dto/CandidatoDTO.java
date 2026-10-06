package br.com.zep.servio.model.dto;

import java.time.LocalDate;
import java.util.List;

/** Candidato à vaga: se está impedido, com o motivo de cada impedimento. */
public record CandidatoDTO(
    ReferenciaDTO usuario,
    boolean elegivel,
    List<String> motivos,
    LocalDate ultimaVezQueServiu
) {}
