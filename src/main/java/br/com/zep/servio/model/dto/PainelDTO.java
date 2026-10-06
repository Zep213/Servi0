package br.com.zep.servio.model.dto;

import br.com.zep.servio.model.enumerated.TipoCelebracao;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Painel do coordenador de uma pastoral (Etapa 6, Parte 6). Cards e celebrações são do mês pedido;
 * pendências são do que ainda está por vir, sem recorte de mês.
 */
public record PainelDTO(Cards cards, List<CelebracaoPainelDTO> celebracoes, Pendencias pendencias) {

    public record Cards(long vagasTotais, long vagasOcupadas, long convitesPendentes, long alteracoesPendentes) {}

    /**
     * NAO_INICIADO: ninguém ocupa nenhuma vaga ainda. COMPLETO: todas as vagas com quantidade
     * estão confirmadas (ACEITA). PENDENTE: o resto (há convite sem resposta ou vaga aberta).
     */
    public enum StatusPainel { NAO_INICIADO, COMPLETO, PENDENTE }

    public record CelebracaoPainelDTO(Long id, LocalDate data, LocalTime hora, String titulo, TipoCelebracao tipo,
                                      long vagasTotais, long ocupadas, long confirmadas, StatusPainel status,
                                      boolean aguardandoQuantidade) {}

    public record Pendencias(List<ConvitePrazoDTO> convitesVencendo,
                             List<RecusaSemSubstitutoDTO> recusasSemSubstituto,
                             List<AlteracaoAguardandoDTO> alteracoesAguardando) {}

    public record ConvitePrazoDTO(Long alocacaoId, String nome, Long celebracaoId, String titulo, LocalDate data,
                                  LocalTime hora, String funcao, LocalDateTime prazo) {}

    public record RecusaSemSubstitutoDTO(Long alocacaoId, String nome, String status, Long celebracaoId,
                                         String titulo, LocalDate data, String funcao, long faltam) {}

    public record AlteracaoAguardandoDTO(Long id, Long alocacaoId, String funcao, String usuarioAnterior,
                                         String usuarioNovo) {}
}
