package br.com.zep.servio.model.dto;

public record VagaIncompletaDTO(Long vagaId, ReferenciaDTO funcao, int faltam) {}
