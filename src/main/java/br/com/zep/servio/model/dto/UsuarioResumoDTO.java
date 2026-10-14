package br.com.zep.servio.model.dto;

/** Para achar quem adicionar a uma pastoral: nenhum dado sensível (perfil, etc). */
public record UsuarioResumoDTO(Long id, String nome, String email) {}
