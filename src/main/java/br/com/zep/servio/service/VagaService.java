package br.com.zep.servio.service;

import br.com.zep.servio.mapper.VagaMapper;
import br.com.zep.servio.model.Vaga;
import br.com.zep.servio.model.dto.VagaRequestDTO;
import br.com.zep.servio.model.dto.VagaResponseDTO;
import br.com.zep.servio.model.enumerated.PapelPastoral;
import br.com.zep.servio.repository.CelebracaoRepository;
import br.com.zep.servio.repository.FuncaoRepository;
import br.com.zep.servio.repository.TenantRepository;
import br.com.zep.servio.repository.VagaRepository;
import br.com.zep.servio.security.PastoraisPermissao;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class VagaService extends CrudService<Vaga, VagaRequestDTO, VagaResponseDTO> {

    private final VagaRepository repository;
    private final VagaMapper mapper;
    private final CelebracaoRepository celebracaoRepository;
    private final FuncaoRepository funcaoRepository;
    private final PastoraisPermissao pastoraisPermissao;

    @Override
    protected TenantRepository<Vaga> repository() {
        return repository;
    }

    @Override
    protected String nomeRecurso() {
        return "Vaga";
    }

    @Override
    protected VagaResponseDTO paraResposta(Vaga entity) {
        return mapper.toResponse(entity);
    }

    @Override
    protected Vaga paraEntidade(VagaRequestDTO request) {
        Vaga entity = mapper.toEntity(request);
        resolverRelacoes(request, entity);
        return entity;
    }

    @Override
    protected void atualizarEntidade(VagaRequestDTO request, Vaga entity) {
        mapper.updateEntity(request, entity);
        resolverRelacoes(request, entity);
    }

    /**
     * Parte 3.2: PADRE/ADMIN mexem em qualquer vaga, inclusive "responsabilizando" a pastoral
     * (quantidade vazia). COORDENADOR/VICE/SECRETARIO só na própria pastoral, e só criam vaga
     * adicional (sem quantidade ainda não existir) num evento onde a pastoral já é responsável.
     */
    @Override
    protected void validar(Vaga entidade) {
        if (pastoraisPermissao.ehAdmin() || pastoraisPermissao.ehPadre()) {
            return;
        }
        Long pastoralId = entidade.getFuncao().getPastoral().getId();
        boolean gestorPastoral = pastoraisPermissao.temQualquerPapel(pastoralId,
                PapelPastoral.COORDENADOR.name(), PapelPastoral.VICE.name(), PapelPastoral.SECRETARIO.name());
        if (!gestorPastoral) {
            throw new AccessDeniedException("Só coordenador, vice ou secretário da pastoral gerencia vagas");
        }
        boolean criando = entidade.getId() == null;
        if (criando) {
            if (entidade.getQuantidade() == null) {
                throw new AccessDeniedException("Só padre ou admin responsabiliza uma pastoral sem definir quantidade");
            }
            boolean pastoralJaResponsavel = repository.existsByCelebracaoIdAndFuncaoPastoralIdAndActiveTrue(
                    entidade.getCelebracao().getId(), pastoralId);
            if (!pastoralJaResponsavel) {
                throw new AccessDeniedException("Pastoral ainda não é responsável por este evento");
            }
        }
    }

    @Override
    @Transactional
    public void desativar(Long id) {
        Vaga entidade = obterAtivo(id);
        validar(entidade);
        entidade.setActive(false);
        repository.save(entidade);
    }

    private void resolverRelacoes(VagaRequestDTO request, Vaga entity) {
        entity.setCelebracao(referencia(celebracaoRepository, request.celebracaoId(), "Celebracao"));
        entity.setFuncao(referencia(funcaoRepository, request.funcaoId(), "Funcao"));
    }
}
