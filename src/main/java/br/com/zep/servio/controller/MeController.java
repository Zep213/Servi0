package br.com.zep.servio.controller;

import br.com.zep.servio.model.dto.MeResponseDTO;
import br.com.zep.servio.model.dto.TrocaSenhaRequestDTO;
import br.com.zep.servio.security.UsuarioLogado;
import br.com.zep.servio.security.UsuarioPrincipal;
import br.com.zep.servio.service.MeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me")
@RequiredArgsConstructor
public class MeController {

    private final UsuarioLogado usuarioLogado;
    private final MeService meService;

    @GetMapping
    public MeResponseDTO me() {
        UsuarioPrincipal usuario = usuarioLogado.get();
        return new MeResponseDTO(
                usuario.getId(),
                usuario.getNome(),
                usuario.getEmail(),
                usuario.getPerfil(),
                usuario.getParoquiaId());
    }

    @PostMapping("/senha")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void trocarSenha(@Valid @RequestBody TrocaSenhaRequestDTO request) {
        meService.trocarSenha(request);
    }
}
