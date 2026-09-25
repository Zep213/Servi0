package br.com.zep.servio.service;

import br.com.zep.servio.exception.RecursoNaoEncontradoException;
import br.com.zep.servio.exception.RegraNegocioException;
import br.com.zep.servio.mapper.AlteracaoPendenteMapper;
import br.com.zep.servio.model.Alocacao;
import br.com.zep.servio.model.AlteracaoPendente;
import br.com.zep.servio.model.Pastoral;
import br.com.zep.servio.model.Usuario;
import br.com.zep.servio.model.Vaga;
import br.com.zep.servio.model.dto.AlteracaoPendenteResponseDTO;
import br.com.zep.servio.model.enumerated.StatusAlteracaoPendente;
import br.com.zep.servio.model.enumerated.StatusConvite;
import br.com.zep.servio.repository.AlocacaoRepository;
import br.com.zep.servio.repository.AlteracaoPendenteRepository;
import br.com.zep.servio.security.PastoraisPermissao;
import br.com.zep.servio.security.UsuarioLogado;
import br.com.zep.servio.service.escalacao.ConfiguracaoPastoralService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Alterações de escala feitas pelo vice: a mudança já foi aplicada na Alocacao (e o
 * convite já saiu) no momento em que foi feita; este registro fica pendente até o
 * coordenador da pastoral confirmar ou desfazer.
 */
@Service
@RequiredArgsConstructor
public class AlteracaoPendenteService {

    private final AlteracaoPendenteRepository repository;
    private final AlteracaoPendenteMapper mapper;
    private final AlocacaoRepository alocacaoRepository;
    private final ConfiguracaoPastoralService configuracaoPastoralService;
    private final PastoraisPermissao pastoraisPermissao;
    private final UsuarioLogado usuarioLogado;

    @Transactional
    public void registrar(Alocacao alocacaoAtualizada, Pastoral pastoral, Usuario autor,
                           Vaga vagaAnterior, Usuario usuarioAnterior) {
        AlteracaoPendente pendente = new AlteracaoPendente();
        pendente.setParoquiaId(usuarioLogado.paroquiaId());
        pendente.setAlocacao(alocacaoAtualizada);
        pendente.setPastoral(pastoral);
        pendente.setAutor(autor);
        pendente.setVagaAnterior(vagaAnterior);
        pendente.setUsuarioAnterior(usuarioAnterior);
        pendente.setVagaNova(alocacaoAtualizada.getVaga());
        pendente.setUsuarioNovo(alocacaoAtualizada.getUsuario());
        pendente.setStatus(StatusAlteracaoPendente.PENDENTE);
        repository.save(pendente);
    }

    /** Parte 4: só das pastorais que o usuário gerencia (COORDENADOR/VICE); todas para PADRE/ADMIN. */
    @Transactional(readOnly = true)
    public Page<AlteracaoPendenteResponseDTO> listar(Pageable pageable) {
        Optional<List<Long>> gerenciadas = pastoraisPermissao.pastoraisGerenciadas();
        if (gerenciadas.isEmpty()) {
            return repository.findByParoquiaIdAndActiveTrue(usuarioLogado.paroquiaId(), pageable).map(mapper::toResponse);
        }
        if (gerenciadas.get().isEmpty()) {
            return Page.empty(pageable);
        }
        return repository.findByParoquiaIdAndPastoralIdInAndActiveTrue(usuarioLogado.paroquiaId(), gerenciadas.get(), pageable)
                .map(mapper::toResponse);
    }

    @Transactional
    public AlteracaoPendenteResponseDTO confirmar(Long id) {
        AlteracaoPendente pendente = obterPendente(id);
        exigirCoordenador(pendente);
        if (pendente.getStatus() != StatusAlteracaoPendente.PENDENTE) {
            throw new RegraNegocioException("Esta alteração já foi resolvida");
        }
        pendente.setStatus(StatusAlteracaoPendente.CONFIRMADA);
        return mapper.toResponse(repository.save(pendente));
    }

    /** Desfaz a alteração: a Alocacao volta para a vaga/usuário de antes. */
    @Transactional
    public AlteracaoPendenteResponseDTO desfazer(Long id) {
        AlteracaoPendente pendente = obterPendente(id);
        exigirCoordenador(pendente);
        if (pendente.getStatus() != StatusAlteracaoPendente.PENDENTE) {
            throw new RegraNegocioException("Esta alteração já foi resolvida");
        }

        Alocacao alocacao = pendente.getAlocacao();
        alocacao.setVaga(pendente.getVagaAnterior());
        alocacao.setUsuario(pendente.getUsuarioAnterior());
        alocacao.setStatus(StatusConvite.PENDENTE);
        long prazoHoras = configuracaoPastoralService.prazoRespostaHoras(pendente.getPastoral().getId());
        alocacao.setDataLimiteResposta(LocalDateTime.now().plusHours(prazoHoras));
        alocacaoRepository.save(alocacao);

        pendente.setStatus(StatusAlteracaoPendente.DESFEITA);
        return mapper.toResponse(repository.save(pendente));
    }

    /** Pastoral fora do alcance do usuário (Parte 4) dá 404 antes mesmo de checar o papel. */
    private void exigirCoordenador(AlteracaoPendente pendente) {
        Long pastoralId = pendente.getPastoral().getId();
        if (!pastoraisPermissao.visivel(pastoralId)) {
            throw new RecursoNaoEncontradoException("AlteracaoPendente", pendente.getId());
        }
        if (!pastoraisPermissao.temPapel(pastoralId, "COORDENADOR")) {
            throw new AccessDeniedException("Só o coordenador da pastoral confirma ou desfaz alterações");
        }
    }

    private AlteracaoPendente obterPendente(Long id) {
        return repository.findByIdAndParoquiaIdAndActiveTrue(id, usuarioLogado.paroquiaId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("AlteracaoPendente", id));
    }
}
