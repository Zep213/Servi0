package br.com.zep.servio.service;

import br.com.zep.servio.exception.RecursoNaoEncontradoException;
import br.com.zep.servio.model.Funcao;
import br.com.zep.servio.model.Usuario;
import br.com.zep.servio.model.UsuarioFuncao;
import br.com.zep.servio.model.UsuarioPastoral;
import br.com.zep.servio.model.dto.HabilidadeDTO;
import br.com.zep.servio.repository.FuncaoRepository;
import br.com.zep.servio.repository.PastoralRepository;
import br.com.zep.servio.repository.UsuarioFuncaoRepository;
import br.com.zep.servio.repository.UsuarioPastoralRepository;
import br.com.zep.servio.repository.UsuarioRepository;
import br.com.zep.servio.security.PastoraisPermissao;
import br.com.zep.servio.security.UsuarioLogado;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Habilidades de um membro numa pastoral, de uma vez só: a tela "Detalhe do membro" mostra todas as funções da
 * pastoral e manda de volta o conjunto marcado. Ler vale para quem enxerga a pastoral (é o catálogo de funções dela,
 * sem dado pessoal); trocar o conjunto é do coordenador da pastoral, do padre e do ADMIN.
 */
@Service
@RequiredArgsConstructor
public class HabilidadeMembroService {

    private final PastoralRepository pastoralRepository;
    private final FuncaoRepository funcaoRepository;
    private final UsuarioFuncaoRepository usuarioFuncaoRepository;
    private final UsuarioPastoralRepository usuarioPastoralRepository;
    private final UsuarioRepository usuarioRepository;
    private final PastoraisPermissao pastoraisPermissao;
    private final UsuarioFuncaoService usuarioFuncaoService;
    private final AuditLogService auditLogService;
    private final UsuarioLogado usuarioLogado;

    @Transactional(readOnly = true)
    public List<HabilidadeDTO> listar(Long pastoralId, Long usuarioId) {
        exigirPastoralVisivel(pastoralId);
        exigirMembro(pastoralId, usuarioId);
        return montar(pastoralId, usuarioId);
    }

    /**
     * Troca o conjunto: marca as que faltam e desmarca as que sobram, tudo na mesma transação. Uma função que não é
     * da pastoral derruba a operação inteira (404, nada muda).
     */
    @Transactional
    public List<HabilidadeDTO> substituir(Long pastoralId, Long usuarioId, List<Long> funcaoIds) {
        exigirPastoralVisivel(pastoralId);
        usuarioFuncaoService.exigirGestor(pastoralId);
        UsuarioPastoral participacao = exigirMembro(pastoralId, usuarioId);

        Map<Long, Funcao> funcoesDaPastoral = new HashMap<>();
        for (Funcao funcao : funcaoRepository.findByPastoralIdAndActiveTrue(pastoralId)) {
            funcoesDaPastoral.put(funcao.getId(), funcao);
        }
        Set<Long> desejadas = new LinkedHashSet<>(funcaoIds);
        for (Long funcaoId : desejadas) {
            if (!funcoesDaPastoral.containsKey(funcaoId)) {
                throw new RecursoNaoEncontradoException("Funcao", funcaoId);
            }
        }

        Map<Long, UsuarioFuncao> atuais = new HashMap<>();
        for (UsuarioFuncao habilidade : usuarioFuncaoRepository.findByUsuarioIdAndActiveTrue(usuarioId)) {
            if (funcoesDaPastoral.containsKey(habilidade.getFuncao().getId())) {
                atuais.put(habilidade.getFuncao().getId(), habilidade);
            }
        }

        Usuario autor = usuarioRepository.findById(usuarioLogado.id()).orElseThrow();
        for (Long funcaoId : desejadas) {
            if (!atuais.containsKey(funcaoId)) {
                UsuarioFuncao nova = new UsuarioFuncao();
                nova.setParoquiaId(usuarioLogado.paroquiaId());
                nova.setUsuario(participacao.getUsuario());
                nova.setFuncao(funcoesDaPastoral.get(funcaoId));
                usuarioFuncaoRepository.save(nova);
                auditar(autor, nova.getId(), "marcada", usuarioId, funcaoId);
            }
        }
        for (Map.Entry<Long, UsuarioFuncao> atual : atuais.entrySet()) {
            if (!desejadas.contains(atual.getKey())) {
                UsuarioFuncao habilidade = atual.getValue();
                habilidade.setActive(false);
                usuarioFuncaoRepository.save(habilidade);
                auditar(autor, habilidade.getId(), "desmarcada", usuarioId, atual.getKey());
            }
        }
        return montar(pastoralId, usuarioId);
    }

    private List<HabilidadeDTO> montar(Long pastoralId, Long usuarioId) {
        Set<Long> marcadas = new LinkedHashSet<>();
        for (UsuarioFuncao habilidade : usuarioFuncaoRepository.findByUsuarioIdAndActiveTrue(usuarioId)) {
            marcadas.add(habilidade.getFuncao().getId());
        }
        List<HabilidadeDTO> lista = new ArrayList<>();
        for (Funcao funcao : funcaoRepository.findByPastoralIdAndActiveTrue(pastoralId)) {
            lista.add(new HabilidadeDTO(funcao.getId(), funcao.getNome(), marcadas.contains(funcao.getId())));
        }
        return lista;
    }

    /** Pastoral de outra paróquia ou fora do alcance: 404, como se não existisse. */
    private void exigirPastoralVisivel(Long pastoralId) {
        if (pastoralRepository.findByIdAndParoquiaIdAndActiveTrue(pastoralId, usuarioLogado.paroquiaId()).isEmpty()
                || !pastoraisPermissao.visivel(pastoralId)) {
            throw new RecursoNaoEncontradoException("Pastoral", pastoralId);
        }
    }

    private UsuarioPastoral exigirMembro(Long pastoralId, Long usuarioId) {
        return usuarioPastoralRepository.findByUsuarioIdAndPastoralIdAndActiveTrue(usuarioId, pastoralId)
                .filter(up -> usuarioLogado.paroquiaId().equals(up.getParoquiaId()))
                .orElseThrow(() -> new RecursoNaoEncontradoException("Membro", usuarioId));
    }

    private void auditar(Usuario autor, Long habilidadeId, String acao, Long usuarioId, Long funcaoId) {
        auditLogService.registrar("HABILIDADE_ALTERADA", habilidadeId, autor, usuarioLogado.paroquiaId(),
                "Habilidade " + acao + ": usuário " + usuarioId + ", função " + funcaoId);
    }
}
