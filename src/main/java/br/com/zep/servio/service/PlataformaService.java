package br.com.zep.servio.service;

import br.com.zep.servio.exception.ConflitoException;
import br.com.zep.servio.exception.RecursoNaoEncontradoException;
import br.com.zep.servio.model.Paroquia;
import br.com.zep.servio.model.Usuario;
import br.com.zep.servio.model.dto.CriarParoquiaRequestDTO;
import br.com.zep.servio.model.dto.ParoquiaPlataformaDTO;
import br.com.zep.servio.model.dto.ResumoPlataformaDTO;
import br.com.zep.servio.model.enumerated.Perfil;
import br.com.zep.servio.repository.CelebracaoRepository;
import br.com.zep.servio.repository.ParoquiaRepository;
import br.com.zep.servio.repository.PastoralRepository;
import br.com.zep.servio.repository.UsuarioRepository;
import br.com.zep.servio.security.UsuarioLogado;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Só ADMIN: visão de toda a plataforma (todas as paróquias) e o "assumir paróquia"
 * (Parte 2.2). Fora daqui, ADMIN opera dentro da paróquia via UsuarioLogado.paroquiaId(),
 * como qualquer outro perfil.
 */
@Service
@RequiredArgsConstructor
public class PlataformaService {

    private final ParoquiaRepository paroquiaRepository;
    private final UsuarioRepository usuarioRepository;
    private final PastoralRepository pastoralRepository;
    private final CelebracaoRepository celebracaoRepository;
    private final PasswordEncoder passwordEncoder;
    private final UsuarioLogado usuarioLogado;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public List<ParoquiaPlataformaDTO> paroquias() {
        return paroquiaRepository.findByActiveTrue().stream()
                .map(p -> new ParoquiaPlataformaDTO(
                        p.getId(), p.getNome(),
                        usuarioRepository.countByParoquiaIdAndActiveTrue(p.getId()),
                        pastoralRepository.countByParoquiaIdAndActiveTrue(p.getId())))
                .toList();
    }

    @Transactional
    public ParoquiaPlataformaDTO criarParoquia(CriarParoquiaRequestDTO request) {
        if (usuarioRepository.existsByEmailIgnoreCaseAndActiveTrueAndIdNot(request.padreEmail(), 0L)) {
            throw new ConflitoException("Já existe um usuário ativo com este e-mail");
        }

        Paroquia paroquia = new Paroquia();
        paroquia.setNome(request.nome());
        paroquia.setEmailContato(request.emailContato());
        paroquia = paroquiaRepository.save(paroquia);

        Usuario padre = new Usuario();
        padre.setNome(request.padreNome());
        padre.setEmail(request.padreEmail());
        padre.setSenha(passwordEncoder.encode(request.padreSenha()));
        padre.setPerfil(Perfil.PADRE);
        padre.setParoquiaId(paroquia.getId());
        usuarioRepository.save(padre);

        return new ParoquiaPlataformaDTO(paroquia.getId(), paroquia.getNome(), 1, 0);
    }

    @Transactional(readOnly = true)
    public ResumoPlataformaDTO resumo() {
        LocalDate hoje = LocalDate.now();
        return new ResumoPlataformaDTO(
                paroquiaRepository.findByActiveTrue().size(),
                usuarioRepository.count(),
                pastoralRepository.count(),
                celebracaoRepository.countByDataBetweenAndActiveTrue(hoje, hoje.plusDays(30)));
    }

    @Transactional
    public void assumir(Long paroquiaId) {
        Paroquia paroquia = paroquiaRepository.findById(paroquiaId)
                .filter(Paroquia::isActive)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Paroquia", paroquiaId));
        usuarioLogado.assumir(paroquia.getId());
        auditLogService.registrar("ADMIN_ASSUMIU_PAROQUIA", paroquia.getId(), usuarioAtor(), paroquia.getId(), null);
    }

    @Transactional
    public void sair() {
        Long assumida = usuarioLogado.paroquiaAssumida();
        usuarioLogado.sair();
        if (assumida != null) {
            auditLogService.registrar("ADMIN_SAIU_PAROQUIA", assumida, usuarioAtor(), assumida, null);
        }
    }

    private Usuario usuarioAtor() {
        return usuarioRepository.findById(usuarioLogado.id()).orElse(null);
    }
}
