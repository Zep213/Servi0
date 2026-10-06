package br.com.zep.servio.service.escalacao;

import br.com.zep.servio.exception.ServioException;
import br.com.zep.servio.model.Alocacao;
import br.com.zep.servio.model.dto.ConfirmacaoDetalhesDTO;
import br.com.zep.servio.model.dto.ConfirmacaoRespostaDTO;
import br.com.zep.servio.model.enumerated.StatusConvite;
import br.com.zep.servio.repository.AlocacaoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

/** Convite pelo link do e-mail, sem login. Token inválido, usado ou de alocação inativa dão a mesma 404. */
@Service
@RequiredArgsConstructor
public class ConfirmacaoPublicaService {

    private static final String NAO_ENCONTRADO = "Convite não encontrado ou já respondido";

    private final AlocacaoRepository alocacaoRepository;
    private final TokenConvite tokenConvite;
    private final RespostaConviteService respostaConviteService;
    private final Clock clock;

    /** Não altera nada: um prazo vencido aparece como EXPIRADA, mas só a resposta grava isso. */
    @Transactional(readOnly = true)
    public ConfirmacaoDetalhesDTO detalhes(String token) {
        Alocacao alocacao = porToken(token);
        StatusConvite status = alocacao.getStatus();
        if (status == StatusConvite.PENDENTE && prazoVencido(alocacao)) {
            status = StatusConvite.EXPIRADA;
        }
        var celebracao = alocacao.getVaga().getCelebracao();
        var vaga = alocacao.getVaga();
        return new ConfirmacaoDetalhesDTO(
                primeiroNome(alocacao.getUsuario().getNome()),
                celebracao.getTitulo(),
                celebracao.getData(),
                celebracao.getHora(),
                vaga.getHorarioChegada(),
                vaga.getFuncao().getNome(),
                vaga.getObservacao(),
                alocacao.getDataLimiteResposta(),
                status);
    }

    /**
     * noRollbackFor: a expiração gravada abaixo precisa valer mesmo com o 410 sendo devolvido.
     */
    @Transactional(noRollbackFor = ConviteExpiradoException.class)
    public ConfirmacaoRespostaDTO responder(String token, boolean aceitar, String justificativa) {
        Alocacao alocacao = porToken(token);
        if (alocacao.getStatus() == StatusConvite.EXPIRADA) {
            throw new ConviteExpiradoException();
        }
        if (alocacao.getStatus() != StatusConvite.PENDENTE) {
            throw naoEncontrado();
        }
        if (justificativa != null && justificativa.length() > RespostaConviteService.TAMANHO_MAXIMO_JUSTIFICATIVA) {
            throw new ServioException("Justificativa muito longa", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        StatusConvite resultado = respostaConviteService.aplicar(alocacao, aceitar, justificativa);
        alocacaoRepository.save(alocacao);
        if (resultado == StatusConvite.EXPIRADA) {
            throw new ConviteExpiradoException();
        }
        return new ConfirmacaoRespostaDTO(resultado);
    }

    private Alocacao porToken(String token) {
        return alocacaoRepository.findByTokenHashAndActiveTrue(tokenConvite.hash(token))
                .orElseThrow(this::naoEncontrado);
    }

    private boolean prazoVencido(Alocacao alocacao) {
        LocalDateTime prazo = alocacao.getDataLimiteResposta();
        return prazo != null && LocalDateTime.now(clock).isAfter(prazo);
    }

    private ServioException naoEncontrado() {
        return new ServioException(NAO_ENCONTRADO, HttpStatus.NOT_FOUND);
    }

    /** Só o primeiro nome vai para a página pública: nada de sobrenome nem e-mail. */
    private static String primeiroNome(String nomeCompleto) {
        String limpo = nomeCompleto == null ? "" : nomeCompleto.trim();
        int espaco = limpo.indexOf(' ');
        return espaco < 0 ? limpo : limpo.substring(0, espaco);
    }
}
