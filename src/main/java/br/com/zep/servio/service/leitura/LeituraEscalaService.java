package br.com.zep.servio.service.leitura;

import br.com.zep.servio.exception.RecursoNaoEncontradoException;
import br.com.zep.servio.exception.RegraNegocioException;
import br.com.zep.servio.model.Alocacao;
import br.com.zep.servio.model.Celebracao;
import br.com.zep.servio.model.Usuario;
import br.com.zep.servio.model.UsuarioPastoral;
import br.com.zep.servio.model.Vaga;
import br.com.zep.servio.model.dto.EscalaDTOs.AlocacaoEscalaDTO;
import br.com.zep.servio.model.dto.EscalaDTOs.CelebracaoRef;
import br.com.zep.servio.model.dto.EscalaDTOs.EscalaCelebracaoDTO;
import br.com.zep.servio.model.dto.EscalaDTOs.EscalaPessoalDTO;
import br.com.zep.servio.model.dto.EscalaDTOs.MembroDetalheDTO;
import br.com.zep.servio.model.dto.EscalaDTOs.MembroPastoralDTO;
import br.com.zep.servio.model.dto.EscalaDTOs.Ref;
import br.com.zep.servio.model.dto.EscalaDTOs.VagaEscalaDTO;
import br.com.zep.servio.model.enumerated.PapelPastoral;
import br.com.zep.servio.model.enumerated.StatusAtivacao;
import br.com.zep.servio.model.enumerated.StatusConvite;
import br.com.zep.servio.repository.AlocacaoRepository;
import br.com.zep.servio.repository.CelebracaoRepository;
import br.com.zep.servio.repository.PastoralRepository;
import br.com.zep.servio.repository.UsuarioFuncaoRepository;
import br.com.zep.servio.repository.UsuarioPastoralRepository;
import br.com.zep.servio.repository.VagaRepository;
import br.com.zep.servio.security.PastoraisPermissao;
import br.com.zep.servio.service.FotoPerfilService;
import br.com.zep.servio.security.UsuarioLogado;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Leituras para as telas de escala, membros e "minhas escalas" (Etapa 7, Parte 1). Só consultas:
 * as regras de quem pode ver ficam aqui, não no front.
 */
@Service
@RequiredArgsConstructor
public class LeituraEscalaService {

    private static final int DIAS_PADRAO_ESCALAS = 60;
    private static final long MAXIMO_DIAS_PERIODO = 366;
    private static final List<String> PAPEIS_DA_ESCALA = List.of("COORDENADOR", "VICE", "SECRETARIO", "TESOUREIRO", "TECNICO");

    private final AlocacaoRepository alocacaoRepository;
    private final VagaRepository vagaRepository;
    private final CelebracaoRepository celebracaoRepository;
    private final PastoralRepository pastoralRepository;
    private final UsuarioPastoralRepository usuarioPastoralRepository;
    private final UsuarioFuncaoRepository usuarioFuncaoRepository;
    private final PastoraisPermissao pastoraisPermissao;
    private final FotoPerfilService fotoPerfilService;
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

    /** Filtros opcionais da lista de membros; todos combináveis. {@code status} só a coordenação pode usar. */
    public record FiltroMembros(String nome, PapelPastoral papel, StatusAtivacao status, Long habilidade) {
        public static final FiltroMembros NENHUM = new FiltroMembros(null, null, null, null);
    }

    /**
     * Membros ativos da pastoral. O e-mail, {@code ativada} e o status de ativação só saem para quem coordena (ou
     * PADRE/ADMIN, que valem como coordenador): os demais recebem o nome e o papel, sem contato nem situação da conta.
     */
    @Transactional(readOnly = true)
    public List<MembroPastoralDTO> membros(Long pastoralId) {
        return membros(pastoralId, FiltroMembros.NENHUM);
    }

