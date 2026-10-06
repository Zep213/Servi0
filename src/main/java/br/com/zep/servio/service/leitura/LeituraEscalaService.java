package br.com.zep.servio.service.leitura;

import br.com.zep.servio.exception.RecursoNaoEncontradoException;
import br.com.zep.servio.exception.RegraNegocioException;
import br.com.zep.servio.model.Alocacao;
import br.com.zep.servio.model.Celebracao;
import br.com.zep.servio.model.UsuarioPastoral;
import br.com.zep.servio.model.Vaga;
import br.com.zep.servio.model.dto.EscalaDTOs.AlocacaoEscalaDTO;
import br.com.zep.servio.model.dto.EscalaDTOs.CelebracaoRef;
import br.com.zep.servio.model.dto.EscalaDTOs.EscalaCelebracaoDTO;
import br.com.zep.servio.model.dto.EscalaDTOs.EscalaPessoalDTO;
import br.com.zep.servio.model.dto.EscalaDTOs.MembroPastoralDTO;
import br.com.zep.servio.model.dto.EscalaDTOs.Ref;
import br.com.zep.servio.model.dto.EscalaDTOs.VagaEscalaDTO;
import br.com.zep.servio.model.enumerated.StatusConvite;
import br.com.zep.servio.repository.AlocacaoRepository;
import br.com.zep.servio.repository.CelebracaoRepository;
import br.com.zep.servio.repository.PastoralRepository;
import br.com.zep.servio.repository.UsuarioPastoralRepository;
import br.com.zep.servio.repository.VagaRepository;
import br.com.zep.servio.security.PastoraisPermissao;
import br.com.zep.servio.security.UsuarioLogado;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Leituras para as telas de escala, membros e "minhas escalas" (Etapa 7, Parte 1). Só consultas:
 * as regras de quem pode ver ficam aqui, não no front.
 */
@Service
@RequiredArgsConstructor
public class LeituraEscalaService {

    private static final int DIAS_PADRAO_ESCALAS = 60;
    private static final long MAXIMO_DIAS_PERIODO = 366;
    private static final List<String> PAPEIS_DA_ESCALA = List.of("COORDENADOR", "VICE", "SECRETARIO", "TESOUREIRO");

    private final AlocacaoRepository alocacaoRepository;
    private final VagaRepository vagaRepository;
    private final CelebracaoRepository celebracaoRepository;
    private final PastoralRepository pastoralRepository;
    private final UsuarioPastoralRepository usuarioPastoralRepository;
    private final PastoraisPermissao pastoraisPermissao;
    private final UsuarioLogado usuarioLogado;
    private final Clock clock;

    /** Escalas do usuário logado entre {@code de} e {@code ate} (padrão: hoje até +60 dias). */
    @Transactional(readOnly = true)
    public List<EscalaPessoalDTO> minhasEscalas(LocalDate de, LocalDate ate) {
        LocalDate inicio = de == null ? LocalDate.now(clock) : de;
        LocalDate fim = ate == null ? inicio.plusDays(DIAS_PADRAO_ESCALAS) : ate;
        if (fim.isBefore(inicio)) {
            throw new RegraNegocioException("A data final não pode ser antes da inicial");
        }
        if (ChronoUnit.DAYS.between(inicio, fim) > MAXIMO_DIAS_PERIODO) {
            throw new RegraNegocioException("O período pedido é longo demais (no máximo um ano)");
        }
        return alocacaoRepository.escalasDoUsuario(usuarioLogado.paroquiaId(), usuarioLogado.id(), inicio, fim,
                        StatusConvite.SUBSTITUIDA)
                .stream().map(this::escalaPessoal).toList();
    }

