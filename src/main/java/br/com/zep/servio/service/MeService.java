package br.com.zep.servio.service;

import br.com.zep.servio.exception.RegraNegocioException;
import br.com.zep.servio.model.Usuario;
import br.com.zep.servio.model.dto.PastoralPapelDTO;
import br.com.zep.servio.model.dto.TrocaSenhaRequestDTO;
import br.com.zep.servio.repository.UsuarioPastoralRepository;
import br.com.zep.servio.repository.UsuarioRepository;
import br.com.zep.servio.security.SessaoService;
import br.com.zep.servio.security.UsuarioLogado;
import lombok.RequiredArgsConstructor;
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
    public List<PastoralPapelDTO> pastorais(Long usuarioId) {
        return usuarioPastoralRepository.participacoesComPastoral(usuarioId).stream()
                .map(up -> new PastoralPapelDTO(up.getPastoral().getId(), up.getPastoral().getNome(), up.getPapel()))
                .toList();
    }
}
