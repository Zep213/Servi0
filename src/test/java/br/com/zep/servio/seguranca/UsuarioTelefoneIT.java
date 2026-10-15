package br.com.zep.servio.seguranca;

import br.com.zep.servio.model.Paroquia;
import br.com.zep.servio.model.Usuario;
import br.com.zep.servio.model.enumerated.Aparencia;
import br.com.zep.servio.model.enumerated.Perfil;
import br.com.zep.servio.repository.ParoquiaRepository;
import br.com.zep.servio.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** V16: defaults, CHECKs e unicidade do telefone entre contas ativas. */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
@Transactional
class UsuarioTelefoneIT {

    @Autowired
    ParoquiaRepository paroquiaRepository;
    @Autowired
    UsuarioRepository usuarioRepository;
    @Autowired
    PasswordEncoder passwordEncoder;

    private Usuario novo(Paroquia p, String email) {
        return ApoioTeste.criarUsuario(usuarioRepository, passwordEncoder, p.getId(), email, "senha12345", Perfil.SERVIDOR);
    }

    @Test
    void contaNovaNasceSemTelefoneEComAparenciaAutomatica() {
        Paroquia p = ApoioTeste.criarParoquia(paroquiaRepository, "Paróquia Telefone Default");
        Usuario u = usuarioRepository.findById(novo(p, "default.tel@servio.dev").getId()).orElseThrow();
        assertNull(u.getTelefone());
        assertEquals(Aparencia.AUTOMATICO, u.getAparencia());
        assertEquals(0, u.getTelefonePulos());
    }

    @Test
    void telefoneAtivoEhUnicoEBuscavelELiberaQuandoDesativa() {
        Paroquia p = ApoioTeste.criarParoquia(paroquiaRepository, "Paróquia Telefone Unico");
        Usuario a = novo(p, "a.tel@servio.dev");
        Usuario b = novo(p, "b.tel@servio.dev");
        a.setTelefone("+5583988124407");
        usuarioRepository.saveAndFlush(a);

        assertEquals(a.getId(), usuarioRepository.findByTelefoneAndActiveTrue("+5583988124407").orElseThrow().getId());
        assertTrue(usuarioRepository.existsByTelefoneAndActiveTrueAndIdNot("+5583988124407", b.getId()));
        assertFalse(usuarioRepository.existsByTelefoneAndActiveTrueAndIdNot("+5583988124407", a.getId()));

        b.setTelefone("+5583988124407");
        assertThrows(DataIntegrityViolationException.class, () -> usuarioRepository.saveAndFlush(b));
    }

    @Test
    void telefoneForaDoFormatoNormalizadoEhRecusadoPeloBanco() {
        Paroquia p = ApoioTeste.criarParoquia(paroquiaRepository, "Paróquia Telefone Check");
        Usuario u = novo(p, "check.tel@servio.dev");
        u.setTelefone("(83) 98812-4407");
        assertThrows(DataIntegrityViolationException.class, () -> usuarioRepository.saveAndFlush(u));
    }
}
