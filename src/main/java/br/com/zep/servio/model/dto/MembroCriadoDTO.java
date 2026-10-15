package br.com.zep.servio.model.dto;

/** Resposta do cadastro: só ids e o e-mail mascarado; nunca token nem senha. */
public record MembroCriadoDTO(Long usuarioId, Long usuarioPastoralId, String ativacaoEnviadaPara) {}
