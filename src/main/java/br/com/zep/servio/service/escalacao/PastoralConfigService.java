package br.com.zep.servio.service.escalacao;

import br.com.zep.servio.model.Pastoral;
import br.com.zep.servio.model.PastoralConfig;
import br.com.zep.servio.model.dto.PastoralConfigRequestDTO;
import br.com.zep.servio.model.dto.PastoralConfigResponseDTO;
import br.com.zep.servio.repository.PastoralConfigRepository;
import br.com.zep.servio.repository.PastoralRepository;
import br.com.zep.servio.security.PastoraisPermissao;
import br.com.zep.servio.security.UsuarioLogado;
import br.com.zep.servio.service.escalacao.regra.RegraElegibilidade;
import br.com.zep.servio.exception.RecursoNaoEncontradoException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Tela de configuração de uma pastoral: lê o catálogo de regras + PRAZO_RESPOSTA já
 * mesclados com o que a pastoral configurou, e permite sobrescrever cada chave (upsert).
 */
@Service
@RequiredArgsConstructor
public class PastoralConfigService {

    private final ConfiguracaoPastoralService configuracaoPastoralService;
    private final PastoralConfigRepository pastoralConfigRepository;
    private final PastoralRepository pastoralRepository;
    private final PastoraisPermissao pastoraisPermissao;
    private final UsuarioLogado usuarioLogado;

    @Transactional(readOnly = true)
    public List<PastoralConfigResponseDTO> efetivas(Long pastoralId) {
        pastoral(pastoralId);
        List<PastoralConfigResponseDTO> resultado = new ArrayList<>();

        for (RegraElegibilidade regra : configuracaoPastoralService.catalogo()) {
            Optional<PastoralConfig> configurada = pastoralConfigRepository
                    .findByPastoralIdAndChaveAndActiveTrue(pastoralId, regra.codigo());
            resultado.add(new PastoralConfigResponseDTO(
                    regra.codigo(),
                    configurada.map(PastoralConfig::isAtiva).orElse(true),
                    configurada.map(PastoralConfig::getParametros).orElseGet(regra::padroes),
                    configurada.isPresent()));
        }

        Optional<PastoralConfig> prazo = pastoralConfigRepository.findByPastoralIdAndChaveAndActiveTrue(
                pastoralId, ConfiguracaoPastoralService.CHAVE_PRAZO_RESPOSTA);
        resultado.add(new PastoralConfigResponseDTO(
                ConfiguracaoPastoralService.CHAVE_PRAZO_RESPOSTA,
                prazo.map(PastoralConfig::isAtiva).orElse(true),
                prazo.map(PastoralConfig::getParametros)
                        .orElseGet(() -> Map.of("horas", configuracaoPastoralService.prazoRespostaHoras(pastoralId))),
                prazo.isPresent()));

        return resultado;
    }

    /**
     * Só o coordenador da pastoral configura as regras de elegibilidade (Parte C, item 3) —
     * pastoral fora do alcance do usuário (Parte 4) dá 404, não 403.
     */
    @Transactional
    public PastoralConfigResponseDTO salvar(Long pastoralId, String chave, PastoralConfigRequestDTO request) {
        Pastoral pastoral = pastoral(pastoralId);
        if (!pastoraisPermissao.visivel(pastoralId)) {
            throw new RecursoNaoEncontradoException("Pastoral", pastoralId);
        }
        if (!pastoraisPermissao.temPapel(pastoralId, "COORDENADOR")) {
            throw new AccessDeniedException("Só o coordenador da pastoral (ou padre/ADMIN) configura as regras");
        }
        PastoralConfig config = pastoralConfigRepository.findByPastoralIdAndChaveAndActiveTrue(pastoralId, chave)
                .orElseGet(PastoralConfig::new);

        config.setPastoral(pastoral);
        config.setChave(chave);
        config.setAtiva(request.ativa());
        config.setParametros(request.parametros());
        if (config.getId() == null) {
            config.setParoquiaId(usuarioLogado.paroquiaId());
        }
        PastoralConfig salva = pastoralConfigRepository.save(config);
        return new PastoralConfigResponseDTO(salva.getChave(), salva.isAtiva(), salva.getParametros(), true);
    }

    private Pastoral pastoral(Long pastoralId) {
        return pastoralRepository.findByIdAndParoquiaIdAndActiveTrue(pastoralId, usuarioLogado.paroquiaId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Pastoral", pastoralId));
    }
}
