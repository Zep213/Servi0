package br.com.zep.servio.pastoral;

import br.com.zep.servio.model.Alocacao;
import br.com.zep.servio.model.Celebracao;
import br.com.zep.servio.model.Comunidade;
import br.com.zep.servio.model.Funcao;
import br.com.zep.servio.model.Paroquia;
import br.com.zep.servio.model.Usuario;
import br.com.zep.servio.model.Vaga;
import br.com.zep.servio.model.enumerated.StatusConvite;
import br.com.zep.servio.model.enumerated.TipoCelebracao;
import br.com.zep.servio.repository.AlocacaoRepository;
import br.com.zep.servio.repository.CelebracaoRepository;
import br.com.zep.servio.repository.ComunidadeRepository;
import br.com.zep.servio.repository.VagaRepository;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;

/** Montagem de celebrações, vagas e alocações para os testes de escalação (direto no repositório). */
public class CenarioEscalacao {

    private final ComunidadeRepository comunidadeRepository;
    private final CelebracaoRepository celebracaoRepository;
    private final VagaRepository vagaRepository;
    private final AlocacaoRepository alocacaoRepository;

    public CenarioEscalacao(ComunidadeRepository comunidadeRepository, CelebracaoRepository celebracaoRepository,
                            VagaRepository vagaRepository, AlocacaoRepository alocacaoRepository) {
        this.comunidadeRepository = comunidadeRepository;
        this.celebracaoRepository = celebracaoRepository;
        this.vagaRepository = vagaRepository;
        this.alocacaoRepository = alocacaoRepository;
    }

    /** Domingo daqui a N semanas (N >= 1): domingos de missa dominical para as regras de intervalo. */
    public static LocalDate domingo(int semanasAFrente) {
        LocalDate data = LocalDate.now().plusWeeks(semanasAFrente);
        while (data.getDayOfWeek() != DayOfWeek.SUNDAY) {
            data = data.plusDays(1);
        }
        return data;
    }

    public Comunidade comunidade(Paroquia paroquia) {
        Comunidade comunidade = new Comunidade();
        comunidade.setNome("Comunidade " + System.nanoTime());
        comunidade.setParoquiaId(paroquia.getId());
        return comunidadeRepository.save(comunidade);
    }

    public Celebracao celebracao(Paroquia paroquia, Comunidade comunidade, LocalDate data, TipoCelebracao tipo) {
        Celebracao celebracao = new Celebracao();
        celebracao.setComunidade(comunidade);
        celebracao.setData(data);
        celebracao.setHora(LocalTime.of(10, 0));
        celebracao.setTipo(tipo);
        celebracao.setParoquiaId(paroquia.getId());
        return celebracaoRepository.save(celebracao);
    }

    public Vaga vaga(Paroquia paroquia, Celebracao celebracao, Funcao funcao, Integer quantidade) {
        Vaga vaga = new Vaga();
        vaga.setCelebracao(celebracao);
        vaga.setFuncao(funcao);
        vaga.setQuantidade(quantidade);
        vaga.setParoquiaId(paroquia.getId());
        return vagaRepository.save(vaga);
    }

    public Alocacao alocacao(Paroquia paroquia, Vaga vaga, Usuario usuario, StatusConvite status) {
        Alocacao alocacao = new Alocacao();
        alocacao.setVaga(vaga);
        alocacao.setUsuario(usuario);
        alocacao.setStatus(status);
        alocacao.setParoquiaId(paroquia.getId());
        return alocacaoRepository.save(alocacao);
    }
}
