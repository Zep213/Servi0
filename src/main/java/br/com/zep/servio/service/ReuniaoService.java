package br.com.zep.servio.service;

import br.com.zep.servio.exception.RecursoNaoEncontradoException;
import br.com.zep.servio.exception.RegraNegocioException;
import br.com.zep.servio.mapper.ReuniaoMapper;
import br.com.zep.servio.model.Pastoral;
import br.com.zep.servio.model.Reuniao;
import br.com.zep.servio.model.UsuarioPastoral;
import br.com.zep.servio.model.dto.ReuniaoRequestDTO;
import br.com.zep.servio.model.dto.ReuniaoResponseDTO;
import br.com.zep.servio.model.dto.SolicitacaoReuniaoRequestDTO;
import br.com.zep.servio.model.enumerated.PapelPastoral;
import br.com.zep.servio.repository.PastoralRepository;
import br.com.zep.servio.repository.ReuniaoRepository;
import br.com.zep.servio.repository.UsuarioPastoralRepository;
import br.com.zep.servio.security.PastoraisPermissao;
import br.com.zep.servio.security.UsuarioLogado;
import br.com.zep.servio.security.UsuarioPrincipal;
import br.com.zep.servio.service.notification.Notificador;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Só o coordenador da pastoral marca reunião diretamente. Os demais papéis
 * (vice/secretário/tesoureiro/membro) só podem solicitar por e-mail ao coordenador.
 */
@Service
@RequiredArgsConstructor
public class ReuniaoService {

    private final ReuniaoRepository repository;
    private final ReuniaoMapper mapper;
    private final PastoralRepository pastoralRepository;
    private final UsuarioPastoralRepository usuarioPastoralRepository;
    private final PastoraisPermissao pastoraisPermissao;
    private final UsuarioLogado usuarioLogado;
    private final Notificador notificador;

    @Transactional(readOnly = true)
    public Page<ReuniaoResponseDTO> listar(Long pastoralId, Pageable pageable) {
        pastoral(pastoralId);
        return repository.findByPastoralIdAndActiveTrue(pastoralId, pageable).map(mapper::toResponse);
    }

    @Transactional
    public ReuniaoResponseDTO marcar(Long pastoralId, ReuniaoRequestDTO request) {
        if (!pastoraisPermissao.temPapel(pastoralId, PapelPastoral.COORDENADOR.name())) {
            throw new AccessDeniedException("Só o coordenador da pastoral marca reunião");
        }
        Pastoral pastoral = pastoral(pastoralId);

        Reuniao entidade = mapper.toEntity(request);
        entidade.setPastoral(pastoral);
        entidade.setParoquiaId(usuarioLogado.paroquiaId());
        return mapper.toResponse(repository.save(entidade));
    }

    /** Vice/secretário/tesoureiro pedem reunião ao coordenador em vez de marcar direto. */
    @Transactional
    public void solicitar(Long pastoralId, SolicitacaoReuniaoRequestDTO request) {
        Pastoral pastoral = pastoral(pastoralId);
        UsuarioPrincipal autor = usuarioLogado.get();

        UsuarioPastoral participacao = usuarioPastoralRepository
                .findByUsuarioIdAndPastoralIdAndActiveTrue(autor.getId(), pastoralId)
                .orElseThrow(() -> new AccessDeniedException("Só quem tem papel nesta pastoral pode solicitar reunião"));
        if (participacao.getPapel() == PapelPastoral.COORDENADOR) {
            throw new RegraNegocioException("Você já é coordenador desta pastoral: marque a reunião diretamente");
        }

        var coordenadores = usuarioPastoralRepository.findByPastoralIdAndPapelAndActiveTrue(pastoralId, PapelPastoral.COORDENADOR);
        if (coordenadores.isEmpty()) {
            throw new RecursoNaoEncontradoException("Coordenador da pastoral " + pastoral.getNome(), pastoralId);
        }

        String assunto = "Pedido de reunião - " + pastoral.getNome();
        String mensagem = autor.getNome() + " pediu uma reunião da pastoral " + pastoral.getNome()
                + ":\n\n" + request.motivo();
        for (UsuarioPastoral coordenador : coordenadores) {
            notificador.notificar(coordenador.getUsuario().getEmail(), assunto, mensagem);
        }
    }

    private Pastoral pastoral(Long pastoralId) {
        return pastoralRepository.findByIdAndParoquiaIdAndActiveTrue(pastoralId, usuarioLogado.paroquiaId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Pastoral", pastoralId));
    }
}
