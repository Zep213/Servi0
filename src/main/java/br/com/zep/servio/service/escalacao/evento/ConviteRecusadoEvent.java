package br.com.zep.servio.service.escalacao.evento;

/** A pessoa recusou o convite: o coordenador precisa escolher outra pessoa para a vaga. */
public record ConviteRecusadoEvent(Long alocacaoId) {}
