package br.com.zep.servio.service;

import br.com.zep.servio.exception.RecursoNaoEncontradoException;
import br.com.zep.servio.model.ActivatableEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * CRUD com exclusão lógica. As subclasses resolvem os relacionamentos (ids do DTO -> entidades)
 * em {@link #paraEntidade} e {@link #atualizarEntidade}.
 */
public abstract class CrudService<E extends ActivatableEntity, Req, Res> {

    protected abstract JpaRepository<E, Long> repository();

    protected abstract String nomeRecurso();

    protected abstract Res paraResposta(E entidade);

    protected abstract E paraEntidade(Req request);

    protected abstract void atualizarEntidade(Req request, E entidade);

    /** Regras de unicidade/negócio antes de criar. */
    protected void validarCriacao(Req request) {
    }

    @Transactional(readOnly = true)
    public List<Res> listar() {
        return repository().findAll().stream().filter(E::isActive).map(this::paraResposta).toList();
    }

    @Transactional(readOnly = true)
    public Res buscar(Long id) {
        return paraResposta(obterAtivo(id));
    }

    @Transactional
    public Res criar(Req request) {
        validarCriacao(request);
        return paraResposta(repository().save(paraEntidade(request)));
    }

    @Transactional
    public Res atualizar(Long id, Req request) {
        E entidade = obterAtivo(id);
        atualizarEntidade(request, entidade);
        return paraResposta(repository().save(entidade));
    }

    @Transactional
    public void desativar(Long id) {
        E entidade = obterAtivo(id);
        entidade.setActive(false);
        repository().save(entidade);
    }

    protected E obterAtivo(Long id) {
        return repository().findById(id)
                .filter(E::isActive)
                .orElseThrow(() -> new RecursoNaoEncontradoException(nomeRecurso(), id));
    }

    /** Busca uma entidade referenciada pelo DTO; 404 se não existir. */
    protected <T> T referencia(JpaRepository<T, Long> repo, Long id, String recurso) {
        return repo.findById(id).orElseThrow(() -> new RecursoNaoEncontradoException(recurso, id));
    }

    /** Igual a {@link #referencia}, mas aceita id nulo (relacionamento opcional). */
    protected <T> T referenciaOpcional(JpaRepository<T, Long> repo, Long id, String recurso) {
        return id == null ? null : referencia(repo, id, recurso);
    }
}
