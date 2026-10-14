package br.com.zep.servio.model.dto;

import br.com.zep.servio.model.enumerated.OrigemAlocacao;
import br.com.zep.servio.model.enumerated.PapelPastoral;
import br.com.zep.servio.model.enumerated.StatusConvite;
import br.com.zep.servio.model.enumerated.TipoCelebracao;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * Respostas de leitura para as telas (Etapa 7, Parte 1). Os nomes já vêm resolvidos, para o front
 * não precisar de uma chamada por item.
 */
public final class EscalaDTOs {

    private EscalaDTOs() {
    }

    /** Par id + nome, usado para função, pastoral e pessoa. */
    public record Ref(Long id, String nome) {}

    public record CelebracaoRef(Long id, String titulo, TipoCelebracao tipo, LocalDate data, LocalTime hora) {}

    /** Uma escala do usuário logado (GET /api/me/escalas). */
    public record EscalaPessoalDTO(Long alocacaoId, StatusConvite status, LocalDateTime dataLimiteResposta,
                                   OrigemAlocacao origem, LocalTime horarioChegada, String observacao,
                                   CelebracaoRef celebracao, Ref funcao, Ref pastoral) {}

    /** Uma pessoa numa vaga (dentro de EscalaCelebracaoDTO). */
    public record AlocacaoEscalaDTO(Long id, Ref usuario, StatusConvite status, OrigemAlocacao origem,
                                    LocalDateTime dataLimiteResposta, String justificativa) {}

    public record VagaEscalaDTO(Long vagaId, Ref funcao, Integer quantidade, LocalTime horarioChegada,
                                String observacao, List<AlocacaoEscalaDTO> alocacoes) {}

    /** Escala de uma pastoral numa celebração (GET /api/pastorais/{id}/celebracoes/{cid}/escala). */
    public record EscalaCelebracaoDTO(Long celebracaoId, List<VagaEscalaDTO> vagas) {}

    /** Membro de uma pastoral. O e-mail só vem para COORDENADOR, PADRE e ADMIN (null para os demais). */
    public record MembroPastoralDTO(Long usuarioPastoralId, Ref usuario, PapelPastoral papel, String email) {}
}
