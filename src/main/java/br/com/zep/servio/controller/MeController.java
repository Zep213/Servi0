package br.com.zep.servio.controller;

import br.com.zep.servio.model.dto.EscalaDTOs.EscalaPessoalDTO;
import br.com.zep.servio.model.dto.MeResponseDTO;
import br.com.zep.servio.model.dto.MeUpdateDTO;
import br.com.zep.servio.model.dto.TrocaSenhaRequestDTO;
import br.com.zep.servio.service.MeService;
import br.com.zep.servio.service.leitura.LeituraEscalaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import br.com.zep.servio.service.FotoPerfilService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/me")
@RequiredArgsConstructor
public class MeController {

    private final MeService meService;
    private final FotoPerfilService fotoPerfilService;
    private final LeituraEscalaService leituraEscalaService;

    @GetMapping
    public MeResponseDTO me() {
        return meService.obter();
    }

    @PutMapping
    public MeResponseDTO atualizar(@Valid @RequestBody MeUpdateDTO request) {
        return meService.atualizar(request);
    }

    /** Foto de perfil (parte "arquivo"): JPEG ou PNG, até 2 MB; devolve o mesmo corpo do GET. */
    @PutMapping("/foto")
    public MeResponseDTO enviarFoto(@RequestPart(value = "arquivo", required = false) MultipartFile arquivo) {
        fotoPerfilService.salvar(arquivo);
        return meService.obter();
    }

    @DeleteMapping("/foto")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removerFoto() {
        fotoPerfilService.remover();
    }

    /** Escalas do próprio usuário logado; sem período, de hoje a +60 dias. */
    @GetMapping("/escalas")
    public List<EscalaPessoalDTO> escalas(@RequestParam(required = false) LocalDate de,
                                          @RequestParam(required = false) LocalDate ate) {
        return leituraEscalaService.minhasEscalas(de, ate);
    }

    @PostMapping("/senha")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void trocarSenha(@Valid @RequestBody TrocaSenhaRequestDTO request) {
        meService.trocarSenha(request);
    }
}
