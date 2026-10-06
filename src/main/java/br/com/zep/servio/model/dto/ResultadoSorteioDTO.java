package br.com.zep.servio.model.dto;

import java.util.List;

public record ResultadoSorteioDTO(
    List<AlocacaoResponseDTO> convidados,
    List<VagaIncompletaDTO> vagasIncompletas
) {}
