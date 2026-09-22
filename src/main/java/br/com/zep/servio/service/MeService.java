package br.com.zep.servio.service;

import br.com.zep.servio.exception.RegraNegocioException;
import br.com.zep.servio.model.Usuario;
import br.com.zep.servio.model.dto.TrocaSenhaRequestDTO;
import br.com.zep.servio.repository.UsuarioRepository;
import br.com.zep.servio.security.SessaoService;
import br.com.zep.servio.security.UsuarioLogado;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MeService {

    private final UsuarioRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final UsuarioLogado usuarioLogado;
    private final SessaoService sessaoService;

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
}
