package br.com.zep.servio.service.notification;

import br.com.zep.servio.config.AsyncConfig;
import br.com.zep.servio.model.Alocacao;
import br.com.zep.servio.model.Celebracao;
import br.com.zep.servio.model.NotificacaoEnviada;
import br.com.zep.servio.model.Pastoral;
import br.com.zep.servio.model.Usuario;
import br.com.zep.servio.model.Vaga;
import br.com.zep.servio.model.enumerated.PapelPastoral;
import br.com.zep.servio.model.enumerated.StatusConvite;
import br.com.zep.servio.model.enumerated.TipoCelebracao;
import br.com.zep.servio.model.enumerated.TipoNotificacao;
import br.com.zep.servio.repository.AlocacaoRepository;
import br.com.zep.servio.repository.NotificacaoEnviadaRepository;
import br.com.zep.servio.repository.UsuarioPastoralRepository;
import br.com.zep.servio.repository.VagaRepository;
import br.com.zep.servio.service.escalacao.TokenConvite;
import br.com.zep.servio.service.escalacao.evento.AlocacaoSubstituidaEvent;
import br.com.zep.servio.service.escalacao.evento.ConviteCriadoEvent;
import br.com.zep.servio.service.escalacao.evento.ConviteRecusadoEvent;
import br.com.zep.servio.service.escalacao.evento.VagaSemElegiveisEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * E-mails da escalação (Etapa 6, Parte 4). Cada aviso roda depois do commit, numa thread do
 * executor de e-mail: se o SMTP falhar, loga e a escala continua como foi gravada.
 *
 * <p>Deduplicação: antes de enviar, grava uma linha em notificacao_enviada (índice único em
 * alocacao+tipo). Se a linha já existe, não envia. CONVITE_REENVIO não passa por aqui: quem grava
 * esse registro é o próprio ConviteService, na transação do reenvio.
 *
 * <p>Nunca loga o token: o link do convite só sai no corpo do e-mail.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificacaoListener {

    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter PRAZO = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final Notificador notificador;
    private final AlocacaoRepository alocacaoRepository;
    private final VagaRepository vagaRepository;
    private final UsuarioPastoralRepository usuarioPastoralRepository;
    private final NotificacaoEnviadaRepository notificacaoEnviadaRepository;
    private final TokenConvite tokenConvite;
    private final PlatformTransactionManager transacoes;
    private final Clock clock;

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
            if (evento.tipo() == TipoNotificacao.CONVITE && !registrarPrimeiroEnvio(alocacao, evento.tipo())) {
                return;
            }
            Map<String, Object> variaveis = variaveisDaEscala(alocacao);
            variaveis.put("link", frontUrl + "/convite#" + evento.token());
            String assunto = (evento.tipo() == TipoNotificacao.CONVITE_REENVIO ? "Reenvio do convite: " : "Convite: ")
                    + variaveis.get("titulo") + " (" + variaveis.get("data") + ")";
            variaveis.put("assunto", assunto);
            enviarSeguro(alocacao.getUsuario().getEmail(), assunto, "convite", variaveis, alocacao.getId());
        } catch (RuntimeException e) {
            log.error("Falha ao preparar o convite da alocação {}", evento.alocacaoId(), e);
        }
    }

    @Async(AsyncConfig.EXECUTOR_EMAIL)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void aoRecusar(ConviteRecusadoEvent evento) {
        try {
            Alocacao alocacao = alocacaoRepository.findParaEmail(evento.alocacaoId()).orElse(null);
            if (alocacao == null || !registrarPrimeiroEnvio(alocacao, TipoNotificacao.AVISO_COORDENADOR_RECUSA)) {
                return;
            }
            Map<String, Object> dados = variaveisDaEscala(alocacao);
            String mensagem = alocacao.getUsuario().getNome() + " recusou o convite para " + dados.get("titulo")
                    + " (" + dados.get("data") + ", " + dados.get("hora") + ", função " + dados.get("funcao") + ")."
                    + (alocacao.getJustificativa() == null ? "" : " Justificativa: " + alocacao.getJustificativa())
                    + " A vaga voltou para a escala: sorteie de novo ou escolha alguém à mão.";
            avisarCoordenadores(alocacao.getParoquiaId(), alocacao.getVaga().getFuncao().getPastoral(),
                    "Convite recusado: " + dados.get("titulo"), mensagem, alocacao.getId());
        } catch (RuntimeException e) {
            log.error("Falha ao avisar recusa da alocação {}", evento.alocacaoId(), e);
        }
    }

    @Async(AsyncConfig.EXECUTOR_EMAIL)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void aoSubstituir(AlocacaoSubstituidaEvent evento) {
        try {
            Alocacao anterior = alocacaoRepository.findParaEmail(evento.alocacaoSubstituidaId()).orElse(null);
            if (anterior == null || !registrarPrimeiroEnvio(anterior, TipoNotificacao.SUBSTITUICAO)) {
                return;
            }
            Map<String, Object> variaveis = variaveisDaEscala(anterior);
            String assunto = "Você saiu da escala: " + variaveis.get("titulo") + " (" + variaveis.get("data") + ")";
            variaveis.put("assunto", assunto);
            enviarSeguro(anterior.getUsuario().getEmail(), assunto, "substituicao", variaveis, anterior.getId());
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
            Map<String, Object> dados = dadosDaCelebracao(vaga.getCelebracao());
            String titulo = (String) dados.get("titulo");
            String mensagem = "Faltam " + evento.faltam() + " pessoa(s) para " + vaga.getFuncao().getNome()
                    + " em " + titulo + " (" + dados.get("data") + ", " + dados.get("hora")
                    + "). Não havia gente elegível suficiente: escale alguém à mão ou ajuste a escala.";
            avisarCoordenadores(vaga.getParoquiaId(), vaga.getFuncao().getPastoral(),
                    "Vaga sem pessoas elegíveis: " + titulo, mensagem, null);
        } catch (RuntimeException e) {
            log.error("Falha ao avisar vaga {} sem elegíveis", evento.vagaId(), e);
        }
    }

    // ---------------------------------------------------------------- apoio

    private void avisarCoordenadores(Long paroquiaId, Pastoral pastoral,
                                     String assunto, String mensagem, Long alocacaoId) {
        List<Usuario> coordenadores = usuarioPastoralRepository.findUsuariosPorPapel(
                pastoral.getId(), paroquiaId, PapelPastoral.COORDENADOR);
        for (Usuario coordenador : coordenadores) {
            Map<String, Object> variaveis = new HashMap<>();
            variaveis.put("nome", primeiroNome(coordenador.getNome()));
            variaveis.put("mensagem", mensagem);
            variaveis.put("pastoral", pastoral.getNome());
            variaveis.put("assunto", assunto);
            enviarSeguro(coordenador.getEmail(), assunto, "aviso-coordenador", variaveis, alocacaoId);
        }
    }

    /** Grava o envio antes de mandar. Retorna false se ele já existia (outro evento ou thread ganhou). */
    private boolean registrarPrimeiroEnvio(Alocacao alocacao, TipoNotificacao tipo) {
        TransactionTemplate tx = new TransactionTemplate(transacoes);
        try {
            tx.executeWithoutResult(status -> {
                NotificacaoEnviada envio = new NotificacaoEnviada();
                envio.setParoquiaId(alocacao.getParoquiaId());
                envio.setAlocacao(alocacaoRepository.getReferenceById(alocacao.getId()));
                envio.setTipo(tipo);
                envio.setEnviadaEm(LocalDateTime.now(clock));
                notificacaoEnviadaRepository.saveAndFlush(envio);
            });
            return true;
        } catch (DataIntegrityViolationException jaExiste) {
            return false;
        }
    }

    private void enviarSeguro(String destinatario, String assunto, String template,
                              Map<String, Object> variaveis, Long alocacaoId) {
        try {
            notificador.enviar(destinatario, assunto, template, variaveis);
        } catch (RuntimeException e) {
            log.error("Falha ao enviar e-mail '{}' da alocação {}", template, alocacaoId, e);
        }
    }

    private boolean tokenAtual(Alocacao alocacao, String token) {
        return token != null && tokenConvite.hash(token).equals(alocacao.getTokenHash());
    }

    /** Variáveis que toda mensagem sobre uma escala usa: pessoa, celebração, função e prazo. */
    private Map<String, Object> variaveisDaEscala(Alocacao alocacao) {
        Vaga vaga = alocacao.getVaga();
        Map<String, Object> variaveis = dadosDaCelebracao(vaga.getCelebracao());
        variaveis.put("nome", primeiroNome(alocacao.getUsuario().getNome()));
        variaveis.put("funcao", vaga.getFuncao().getNome());
        variaveis.put("chegada", vaga.getHorarioChegada() == null ? null : vaga.getHorarioChegada().format(HORA));
        variaveis.put("observacao", vaga.getObservacao() == null || vaga.getObservacao().isBlank() ? null : vaga.getObservacao());
        variaveis.put("prazo", alocacao.getDataLimiteResposta() == null ? "" : alocacao.getDataLimiteResposta().format(PRAZO));
        return variaveis;
    }

    private Map<String, Object> dadosDaCelebracao(Celebracao celebracao) {
        Map<String, Object> dados = new HashMap<>();
        dados.put("titulo", titulo(celebracao));
        dados.put("data", formatar(celebracao.getData(), DATA));
        dados.put("hora", formatar(celebracao.getHora(), HORA));
        return dados;
    }

    private static String titulo(Celebracao celebracao) {
        if (celebracao.getTitulo() != null && !celebracao.getTitulo().isBlank()) {
            return celebracao.getTitulo();
        }
        return celebracao.getTipo() == TipoCelebracao.MISSA_DOMINICAL ? "Missa dominical" : "Festividade";
    }

    private static String formatar(LocalDate data, DateTimeFormatter formato) {
        return data == null ? "" : data.format(formato);
    }

    private static String formatar(LocalTime hora, DateTimeFormatter formato) {
        return hora == null ? "" : hora.format(formato);
    }

    private static String primeiroNome(String nome) {
        if (nome == null || nome.isBlank()) {
            return "";
        }
        return nome.trim().split("\\s+")[0];
    }
}
