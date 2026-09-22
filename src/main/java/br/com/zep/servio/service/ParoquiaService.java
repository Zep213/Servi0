package br.com.zep.servio.service;

import br.com.zep.servio.exception.RecursoNaoEncontradoException;
import br.com.zep.servio.mapper.ParoquiaMapper;
import br.com.zep.servio.model.Paroquia;
import br.com.zep.servio.model.dto.ParoquiaRequestDTO;
import br.com.zep.servio.model.dto.ParoquiaResponseDTO;
import br.com.zep.servio.repository.ParoquiaRepository;
import br.com.zep.servio.security.UsuarioLogado;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ParoquiaService {

    private final ParoquiaRepository repository;
    private final ParoquiaMapper mapper;
    private final UsuarioLogado usuarioLogado;

    @Transactional(readOnly = true)
    public ParoquiaResponseDTO minha() {
        return mapper.toResponse(obterMinha());
    }

    @Transactional
    public ParoquiaResponseDTO atualizarMinha(ParoquiaRequestDTO request) {
        Paroquia paroquia = obterMinha();
        mapper.updateEntity(request, paroquia);
        return mapper.toResponse(repository.save(paroquia));
    }

    private Paroquia obterMinha() {
        Long id = usuarioLogado.paroquiaId();
        return repository.findById(id)
                .filter(Paroquia::isActive)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Paroquia", id));
    }
}
