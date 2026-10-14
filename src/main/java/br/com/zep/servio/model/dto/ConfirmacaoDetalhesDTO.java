package br.com.zep.servio.model.dto;

import br.com.zep.servio.model.enumerated.StatusConvite;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/** O que a página do convite mostra. Só leitura: montar esta resposta não altera nada. */
public record ConfirmacaoDetalhesDTO(
    String primeiroNome,
    String celebracaoTitulo,
    LocalDate data,
    LocalTime hora,
    LocalTime horarioChegada,
    String funcao,
    String observacao,
    LocalDateTime prazo,
    StatusConvite status
) {}
