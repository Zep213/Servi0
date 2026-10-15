package br.com.zep.servio.controller;

import br.com.zep.servio.model.UsuarioFoto;
import br.com.zep.servio.service.FotoPerfilService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;

import java.time.Duration;
import java.util.Optional;

/** Foto de perfil de alguém da mesma paróquia. Sem foto, de outra paróquia ou inativo: 404. */
@RestController
@RequestMapping("/api/fotos")
@RequiredArgsConstructor
public class FotoController {

    private final FotoPerfilService fotoPerfilService;

    @GetMapping("/{usuarioId}")
    public ResponseEntity<byte[]> foto(@PathVariable Long usuarioId, WebRequest request) {
        Optional<UsuarioFoto> foto = fotoPerfilService.obterParaExibir(usuarioId);
        if (foto.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        String etag = FotoPerfilService.etagDe(foto.get().getAtualizadoEm());
        if (request.checkNotModified(etag)) {
            return null;                                  // 304 já preenchido pelo Spring
        }
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePrivate())
                .eTag(etag)
                .header("X-Content-Type-Options", "nosniff")
                .body(foto.get().getConteudo());
    }
}
