package br.com.zep.servio.service.escalacao;

import br.com.zep.servio.model.Alocacao;
import br.com.zep.servio.model.enumerated.StatusConvite;
import br.com.zep.servio.service.escalacao.evento.ConviteExpiradoEvent;
import br.com.zep.servio.service.escalacao.evento.ConviteRecusadoEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * Regra única de aceitar/recusar um convite, usada pela rota autenticada (/api/alocacoes/{id}/responder)
 * e pela página pública (/api/confirmacoes/responder). Cada rota decide o código HTTP do prazo vencido.
 */
@Service
@RequiredArgsConstructor
public class RespostaConviteService {

    public static final int TAMANHO_MAXIMO_JUSTIFICATIVA = 500;

    private final ApplicationEventPublisher eventos;
    private final Clock clock;

    /**
     * Aplica a resposta e zera o token (o link deixa de valer). Se o prazo já passou, grava EXPIRADA
     * e devolve EXPIRADA sem mudar para aceita/recusada (e avisa o coordenador). Quem chama persiste e decide o erro.
     */
    public StatusConvite aplicar(Alocacao alocacao, boolean aceitar, String justificativa) {
        LocalDateTime agora = LocalDateTime.now(clock);
        alocacao.setTokenHash(null);
        if (alocacao.getDataLimiteResposta() != null && agora.isAfter(alocacao.getDataLimiteResposta())) {
            alocacao.setStatus(StatusConvite.EXPIRADA);
            eventos.publishEvent(new ConviteExpiradoEvent(alocacao.getId()));
            return StatusConvite.EXPIRADA;
        }
        alocacao.setStatus(aceitar ? StatusConvite.ACEITA : StatusConvite.RECUSADA);
        alocacao.setRespondidoEm(agora);
        alocacao.setJustificativa(aceitar || justificativa == null || justificativa.isBlank() ? null : justificativa.trim());
        if (!aceitar) {
            eventos.publishEvent(new ConviteRecusadoEvent(alocacao.getId()));
        }
        return alocacao.getStatus();
    }
}
