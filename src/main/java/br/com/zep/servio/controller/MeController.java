package br.com.zep.servio.controller;

import br.com.zep.servio.model.dto.MeResponseDTO;
import br.com.zep.servio.security.UsuarioLogado;
import br.com.zep.servio.security.UsuarioPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me")
@RequiredArgsConstructor
public class MeController {

    private final UsuarioLogado usuarioLogado;

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
}
