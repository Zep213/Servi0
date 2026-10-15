package br.com.zep.servio.service;

import br.com.zep.servio.exception.ConflitoException;
import br.com.zep.servio.exception.ServioException;
import br.com.zep.servio.model.Usuario;
import br.com.zep.servio.model.dto.MeResponseDTO;
import br.com.zep.servio.model.dto.MeUpdateDTO;
import br.com.zep.servio.model.enumerated.Aparencia;
import br.com.zep.servio.model.enumerated.Perfil;
import br.com.zep.servio.repository.UsuarioPastoralRepository;
import br.com.zep.servio.repository.UsuarioRepository;
import br.com.zep.servio.security.SessaoService;
import br.com.zep.servio.security.TentativasLogin;
import br.com.zep.servio.security.UsuarioLogado;
import br.com.zep.servio.service.escalacao.PainelService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MeServiceTest {

    private final UsuarioRepository repository = mock(UsuarioRepository.class);
    private final UsuarioLogado usuarioLogado = mock(UsuarioLogado.class);
    private final UsuarioPastoralRepository usuarioPastoralRepository = mock(UsuarioPastoralRepository.class);
    private final AuditLogService auditLogService = mock(AuditLogService.class);
    private MeService service;
    private Usuario usuario;

    @BeforeEach
    void setUp() {
        service = new MeService(repository, mock(PasswordEncoder.class), usuarioLogado, mock(SessaoService.class),
                usuarioPastoralRepository, mock(PainelService.class), auditLogService, new TentativasLogin(), mock(br.com.zep.servio.service.FotoPerfilService.class));
        usuario = new Usuario();
        usuario.setId(7L);
        usuario.setParoquiaId(1L);
        usuario.setNome("Maria");
        usuario.setEmail("maria@servio.dev");
        usuario.setPerfil(Perfil.SERVIDOR);
        when(usuarioLogado.id()).thenReturn(7L);
        when(repository.findById(7L)).thenReturn(java.util.Optional.of(usuario));
        when(repository.save(any(Usuario.class))).thenAnswer(i -> i.getArgument(0));
        when(repository.saveAndFlush(any(Usuario.class))).thenAnswer(i -> i.getArgument(0));
        when(usuarioPastoralRepository.participacoesComPastoral(anyLong())).thenReturn(List.of());
    }

    @Test
    void contaSemTelefoneEPoucosPulosPedeOTelefone() {
        assertTrue(service.obter().pedirTelefone());
    }

    @Test
    void telefoneEhNormalizadoEAuditadoMascarado() {
        MeResponseDTO resposta = service.atualizar(new MeUpdateDTO("(83) 9 8812-4407", null, null));

        assertEquals("+5583988124407", resposta.telefone());
        assertFalse(resposta.pedirTelefone());
        verify(auditLogService).registrar("TELEFONE_ALTERADO", 7L, usuario, 1L, "Telefone definido: +55839****4407");
    }

    @Test
    void telefoneInvalidoLancaRegraDeNegocio() {
        ServioException erro = assertThrows(ServioException.class,
                () -> service.atualizar(new MeUpdateDTO("12345", null, null)));
        assertEquals("Telefone inválido", erro.getMessage());
        assertEquals(HttpStatus.BAD_REQUEST, erro.getStatus());
    }

    @Test
    void telefoneDeOutraContaLancaConflitoGenerico() {
        when(repository.existsByTelefoneAndActiveTrueAndIdNot("+5583988124407", 7L)).thenReturn(true);

        ConflitoException erro = assertThrows(ConflitoException.class,
                () -> service.atualizar(new MeUpdateDTO("83988124407", null, null)));
        assertEquals("Este telefone não pode ser usado", erro.getMessage());
    }

    @Test
    void violacaoDoIndiceUnicoTambemVira409() {
        when(repository.saveAndFlush(any(Usuario.class))).thenThrow(new DataIntegrityViolationException("uq"));

        assertThrows(ConflitoException.class, () -> service.atualizar(new MeUpdateDTO("83988124407", null, null)));
    }

    @Test
    void telefoneVazioOuNuloEhIgnorado() {
        usuario.setTelefone("+5583988124407");

        assertEquals("+5583988124407", service.atualizar(new MeUpdateDTO("", null, null)).telefone());
        assertEquals("+5583988124407", service.atualizar(new MeUpdateDTO(null, Aparencia.CLARO, null)).telefone());
        verify(repository, never()).saveAndFlush(any());
        verify(auditLogService, never()).registrar(anyString(), anyLong(), any(), anyLong(), anyString());
    }

    @Test
    void tresPulosParamDePedirESoContamSemTelefone() {
        MeUpdateDTO pular = new MeUpdateDTO(null, null, true);
        assertTrue(service.atualizar(pular).pedirTelefone());
        assertTrue(service.atualizar(pular).pedirTelefone());
        assertFalse(service.atualizar(pular).pedirTelefone());

        usuario.setTelefonePulos((short) 0);
        usuario.setTelefone("+5583988124407");
        service.atualizar(pular);
        assertEquals(0, usuario.getTelefonePulos());
    }

    @Test
    void sextaTentativaDeTelefoneNoPeriodoDa429() {
        MeUpdateDTO tentativa = new MeUpdateDTO("83988124407", null, null);
        when(repository.existsByTelefoneAndActiveTrueAndIdNot("+5583988124407", 7L)).thenReturn(true);
        for (int i = 0; i < TentativasLogin.MAX_FALHAS; i++) {
            assertThrows(ConflitoException.class, () -> service.atualizar(tentativa));
        }

        ServioException erro = assertThrows(ServioException.class, () -> service.atualizar(tentativa));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, erro.getStatus());
    }

    @Test
    void pulosTemTeto() {
        usuario.setTelefonePulos((short) 3);
        service.atualizar(new MeUpdateDTO(null, null, true));
        assertEquals(3, usuario.getTelefonePulos());
    }

    @Test
    void aparenciaPersiste() {
        assertEquals(Aparencia.ESCURO, service.atualizar(new MeUpdateDTO(null, Aparencia.ESCURO, null)).aparencia());
    }
}
