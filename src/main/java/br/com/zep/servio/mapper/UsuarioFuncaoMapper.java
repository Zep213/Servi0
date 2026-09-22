package br.com.zep.servio.mapper;

import br.com.zep.servio.model.UsuarioFuncao;
import br.com.zep.servio.model.dto.UsuarioFuncaoRequestDTO;
import br.com.zep.servio.model.dto.UsuarioFuncaoResponseDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface UsuarioFuncaoMapper {

    @Mapping(source = "usuario.id", target = "usuarioId")
    @Mapping(source = "funcao.id", target = "funcaoId")
    UsuarioFuncaoResponseDTO toResponse(UsuarioFuncao entity);

    UsuarioFuncao toEntity(UsuarioFuncaoRequestDTO request);

    void updateEntity(UsuarioFuncaoRequestDTO request, @MappingTarget UsuarioFuncao entity);
}
