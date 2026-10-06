package br.com.zep.servio.service.notification;

import br.com.zep.servio.model.Alocacao;
import br.com.zep.servio.model.Celebracao;
import br.com.zep.servio.model.NotificacaoEnviada;
import br.com.zep.servio.model.Pastoral;
import br.com.zep.servio.model.Usuario;
import br.com.zep.servio.model.Vaga;
import br.com.zep.servio.model.enumerated.PapelPastoral;
import br.com.zep.servio.model.enumerated.TipoCelebracao;
import br.com.zep.servio.model.enumerated.TipoNotificacao;
import br.com.zep.servio.repository.AlocacaoRepository;
import br.com.zep.servio.repository.NotificacaoEnviadaRepository;
import br.com.zep.servio.repository.UsuarioPastoralRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Peças comuns a todo e-mail da escalação: deduplicação em notificacao_enviada, envio que nunca
 * derruba o chamador, textos e variáveis. Usado pelo NotificacaoListener (eventos depois do commit)
 * e pelos jobs (expiração e lembretes). Nada aqui usa usuário logado: a paróquia vem da entidade.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EnvioEscalacao {

    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter PRAZO = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final Notificador notificador;
    private final AlocacaoRepository alocacaoRepository;
    private final NotificacaoEnviadaRepository notificacaoEnviadaRepository;
    private final UsuarioPastoralRepository usuarioPastoralRepository;
    private final PlatformTransactionManager transacoes;
    private final Clock clock;

    /** Sem alteração extra na mesma transação do registro. */
    public boolean registrarPrimeiroEnvio(Alocacao alocacao, TipoNotificacao tipo) {
        return registrarPrimeiroEnvio(alocacao, tipo, gerenciada -> { });
    }

    /**
     * Grava o envio antes de mandar. Retorna false se ele já existia (outro evento, outro job ou
     * outra thread ganhou o índice único). {@code naMesmaTransacao} roda junto com o registro: se o
     * registro falha, a mudança também volta (o lembrete usa isso para trocar o token).
     */
    public boolean registrarPrimeiroEnvio(Alocacao alocacao, TipoNotificacao tipo,
                                          Consumer<Alocacao> naMesmaTransacao) {
        TransactionTemplate tx = new TransactionTemplate(transacoes);
        try {
            tx.executeWithoutResult(status -> {
                Alocacao gerenciada = alocacaoRepository.findById(alocacao.getId()).orElseThrow();
                naMesmaTransacao.accept(gerenciada);
                NotificacaoEnviada envio = new NotificacaoEnviada();
                envio.setParoquiaId(gerenciada.getParoquiaId());
                envio.setAlocacao(gerenciada);
                envio.setTipo(tipo);
                envio.setEnviadaEm(LocalDateTime.now(clock));
                notificacaoEnviadaRepository.saveAndFlush(envio);
            });
            return true;
        } catch (DataIntegrityViolationException jaExiste) {
            return false;
        }
    }

    public void enviarSeguro(String destinatario, String assunto, String template,
                             Map<String, Object> variaveis, Long alocacaoId) {
        try {
            notificador.enviar(destinatario, assunto, template, variaveis);
        } catch (RuntimeException e) {
            // SMTP caiu não desfaz a escala: loga (sem token, sem corpo do e-mail) e segue
            log.error("Falha ao enviar e-mail '{}' da alocação {}", template, alocacaoId, e);
        }
    }

    /** Aviso para as pessoas com papel COORDENADOR da pastoral, na mesma paróquia. */
    public void avisarCoordenadores(Long paroquiaId, Pastoral pastoral, String assunto,
                                    String mensagem, Long alocacaoId) {
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

    /** Variáveis que toda mensagem sobre uma escala usa: pessoa, celebração, função e prazo. */
    public Map<String, Object> variaveisDaEscala(Alocacao alocacao) {
        Vaga vaga = alocacao.getVaga();
        Map<String, Object> variaveis = dadosDaCelebracao(vaga.getCelebracao());
        variaveis.put("nome", primeiroNome(alocacao.getUsuario().getNome()));
        variaveis.put("funcao", vaga.getFuncao().getNome());
        variaveis.put("chegada", vaga.getHorarioChegada() == null ? null : vaga.getHorarioChegada().format(HORA));
        variaveis.put("observacao", vaga.getObservacao() == null || vaga.getObservacao().isBlank() ? null : vaga.getObservacao());
        variaveis.put("prazo", alocacao.getDataLimiteResposta() == null ? "" : alocacao.getDataLimiteResposta().format(PRAZO));
        return variaveis;
    }

    public Map<String, Object> dadosDaCelebracao(Celebracao celebracao) {
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

    public static String primeiroNome(String nome) {
        if (nome == null || nome.isBlank()) {
            return "";
        }
        return nome.trim().split("\\s+")[0];
    }
}
