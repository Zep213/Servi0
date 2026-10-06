package br.com.zep.servio.service.escalacao.evento;

/** O prazo do convite venceu sem resposta: o coordenador precisa escolher outra pessoa para a vaga. */
public record ConviteExpiradoEvent(Long alocacaoId) {}
