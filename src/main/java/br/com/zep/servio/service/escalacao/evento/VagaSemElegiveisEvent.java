package br.com.zep.servio.service.escalacao.evento;

/** O sorteio não conseguiu preencher a vaga: faltam estas pessoas e o coordenador precisa saber. */
public record VagaSemElegiveisEvent(Long vagaId, int faltam) {}