    /**
     * A lista de uma pastoral tem dezenas de pessoas e já vem inteira em uma consulta; os filtros rodam sobre ela (o de
     * nome ignora maiúsculas e acentos) e o de habilidade usa uma consulta só para a função pedida. Filtrar por status
     * é da coordenação: para os demais seria um jeito de descobrir a situação da conta dos outros.
     */
    @Transactional(readOnly = true)
    public List<MembroPastoralDTO> membros(Long pastoralId, FiltroMembros filtro) {
        Long paroquiaId = usuarioLogado.paroquiaId();
        exigirPastoralVisivel(pastoralId, paroquiaId);
        boolean veEmail = pastoraisPermissao.temPapel(pastoralId, "COORDENADOR");
        if (filtro.status() != null && !veEmail) {
            throw new AccessDeniedException("Só a coordenação filtra por situação da conta");
        }
        Set<Long> comHabilidade = filtro.habilidade() == null ? null : usuariosComHabilidade(pastoralId, filtro.habilidade());
        String busca = filtro.nome() == null || filtro.nome().isBlank() ? null : semAcento(filtro.nome());

        List<MembroPastoralDTO> lista = new ArrayList<>();
        List<UsuarioPastoral> participacoes = usuarioPastoralRepository.membrosDaPastoral(paroquiaId, pastoralId);
        Map<Long, String> fotos = fotoPerfilService.fotoUrls(
                participacoes.stream().map(up -> up.getUsuario().getId()).toList());
        for (UsuarioPastoral up : participacoes) {
            Usuario usuario = up.getUsuario();
            if ((busca != null && !semAcento(usuario.getNome()).contains(busca))
                    || (filtro.papel() != null && up.getPapel() != filtro.papel())
                    || (filtro.status() != null && usuario.statusAtivacao() != filtro.status())
                    || (comHabilidade != null && !comHabilidade.contains(usuario.getId()))) {
                continue;
            }
            lista.add(new MembroPastoralDTO(up.getId(),
                    new Ref(usuario.getId(), usuario.getNome()),
                    up.getPapel(),
                    veEmail ? usuario.getEmail() : null,
                    veEmail ? usuario.estaAtivada() : null,
                    veEmail ? usuario.statusAtivacao() : null,
                    fotos.get(usuario.getId())));
        }
        return lista;
    }

    /** Um membro da pastoral, com habilidades, último serviço e convites pendentes dele nesta pastoral. */
    @Transactional(readOnly = true)
    public MembroDetalheDTO membro(Long pastoralId, Long usuarioId) {
        Long paroquiaId = usuarioLogado.paroquiaId();
        exigirPastoralVisivel(pastoralId, paroquiaId);
        boolean veEmail = pastoraisPermissao.temPapel(pastoralId, "COORDENADOR");
        UsuarioPastoral up = usuarioPastoralRepository.findByUsuarioIdAndPastoralIdAndActiveTrue(usuarioId, pastoralId)
                .filter(participacao -> paroquiaId.equals(participacao.getParoquiaId()))
                .filter(participacao -> participacao.getUsuario().isActive())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Membro", usuarioId));
        Usuario usuario = up.getUsuario();

        List<Ref> habilidades = usuarioFuncaoRepository.findByUsuarioIdAndActiveTrue(usuarioId).stream()
                .filter(h -> h.getFuncao().isActive() && h.getFuncao().getPastoral().getId().equals(pastoralId))
                .map(h -> new Ref(h.getFuncao().getId(), h.getFuncao().getNome()))
                .toList();
        LocalDate ultimoServico = alocacaoRepository.ultimosServicos(paroquiaId, StatusConvite.ACEITA,
                        List.of(usuarioId), LocalDate.now(clock)).stream()
                .findFirst().map(AlocacaoRepository.UltimoServico::getUltimo).orElse(null);
        long convitesPendentes = alocacaoRepository.contarDoUsuarioNaPastoral(paroquiaId, usuarioId, pastoralId,
                StatusConvite.PENDENTE);

        return new MembroDetalheDTO(new Ref(usuario.getId(), usuario.getNome()), up.getPapel(), habilidades,
                ultimoServico, convitesPendentes,
                veEmail ? usuario.getEmail() : null,
                veEmail ? usuario.statusAtivacao() : null,
                veEmail ? usuario.getAtivadoEm() : null,
                fotoPerfilService.fotoUrl(usuarioId));
    }

    private Set<Long> usuariosComHabilidade(Long pastoralId, Long funcaoId) {
        return usuarioFuncaoRepository.findByFuncaoIdAndActiveTrue(funcaoId).stream()
                .filter(h -> h.getFuncao().getPastoral().getId().equals(pastoralId))
                .map(h -> h.getUsuario().getId())
                .collect(Collectors.toSet());
    }

    /** Minúsculas e sem acento: "José" encontra "jose" e "JOSÉ". */
    static String semAcento(String texto) {
        return Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT).trim();
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
