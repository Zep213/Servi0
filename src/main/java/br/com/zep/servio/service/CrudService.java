package br.com.zep.servio.service;

import br.com.zep.servio.exception.RecursoNaoEncontradoException;
import br.com.zep.servio.model.TenantEntity;
import br.com.zep.servio.repository.TenantRepository;
import br.com.zep.servio.security.UsuarioLogado;
import br.com.zep.servio.security.UsuarioPrincipal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

/**
 * CRUD com exclusão lógica, sempre restrito à paróquia do usuário logado.
 * As subclasses resolvem relacionamentos em paraEntidade/atualizarEntidade e
 * implementam regras de negócio em validar(), que roda no criar E no atualizar.
 */
public abstract class CrudService<E extends TenantEntity, Req, Res> {

    private UsuarioLogado usuarioLogado;

    @Autowired
    void setUsuarioLogado(UsuarioLogado usuarioLogado) {
        this.usuarioLogado = usuarioLogado;
    }

    protected abstract TenantRepository<E> repository();

    protected abstract String nomeRecurso();

    protected abstract Res paraResposta(E entidade);

    protected abstract E paraEntidade(Req request);

    protected abstract void atualizarEntidade(Req request, E entidade);

    /** Regras de unicidade e de negócio. Roda no criar e no atualizar. */
    protected void validar(E entidade) {
    }

    protected UsuarioPrincipal usuario() {
        return usuarioLogado.get();
    }

    protected Long usuarioId() {
        return usuarioLogado.id();
    }

    protected Long paroquiaId() {
        return usuarioLogado.paroquiaId();
    }

    /** Id a usar nas consultas de unicidade quando a entidade ainda não foi salva. */
    protected Long idOuZero(E entidade) {
        return entidade.getId() == null ? 0L : entidade.getId();
    }

    @Transactional(readOnly = true)
    public Page<Res> listar(Pageable pageable) {
        return repository().findByParoquiaIdAndActiveTrue(paroquiaId(), pageable).map(this::paraResposta);
    }

    @Transactional(readOnly = true)
    public Res buscar(Long id) {
        return paraResposta(obterAtivo(id));
    }

    @Transactional
    public Res criar(Req request) {
        E entidade = paraEntidade(request);
        entidade.setParoquiaId(paroquiaId());
        validar(entidade);
        return paraResposta(repository().save(entidade));
    }

    @Transactional
    public Res atualizar(Long id, Req request) {
        E entidade = obterAtivo(id);
        atualizarEntidade(request, entidade);
        validar(entidade);
        return paraResposta(repository().save(entidade));
    }

    @Transactional
    public void desativar(Long id) {
        E entidade = obterAtivo(id);
        entidade.setActive(false);
        repository().save(entidade);
    }

    protected E obterAtivo(Long id) {
        return repository().findByIdAndParoquiaIdAndActiveTrue(id, paroquiaId())
                .orElseThrow(() -> new RecursoNaoEncontradoException(nomeRecurso(), id));
    }

    /** Referência vinda do DTO: 404 se não existir OU se for de outra paróquia. */
    protected <T extends TenantEntity> T referencia(TenantRepository<T> repo, Long id, String recurso) {
        return repo.findByIdAndParoquiaIdAndActiveTrue(id, paroquiaId())
                .orElseThrow(() -> new RecursoNaoEncontradoException(recurso, id));
    }

    protected <T extends TenantEntity> T referenciaOpcional(TenantRepository<T> repo, Long id, String recurso) {
        return id == null ? null : referencia(repo, id, recurso);
    }
}
