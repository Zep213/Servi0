package br.com.zep.servio.service;

import br.com.zep.servio.exception.ConflitoException;
import br.com.zep.servio.exception.RegraNegocioException;
import br.com.zep.servio.exception.ServioException;
import br.com.zep.servio.model.Usuario;
import br.com.zep.servio.model.dto.MeResponseDTO;
import br.com.zep.servio.model.dto.MeUpdateDTO;
import br.com.zep.servio.model.dto.PastoralPapelDTO;
import br.com.zep.servio.model.dto.TrocaSenhaRequestDTO;
import br.com.zep.servio.repository.UsuarioPastoralRepository;
import br.com.zep.servio.repository.UsuarioRepository;
import br.com.zep.servio.security.SessaoService;
import br.com.zep.servio.security.TentativasLogin;
import br.com.zep.servio.security.UsuarioLogado;
import br.com.zep.servio.service.escalacao.PainelService;
import br.com.zep.servio.util.Telefone;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MeService {

    private final UsuarioRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final UsuarioLogado usuarioLogado;
    private final SessaoService sessaoService;
    private final UsuarioPastoralRepository usuarioPastoralRepository;
    private final PainelService painelService;
    private final AuditLogService auditLogService;
    private final TentativasLogin tentativas;
    private final FotoPerfilService fotoPerfilService;

    /** Depois de 3 pulos o front para de pedir o telefone. */
    static final int MAX_PULOS_TELEFONE = 3;
    static final String MSG_TELEFONE_EM_USO = "Este telefone não pode ser usado";

    @Transactional(readOnly = true)
    public MeResponseDTO obter() {
        return montar(repository.findById(usuarioLogado.id()).orElseThrow());
    }

    /**
     * Só altera o que vier. Telefone vazio ou nulo é ignorado (apagar fica para depois). O telefone de
     * contas antigas pode ser pulado até MAX_PULOS_TELEFONE vezes, com lembrete (pedirTelefone).
     */
    @Transactional
    public MeResponseDTO atualizar(MeUpdateDTO request) {
        Usuario usuario = repository.findById(usuarioLogado.id()).orElseThrow();

        if (request.telefone() != null && !request.telefone().isBlank()) {
            // limite por usuário: sem ele, 409 x 200 serviria para varrer quais números existem no sistema
            String chave = "tel:" + usuario.getId();
            if (tentativas.bloqueado(chave)) {
                throw new ServioException("Muitas tentativas. Aguarde alguns minutos e tente de novo.",
                        HttpStatus.TOO_MANY_REQUESTS);
            }
            tentativas.registrarFalha(chave);
            String novo = Telefone.normalizar(request.telefone())
                    .orElseThrow(() -> new ServioException("Telefone inválido", HttpStatus.BAD_REQUEST));
            if (!novo.equals(usuario.getTelefone())) {
                if (repository.existsByTelefoneAndActiveTrueAndIdNot(novo, usuario.getId())) {
                    throw new ConflitoException(MSG_TELEFONE_EM_USO);
                }
                usuario.setTelefone(novo);
                try {
                    repository.saveAndFlush(usuario);    // duas pessoas ao mesmo tempo: o índice único decide
                } catch (DataIntegrityViolationException e) {
                    throw new ConflitoException(MSG_TELEFONE_EM_USO);
                }
                auditLogService.registrar("TELEFONE_ALTERADO", usuario.getId(), usuario, usuario.getParoquiaId(),
                        "Telefone definido: " + Telefone.mascarar(novo));
            }
        }
        if (request.aparencia() != null) {
            usuario.setAparencia(request.aparencia());
        }
        if (Boolean.TRUE.equals(request.pularTelefone()) && usuario.getTelefone() == null) {
            usuario.setTelefonePulos((short) Math.min(MAX_PULOS_TELEFONE, usuario.getTelefonePulos() + 1));
        }
        return montar(repository.save(usuario));
    }

    private MeResponseDTO montar(Usuario usuario) {
        boolean pedirTelefone = usuario.getTelefone() == null && usuario.getTelefonePulos() < MAX_PULOS_TELEFONE;
        return new MeResponseDTO(usuario.getId(), usuario.getNome(), usuario.getEmail(), usuario.getPerfil(),
                usuario.getParoquiaId(), usuario.getTelefone(), usuario.getAparencia(), pedirTelefone,
                fotoPerfilService.fotoUrl(usuario.getId()),
                pastorais(usuario));
    }

    @Transactional
    public void trocarSenha(TrocaSenhaRequestDTO request) {
        Usuario usuario = repository.findById(usuarioLogado.id()).orElseThrow();

        if (!passwordEncoder.matches(request.senhaAtual(), usuario.getSenha())) {
            throw new RegraNegocioException("Senha atual incorreta");
        }
        usuario.setSenha(passwordEncoder.encode(request.senhaNova()));
        repository.save(usuario);

        // derruba todas as sessões, inclusive a atual: o usuário faz login de novo
        sessaoService.encerrarTodas(usuario.getEmail());
    }

    /** Pastorais e papéis do usuário (Etapa 6, Parte 6). Só leitura. */
    @Transactional(readOnly = true)
    public List<PastoralPapelDTO> pastorais(Usuario usuario) {
        return usuarioPastoralRepository.participacoesComPastoral(usuario.getId()).stream()
                .map(up -> new PastoralPapelDTO(up.getPastoral().getId(), up.getPastoral().getNome(), up.getPapel(),
                        painelService.contarPendencias(up.getPastoral().getId(),
                                PainelService.veOPainel(usuario.getPerfil(), up.getPapel()))))
                .toList();
    }
}
