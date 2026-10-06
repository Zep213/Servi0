package br.com.zep.servio.service.notification;

import br.com.zep.servio.config.AsyncConfig;
import br.com.zep.servio.model.Alocacao;
import br.com.zep.servio.model.Vaga;
import br.com.zep.servio.model.enumerated.StatusConvite;
import br.com.zep.servio.model.enumerated.TipoNotificacao;
import br.com.zep.servio.repository.AlocacaoRepository;
import br.com.zep.servio.repository.VagaRepository;
import br.com.zep.servio.service.escalacao.TokenConvite;
import br.com.zep.servio.service.escalacao.evento.AlocacaoSubstituidaEvent;
import br.com.zep.servio.service.escalacao.evento.ConviteCriadoEvent;
import br.com.zep.servio.service.escalacao.evento.ConviteExpiradoEvent;
import br.com.zep.servio.service.escalacao.evento.ConviteRecusadoEvent;
import br.com.zep.servio.service.escalacao.evento.VagaSemElegiveisEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;

/**
 * E-mails da escalação disparados por eventos (Etapa 6, Parte 4). Cada aviso roda depois do commit,
 * numa thread do executor de e-mail: se o SMTP falhar, loga e a escala continua como foi gravada.
 *
 * <p>Os lembretes e a expiração por prazo são feitos pelos jobs ({@code service.escalacao.job}).
 * Nunca loga o token: o link do convite só sai no corpo do e-mail.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificacaoListener {

    private final AlocacaoRepository alocacaoRepository;
    private final VagaRepository vagaRepository;
    private final TokenConvite tokenConvite;
    private final EnvioEscalacao envio;

    @Value("${servio.front-url}")
    private String frontUrl;

    @Async(AsyncConfig.EXECUTOR_EMAIL)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void aoConvidar(ConviteCriadoEvent evento) {
        try {
            Alocacao alocacao = alocacaoRepository.findParaEmail(evento.alocacaoId()).orElse(null);
            if (alocacao == null || alocacao.getStatus() != StatusConvite.PENDENTE || !tokenAtual(alocacao, evento.token())) {
                // Já respondido, substituído ou com token trocado depois: o link deste evento não vale mais
                return;
            }
            if (evento.tipo() == TipoNotificacao.CONVITE && !envio.registrarPrimeiroEnvio(alocacao, evento.tipo())) {
                return;
            }
            Map<String, Object> variaveis = envio.variaveisDaEscala(alocacao);
            variaveis.put("link", frontUrl + "/convite#" + evento.token());
            String assunto = (evento.tipo() == TipoNotificacao.CONVITE_REENVIO ? "Reenvio do convite: " : "Convite: ")
                    + variaveis.get("titulo") + " (" + variaveis.get("data") + ")";
            variaveis.put("assunto", assunto);
            envio.enviarSeguro(alocacao.getUsuario().getEmail(), assunto, "convite", variaveis, alocacao.getId());
        } catch (RuntimeException e) {
            log.error("Falha ao preparar o convite da alocação {}", evento.alocacaoId(), e);
        }
    }

    @Async(AsyncConfig.EXECUTOR_EMAIL)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void aoRecusar(ConviteRecusadoEvent evento) {
        try {
            Alocacao alocacao = alocacaoRepository.findParaEmail(evento.alocacaoId()).orElse(null);
            if (alocacao == null || !envio.registrarPrimeiroEnvio(alocacao, TipoNotificacao.AVISO_COORDENADOR_RECUSA)) {
                return;
            }
            Map<String, Object> dados = envio.variaveisDaEscala(alocacao);
            String mensagem = alocacao.getUsuario().getNome() + " recusou o convite para " + dados.get("titulo")
                    + " (" + dados.get("data") + ", " + dados.get("hora") + ", função " + dados.get("funcao") + ")."
                    + (alocacao.getJustificativa() == null ? "" : " Justificativa: " + alocacao.getJustificativa())
                    + " A vaga voltou para a escala: sorteie de novo ou escolha alguém à mão.";
            envio.avisarCoordenadores(alocacao.getParoquiaId(), alocacao.getVaga().getFuncao().getPastoral(),
                    "Convite recusado: " + dados.get("titulo"), mensagem, alocacao.getId());
        } catch (RuntimeException e) {
            log.error("Falha ao avisar recusa da alocação {}", evento.alocacaoId(), e);
        }
    }

    @Async(AsyncConfig.EXECUTOR_EMAIL)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void aoExpirar(ConviteExpiradoEvent evento) {
        try {
            Alocacao alocacao = alocacaoRepository.findParaEmail(evento.alocacaoId()).orElse(null);
            if (alocacao == null || !envio.registrarPrimeiroEnvio(alocacao, TipoNotificacao.AVISO_COORDENADOR_EXPIRADO)) {
                return;
            }
            Map<String, Object> dados = envio.variaveisDaEscala(alocacao);
            String mensagem = "O prazo do convite de " + alocacao.getUsuario().getNome() + " para " + dados.get("titulo")
                    + " (" + dados.get("data") + ", " + dados.get("hora") + ", função " + dados.get("funcao")
                    + ") venceu sem resposta. A vaga voltou para a escala: sorteie de novo ou escolha alguém à mão.";
            envio.avisarCoordenadores(alocacao.getParoquiaId(), alocacao.getVaga().getFuncao().getPastoral(),
                    "Convite expirado: " + dados.get("titulo"), mensagem, alocacao.getId());
        } catch (RuntimeException e) {
            log.error("Falha ao avisar expiração da alocação {}", evento.alocacaoId(), e);
        }
    }

    @Async(AsyncConfig.EXECUTOR_EMAIL)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void aoSubstituir(AlocacaoSubstituidaEvent evento) {
        try {
            Alocacao anterior = alocacaoRepository.findParaEmail(evento.alocacaoSubstituidaId()).orElse(null);
            if (anterior == null || !envio.registrarPrimeiroEnvio(anterior, TipoNotificacao.SUBSTITUICAO)) {
                return;
            }
            Map<String, Object> variaveis = envio.variaveisDaEscala(anterior);
            String assunto = "Você saiu da escala: " + variaveis.get("titulo") + " (" + variaveis.get("data") + ")";
            variaveis.put("assunto", assunto);
            envio.enviarSeguro(anterior.getUsuario().getEmail(), assunto, "substituicao", variaveis, anterior.getId());
        } catch (RuntimeException e) {
            log.error("Falha ao avisar substituição da alocação {}", evento.alocacaoSubstituidaId(), e);
        }
    }

    /**
     * Sem alocação para usar como chave, não há deduplicação: cada sorteio que deixa vaga aberta
     * avisa de novo, e isso é o que o coordenador espera ao pedir o sorteio outra vez.
     */
    @Async(AsyncConfig.EXECUTOR_EMAIL)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void aoFaltarElegiveis(VagaSemElegiveisEvent evento) {
        try {
            Vaga vaga = vagaRepository.findParaEmail(evento.vagaId()).orElse(null);
            if (vaga == null) {
                return;
            }
            Map<String, Object> dados = envio.dadosDaCelebracao(vaga.getCelebracao());
            String titulo = (String) dados.get("titulo");
            String mensagem = "Faltam " + evento.faltam() + " pessoa(s) para " + vaga.getFuncao().getNome()
                    + " em " + titulo + " (" + dados.get("data") + ", " + dados.get("hora")
                    + "). Não havia gente elegível suficiente: escale alguém à mão ou ajuste a escala.";
            envio.avisarCoordenadores(vaga.getParoquiaId(), vaga.getFuncao().getPastoral(),
                    "Vaga sem pessoas elegíveis: " + titulo, mensagem, null);
        } catch (RuntimeException e) {
            log.error("Falha ao avisar vaga {} sem elegíveis", evento.vagaId(), e);
        }
    }

    private boolean tokenAtual(Alocacao alocacao, String token) {
        return token != null && tokenConvite.hash(token).equals(alocacao.getTokenHash());
    }
}
