package br.com.zep.servio.service.escalacao;

import br.com.zep.servio.exception.RecursoNaoEncontradoException;
import br.com.zep.servio.model.Alocacao;
import br.com.zep.servio.model.AlteracaoPendente;
import br.com.zep.servio.model.Celebracao;
import br.com.zep.servio.model.Usuario;
import br.com.zep.servio.model.Vaga;
import br.com.zep.servio.model.dto.PainelDTO;
import br.com.zep.servio.model.dto.PainelDTO.AlteracaoAguardandoDTO;
import br.com.zep.servio.model.dto.PainelDTO.CelebracaoPainelDTO;
import br.com.zep.servio.model.dto.PainelDTO.ConvitePrazoDTO;
import br.com.zep.servio.model.dto.PainelDTO.Pendencias;
import br.com.zep.servio.model.dto.PainelDTO.RecusaSemSubstitutoDTO;
import br.com.zep.servio.model.dto.PainelDTO.StatusPainel;
import br.com.zep.servio.model.enumerated.StatusAlteracaoPendente;
import br.com.zep.servio.model.enumerated.StatusConvite;
import br.com.zep.servio.repository.AlocacaoRepository;
import br.com.zep.servio.repository.AlteracaoPendenteRepository;
import br.com.zep.servio.repository.PastoralRepository;
import br.com.zep.servio.repository.VagaRepository;
import br.com.zep.servio.security.PastoraisPermissao;
import br.com.zep.servio.security.UsuarioLogado;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Painel do coordenador (Etapa 6, Parte 6). Consultas agrupadas por celebração e por status,
 * juntadas aqui em Java. Só leitura: nada muda o estado da escala.
 */
@Service
@RequiredArgsConstructor
public class PainelService {

    private static final List<String> PAPEIS_QUE_VEEM = List.of("COORDENADOR", "VICE", "SECRETARIO", "TESOUREIRO");

    private final VagaRepository vagaRepository;
    private final AlocacaoRepository alocacaoRepository;
    private final AlteracaoPendenteRepository alteracaoPendenteRepository;
    private final PastoralRepository pastoralRepository;
    private final ConfiguracaoPastoralService configuracao;
    private final PastoraisPermissao pastoraisPermissao;
    private final UsuarioLogado usuarioLogado;
    private final Clock clock;

    @Transactional(readOnly = true)
    public PainelDTO painel(Long pastoralId, YearMonth mes) {
        Long paroquiaId = usuarioLogado.paroquiaId();
        // Fora do alcance ou de outra paróquia: 404 (como se não existisse). PADRE/ADMIN passam pela
        // visibilidade sem restrição, por isso a paróquia é conferida aqui, antes de qualquer consulta.
        if (pastoralRepository.findByIdAndParoquiaIdAndActiveTrue(pastoralId, paroquiaId).isEmpty()
                || !pastoraisPermissao.visivel(pastoralId)) {
            throw new RecursoNaoEncontradoException("Pastoral", pastoralId);
        }
        // Dentro, mas só membro comum: 403.
        if (!pastoraisPermissao.temQualquerPapel(pastoralId, PAPEIS_QUE_VEEM.toArray(String[]::new))) {
            throw new AccessDeniedException("Só coordenação, vice, secretaria e tesouraria veem o painel");
        }
        LocalDateTime agora = LocalDateTime.now(clock);
        LocalDate hoje = agora.toLocalDate();
        LocalDate inicio = mes.atDay(1);
        LocalDate fim = mes.atEndOfMonth();

        Map<Long, long[]> totais = new HashMap<>();
        for (Object[] linha : vagaRepository.totaisPorCelebracao(paroquiaId, pastoralId, inicio, fim)) {
            totais.put(((Number) linha[0]).longValue(),
                    new long[]{((Number) linha[1]).longValue(), ((Number) linha[2]).longValue()});
        }
        Map<Long, Map<StatusConvite, Long>> contagens = new HashMap<>();
        for (Object[] linha : alocacaoRepository.contagensPorCelebracaoEStatus(paroquiaId, pastoralId, inicio, fim)) {
            contagens.computeIfAbsent(((Number) linha[0]).longValue(), k -> new EnumMap<>(StatusConvite.class))
                    .put((StatusConvite) linha[1], ((Number) linha[2]).longValue());
        }

        List<CelebracaoPainelDTO> celebracoes = new ArrayList<>();
        long vagasTotais = 0, vagasOcupadas = 0, convitesPendentes = 0;
        for (Celebracao celebracao : vagaRepository.celebracoesDaPastoral(paroquiaId, pastoralId, inicio, fim)) {
            long[] t = totais.getOrDefault(celebracao.getId(), new long[]{0, 0});
            Map<StatusConvite, Long> c = contagens.getOrDefault(celebracao.getId(), Map.of());
            long pendentes = c.getOrDefault(StatusConvite.PENDENTE, 0L);
            long confirmadas = c.getOrDefault(StatusConvite.ACEITA, 0L);
            long ocupadas = pendentes + confirmadas;
            boolean aguardando = t[1] > 0;
            celebracoes.add(new CelebracaoPainelDTO(celebracao.getId(), celebracao.getData(), celebracao.getHora(),
                    celebracao.getTitulo(), celebracao.getTipo(), t[0], ocupadas, confirmadas,
                    statusDaCelebracao(ocupadas, confirmadas, t[0], aguardando), aguardando));
            vagasTotais += t[0];
            vagasOcupadas += ocupadas;
            convitesPendentes += pendentes;
        }

        List<AlteracaoAguardandoDTO> alteracoes = alteracoesAguardando(paroquiaId, pastoralId);
        PainelDTO.Cards cards = new PainelDTO.Cards(vagasTotais, vagasOcupadas, convitesPendentes, alteracoes.size());
        Pendencias pendencias = new Pendencias(
                convitesVencendo(paroquiaId, pastoralId, agora),
                recusasSemSubstituto(paroquiaId, pastoralId, hoje),
                alteracoes);
        return new PainelDTO(cards, celebracoes, pendencias);
    }

