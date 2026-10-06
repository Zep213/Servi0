package br.com.zep.servio.service.escalacao.job;

import br.com.zep.servio.model.Alocacao;
import br.com.zep.servio.model.Celebracao;
import br.com.zep.servio.model.enumerated.StatusConvite;
import br.com.zep.servio.model.enumerated.TipoNotificacao;
import br.com.zep.servio.repository.AlocacaoRepository;
import br.com.zep.servio.service.escalacao.ConfiguracaoPastoralService;
import br.com.zep.servio.service.escalacao.TokenConvite;
import br.com.zep.servio.service.notification.EnvioEscalacao;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Lembretes por e-mail (Etapa 6, Parte 5). Resposta: convite pendente a N horas do prazo (N da
 * pastoral). Serviço: convite aceito a N horas da celebração (N da pastoral). Cada lembrete sai uma
 * vez por convite, pelo registro em notificacao_enviada.
 *
 * <p>Lembrete de resposta troca o token: o link precisa valer, e o token só existe em memória no
 * envio. Quem clicou no convite original perde o link antigo; o lembrete traz o novo.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "servio.jobs.enabled", havingValue = "true", matchIfMissing = true)
public class LembretesJob {

    /** Celebrações até esta distância são candidatas ao lembrete de serviço (cobre a janela de config). */
    private static final int DIAS_A_FRENTE_LEMBRETE_SERVICO = 8;

    private final AlocacaoRepository alocacaoRepository;
    private final ConfiguracaoPastoralService configuracao;
    private final EnvioEscalacao envio;
    private final TokenConvite tokenConvite;
    private final Clock clock;

    @Value("${servio.front-url}")
    private String frontUrl;

    @Scheduled(cron = "0 */15 * * * *", zone = "${servio.fuso}")
    public void executar() {
        lembretesDeResposta(LocalDateTime.now(clock));
        lembretesDeServico(LocalDateTime.now(clock));
    }

    void lembretesDeResposta(LocalDateTime agora) {
        for (Alocacao alocacao : alocacaoRepository.candidatosLembreteResposta(
                StatusConvite.PENDENTE, TipoNotificacao.LEMBRETE_RESPOSTA, agora)) {
            try {
                long horas = configuracao.lembreteRespostaHorasAntesDoPrazo(pastoralId(alocacao));
                if (agora.isBefore(alocacao.getDataLimiteResposta().minusHours(horas))) {
                    continue;
                }
                String token = tokenConvite.gerar();
                boolean enviou = envio.registrarPrimeiroEnvio(alocacao, TipoNotificacao.LEMBRETE_RESPOSTA,
                        gerenciada -> gerenciada.setTokenHash(tokenConvite.hash(token)));
                if (!enviou) {
                    continue;
                }
                Map<String, Object> variaveis = envio.variaveisDaEscala(alocacao);
                variaveis.put("link", frontUrl + "/convite#" + token);
                String assunto = "Responda ao convite: " + variaveis.get("titulo") + " (" + variaveis.get("data") + ")";
                variaveis.put("assunto", assunto);
                envio.enviarSeguro(alocacao.getUsuario().getEmail(), assunto, "lembrete-resposta", variaveis, alocacao.getId());
            } catch (RuntimeException e) {
                log.error("Falha no lembrete de resposta da alocação {}", alocacao.getId(), e);
            }
        }
    }

    void lembretesDeServico(LocalDateTime agora) {
        LocalDate hoje = agora.toLocalDate();
        for (Alocacao alocacao : alocacaoRepository.candidatosLembrete(StatusConvite.ACEITA,
                TipoNotificacao.LEMBRETE_SERVICO, hoje, hoje.plusDays(DIAS_A_FRENTE_LEMBRETE_SERVICO))) {
            try {
                Celebracao celebracao = alocacao.getVaga().getCelebracao();
                if (celebracao.getHora() == null) {
                    continue;
                }
                LocalDateTime inicio = celebracao.getData().atTime(celebracao.getHora());
                long horas = configuracao.lembreteServicoHorasAntes(pastoralId(alocacao));
                if (!agora.isBefore(inicio) || agora.isBefore(inicio.minusHours(horas))) {
                    continue;
                }
                if (!envio.registrarPrimeiroEnvio(alocacao, TipoNotificacao.LEMBRETE_SERVICO)) {
                    continue;
                }
                Map<String, Object> variaveis = new HashMap<>(envio.variaveisDaEscala(alocacao));
                String assunto = "Lembrete de serviço: " + variaveis.get("titulo") + " (" + variaveis.get("data") + ")";
                variaveis.put("assunto", assunto);
                envio.enviarSeguro(alocacao.getUsuario().getEmail(), assunto, "lembrete-servico", variaveis, alocacao.getId());
            } catch (RuntimeException e) {
                log.error("Falha no lembrete de serviço da alocação {}", alocacao.getId(), e);
            }
        }
    }

    private static Long pastoralId(Alocacao alocacao) {
        return alocacao.getVaga().getFuncao().getPastoral().getId();
    }
}
