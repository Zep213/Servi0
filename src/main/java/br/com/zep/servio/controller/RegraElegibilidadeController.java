package br.com.zep.servio.controller;

import br.com.zep.servio.model.dto.RegraCatalogoDTO;
import br.com.zep.servio.service.escalacao.ConfiguracaoPastoralService;
import br.com.zep.servio.service.escalacao.regra.RegraElegibilidade;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Catálogo de regras disponíveis, para o front montar a tela de configuração de cada pastoral. */
@RestController
@RequestMapping("/api/regras")
@RequiredArgsConstructor
public class RegraElegibilidadeController {

    private final ConfiguracaoPastoralService configuracaoPastoralService;

    @GetMapping("/catalogo")
    public List<RegraCatalogoDTO> catalogo() {
        List<RegraCatalogoDTO> catalogo = new java.util.ArrayList<>();
        for (RegraElegibilidade regra : configuracaoPastoralService.catalogo()) {
            catalogo.add(new RegraCatalogoDTO(regra.codigo(), regra.descricao(), regra.padroes()));
        }
        catalogo.add(new RegraCatalogoDTO(
                ConfiguracaoPastoralService.CHAVE_PRAZO_RESPOSTA,
                "Prazo para o convidado responder à escalação",
                Map.of("horas", 24)));
        return catalogo;
    }
}
