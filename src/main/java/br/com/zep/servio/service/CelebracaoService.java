package br.com.zep.servio.service;

import br.com.zep.servio.exception.RecursoNaoEncontradoException;
import br.com.zep.servio.mapper.CelebracaoMapper;
import br.com.zep.servio.model.Celebracao;
import br.com.zep.servio.model.dto.CelebracaoRequestDTO;
import br.com.zep.servio.model.dto.CelebracaoResponseDTO;
import br.com.zep.servio.repository.CelebracaoRepository;
import br.com.zep.servio.repository.ComunidadeRepository;
import br.com.zep.servio.repository.TenantRepository;
import br.com.zep.servio.repository.VagaRepository;
import br.com.zep.servio.security.PastoraisPermissao;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CelebracaoService extends CrudService<Celebracao, CelebracaoRequestDTO, CelebracaoResponseDTO> {

    private final CelebracaoRepository repository;
    private final CelebracaoMapper mapper;
    private final ComunidadeRepository comunidadeRepository;
    private final VagaRepository vagaRepository;
    private final PastoraisPermissao pastoraisPermissao;
    private final CoberturaAutomaticaService coberturaAutomaticaService;

    @Override
    protected TenantRepository<Celebracao> repository() {
        return repository;
    }

    @Override
    protected String nomeRecurso() {
        return "Celebracao";
    }

    @Override
    protected CelebracaoResponseDTO paraResposta(Celebracao entity) {
        return mapper.toResponse(entity);
    }

    @Override
    protected Celebracao paraEntidade(CelebracaoRequestDTO request) {
        Celebracao entity = mapper.toEntity(request);
        resolverRelacoes(request, entity);
        return entity;
    }

    @Override
    protected void atualizarEntidade(CelebracaoRequestDTO request, Celebracao entity) {
        mapper.updateEntity(request, entity);
        resolverRelacoes(request, entity);
    }

    /** Criar/editar/excluir celebração: só PADRE ou ADMIN (Parte 3.2). */
    @Override
    protected void validar(Celebracao entidade) {
        exigirPadreOuAdmin();
    }

    @Override
    @Transactional
    public CelebracaoResponseDTO criar(CelebracaoRequestDTO request) {
        CelebracaoResponseDTO criada = super.criar(request);
        coberturaAutomaticaService.aplicarNaCriacao(obterAtivo(criada.id()), paroquiaId());
        return criada;
    }

    @Override
    @Transactional
    public void desativar(Long id) {
        exigirPadreOuAdmin();
        super.desativar(id);
    }

    private void exigirPadreOuAdmin() {
        if (!pastoraisPermissao.ehAdmin() && !pastoraisPermissao.ehPadre()) {
            throw new AccessDeniedException("Só padre ou admin gerencia celebrações");
        }
    }

    /**
     * Parte 4: PADRE/ADMIN veem todas as celebrações; os demais só as que têm vaga de
     * alguma pastoral do usuário.
     */
    @Override
    @Transactional(readOnly = true)
    public Page<CelebracaoResponseDTO> listar(Pageable pageable) {
        Optional<List<Long>> visiveis = pastoraisPermissao.pastoraisVisiveis();
        if (visiveis.isEmpty()) {
            return super.listar(pageable);
        }
        if (visiveis.get().isEmpty()) {
            return Page.empty(pageable);
        }
        return repository.findVisiveisPorPastorais(paroquiaId(), visiveis.get(), pageable).map(this::paraResposta);
    }

    @Override
    @Transactional(readOnly = true)
    public CelebracaoResponseDTO buscar(Long id) {
        Celebracao entidade = obterAtivo(id);
        exigirVisivel(entidade);
        return paraResposta(entidade);
    }

    private void exigirVisivel(Celebracao entidade) {
        Optional<List<Long>> visiveis = pastoraisPermissao.pastoraisVisiveis();
        if (visiveis.isEmpty()) {
            return;
        }
        boolean visivel = !visiveis.get().isEmpty()
                && vagaRepository.existsByCelebracaoIdAndFuncaoPastoralIdInAndActiveTrue(entidade.getId(), visiveis.get());
        if (!visivel) {
            throw new RecursoNaoEncontradoException(nomeRecurso(), entidade.getId());
        }
    }

    private void resolverRelacoes(CelebracaoRequestDTO request, Celebracao entity) {
        entity.setComunidade(referencia(comunidadeRepository, request.comunidadeId(), "Comunidade"));
    }
}
