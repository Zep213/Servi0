package br.com.zep.servio.model.dto;

public record ParoquiaPlataformaDTO(
    Long id,
    String nome,
    long usuarios,
    long pastorais
) {}