    /** Vagas da pastoral numa celebração, com quem está escalado. Mesma permissão do painel. */
    @Transactional(readOnly = true)
    public EscalaCelebracaoDTO escalaDaCelebracao(Long pastoralId, Long celebracaoId) {
        Long paroquiaId = usuarioLogado.paroquiaId();
        exigirPastoralVisivel(pastoralId, paroquiaId);
        if (!pastoraisPermissao.temQualquerPapel(pastoralId, PAPEIS_DA_ESCALA.toArray(String[]::new))) {
            throw new AccessDeniedException("Só coordenação, vice, secretaria e tesouraria veem a escala");
        }
        Celebracao celebracao = celebracaoRepository.findByIdAndParoquiaIdAndActiveTrue(celebracaoId, paroquiaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Celebracao", celebracaoId));

        List<Vaga> vagas = vagaRepository.vagasDaPastoralNaCelebracao(paroquiaId, pastoralId, celebracao.getId());
        List<Long> vagaIds = vagas.stream().map(Vaga::getId).toList();
        Map<Long, List<AlocacaoEscalaDTO>> porVaga = new LinkedHashMap<>();
        if (!vagaIds.isEmpty()) {
            for (Alocacao a : alocacaoRepository.alocacoesDasVagas(paroquiaId, vagaIds, StatusConvite.SUBSTITUIDA)) {
                porVaga.computeIfAbsent(a.getVaga().getId(), k -> new ArrayList<>()).add(new AlocacaoEscalaDTO(
                        a.getId(), new Ref(a.getUsuario().getId(), a.getUsuario().getNome()), a.getStatus(),
                        a.getOrigem(), a.getDataLimiteResposta(), a.getJustificativa()));
            }
        }
        List<VagaEscalaDTO> resposta = vagas.stream().map(v -> new VagaEscalaDTO(
                v.getId(),
                new Ref(v.getFuncao().getId(), v.getFuncao().getNome()),
                v.getQuantidade(),
                v.getHorarioChegada(),
                v.getObservacao(),
                porVaga.getOrDefault(v.getId(), List.of()))).toList();
        return new EscalaCelebracaoDTO(celebracao.getId(), resposta);
    }

    /**
     * Membros ativos da pastoral. O e-mail só sai para quem coordena (ou PADRE/ADMIN, que valem
     * como coordenador): os demais recebem o nome e o papel, sem contato.
     */
    @Transactional(readOnly = true)
    public List<MembroPastoralDTO> membros(Long pastoralId) {
        Long paroquiaId = usuarioLogado.paroquiaId();
        exigirPastoralVisivel(pastoralId, paroquiaId);
        boolean veEmail = pastoraisPermissao.temPapel(pastoralId, "COORDENADOR");
        List<MembroPastoralDTO> lista = new ArrayList<>();
        for (UsuarioPastoral up : usuarioPastoralRepository.membrosDaPastoral(paroquiaId, pastoralId)) {
            lista.add(new MembroPastoralDTO(up.getId(),
                    new Ref(up.getUsuario().getId(), up.getUsuario().getNome()),
                    up.getPapel(),
                    veEmail ? up.getUsuario().getEmail() : null));
        }
        return lista;
    }

    /** Fora da paróquia ou fora do alcance: 404, como se a pastoral não existisse. */
    private void exigirPastoralVisivel(Long pastoralId, Long paroquiaId) {
        if (pastoralRepository.findByIdAndParoquiaIdAndActiveTrue(pastoralId, paroquiaId).isEmpty()
                || !pastoraisPermissao.visivel(pastoralId)) {
            throw new RecursoNaoEncontradoException("Pastoral", pastoralId);
        }
    }

    private EscalaPessoalDTO escalaPessoal(Alocacao a) {
        Vaga v = a.getVaga();
        Celebracao c = v.getCelebracao();
        return new EscalaPessoalDTO(a.getId(), a.getStatus(), a.getDataLimiteResposta(), a.getOrigem(),
                v.getHorarioChegada(), v.getObservacao(),
                new CelebracaoRef(c.getId(), c.getTitulo(), c.getTipo(), c.getData(), c.getHora()),
                new Ref(v.getFuncao().getId(), v.getFuncao().getNome()),
                new Ref(v.getFuncao().getPastoral().getId(), v.getFuncao().getPastoral().getNome()));
    }
}