    /**
     * NAO_INICIADO: ninguém ocupa vaga ainda. COMPLETO: todas as vagas com quantidade estão
     * confirmadas e nenhuma vaga está sem quantidade. Convite sem resposta nunca conta como completo.
     */
    static StatusPainel statusDaCelebracao(long ocupadas, long confirmadas, long vagasTotais, boolean aguardandoQuantidade) {
        if (ocupadas == 0) {
            return StatusPainel.NAO_INICIADO;
        }
        if (!aguardandoQuantidade && vagasTotais > 0 && confirmadas == vagasTotais) {
            return StatusPainel.COMPLETO;
        }
        return StatusPainel.PENDENTE;
    }

    /** Convites pendentes que vencem dentro da janela de lembrete da pastoral (padrão: 6h). */
    private List<ConvitePrazoDTO> convitesVencendo(Long paroquiaId, Long pastoralId, LocalDateTime agora) {
        long horas = configuracao.lembreteRespostaHorasAntesDoPrazo(pastoralId);
        LocalDateTime limite = agora.plusHours(horas);
        List<ConvitePrazoDTO> lista = new ArrayList<>();
        for (Alocacao a : alocacaoRepository.pendentesComPrazoPorVir(paroquiaId, pastoralId, StatusConvite.PENDENTE, agora)) {
            if (a.getDataLimiteResposta().isAfter(limite)) {
                continue;
            }
            Celebracao c = a.getVaga().getCelebracao();
            lista.add(new ConvitePrazoDTO(a.getId(), nome(a.getUsuario()), c.getId(), c.getTitulo(), c.getData(),
                    c.getHora(), a.getVaga().getFuncao().getNome(), a.getDataLimiteResposta()));
        }
        return lista;
    }

    /**
     * Recusa ou expiração de uma vaga que ainda está abaixo da quantidade (sem substituto).
     * Vaga sem quantidade nunca entra aqui: não há o que comparar.
     */
    private List<RecusaSemSubstitutoDTO> recusasSemSubstituto(Long paroquiaId, Long pastoralId, LocalDate hoje) {
        Map<Long, Long> ocupantes = new HashMap<>();
        for (Object[] linha : alocacaoRepository.ocupantesPorVagaAPartirDe(paroquiaId, pastoralId, StatusConvite.OCUPANTES, hoje)) {
            ocupantes.put(((Number) linha[0]).longValue(), ((Number) linha[1]).longValue());
        }
        List<RecusaSemSubstitutoDTO> lista = new ArrayList<>();
        for (Alocacao a : alocacaoRepository.recusadasOuExpiradasAPartirDe(paroquiaId, pastoralId,
                List.of(StatusConvite.RECUSADA, StatusConvite.EXPIRADA), hoje)) {
            Vaga vaga = a.getVaga();
            if (vaga.getQuantidade() == null) {
                continue;
            }
            long faltam = vaga.getQuantidade() - ocupantes.getOrDefault(vaga.getId(), 0L);
            if (faltam <= 0) {
                continue;
            }
            Celebracao c = vaga.getCelebracao();
            lista.add(new RecusaSemSubstitutoDTO(a.getId(), nome(a.getUsuario()), a.getStatus().name(), c.getId(),
                    c.getTitulo(), c.getData(), vaga.getFuncao().getNome(), faltam));
        }
        return lista;
    }

    private List<AlteracaoAguardandoDTO> alteracoesAguardando(Long paroquiaId, Long pastoralId) {
        List<AlteracaoAguardandoDTO> lista = new ArrayList<>();
        for (AlteracaoPendente p : alteracaoPendenteRepository.aguardandoComDetalhes(
                paroquiaId, pastoralId, StatusAlteracaoPendente.PENDENTE)) {
            lista.add(new AlteracaoAguardandoDTO(p.getId(),
                    p.getAlocacao() == null ? null : p.getAlocacao().getId(),
                    p.getVagaNova() == null ? "" : p.getVagaNova().getFuncao().getNome(),
                    p.getUsuarioAnterior() == null ? "" : nome(p.getUsuarioAnterior()),
                    p.getUsuarioNovo() == null ? "" : nome(p.getUsuarioNovo())));
        }
        return lista;
    }

    private static String nome(Usuario usuario) {
        return usuario.getNome();
    }
}
