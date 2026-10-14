package br.com.zep.servio.service.escalacao.evento;

/** Quem saiu (alocacaoSubstituidaId) e quem entrou (novaAlocacaoId) numa substituição. */
public record AlocacaoSubstituidaEvent(Long alocacaoSubstituidaId, Long novaAlocacaoId) {}
