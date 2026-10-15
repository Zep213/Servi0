package br.com.zep.servio.model.dto;

/** O que a tela "Ativar conta" mostra antes de pedir a senha: só o primeiro nome e o e-mail mascarado. */
public record AtivacaoDetalhesDTO(String primeiroNome, String email) {}
