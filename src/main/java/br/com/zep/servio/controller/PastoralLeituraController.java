package br.com.zep.servio.controller;

import br.com.zep.servio.model.dto.EscalaDTOs.EscalaCelebracaoDTO;
import br.com.zep.servio.model.dto.EscalaDTOs.MembroDetalheDTO;
import br.com.zep.servio.model.dto.EscalaDTOs.MembroPastoralDTO;
import br.com.zep.servio.model.enumerated.PapelPastoral;
import br.com.zep.servio.model.enumerated.StatusAtivacao;
import br.com.zep.servio.service.leitura.LeituraEscalaService;
import br.com.zep.servio.service.leitura.LeituraEscalaService.FiltroMembros;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Leituras de uma pastoral para as telas de gestão (escala da celebração e membros). */
@RestController
@RequestMapping("/api/pastorais/{pastoralId}")
@RequiredArgsConstructor
public class PastoralLeituraController {

    private final LeituraEscalaService leituraEscalaService;

    @GetMapping("/celebracoes/{celebracaoId}/escala")
    public EscalaCelebracaoDTO escala(@PathVariable Long pastoralId, @PathVariable Long celebracaoId) {
        return leituraEscalaService.escalaDaCelebracao(pastoralId, celebracaoId);
    }

    /**
     * Filtros opcionais e combináveis: nome (parte do nome, sem maiúsculas nem acentos), papel, status (situação da
     * conta, só coordenação) e habilidade (id de função da pastoral).
     */
    @GetMapping("/membros")
    public List<MembroPastoralDTO> membros(@PathVariable Long pastoralId,
                                           @RequestParam(required = false) String nome,
                                           @RequestParam(required = false) PapelPastoral papel,
                                           @RequestParam(required = false) StatusAtivacao status,
                                           @RequestParam(required = false) Long habilidade) {
        return leituraEscalaService.membros(pastoralId, new FiltroMembros(nome, papel, status, habilidade));
    }

    @GetMapping("/membros/{usuarioId}")
    public MembroDetalheDTO membro(@PathVariable Long pastoralId, @PathVariable Long usuarioId) {
        return leituraEscalaService.membro(pastoralId, usuarioId);
    }
}
