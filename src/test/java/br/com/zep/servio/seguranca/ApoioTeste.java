package br.com.zep.servio.seguranca;

import br.com.zep.servio.model.Paroquia;
import br.com.zep.servio.model.Usuario;
import br.com.zep.servio.model.enumerated.Perfil;
import br.com.zep.servio.repository.ParoquiaRepository;
import br.com.zep.servio.repository.UsuarioRepository;
import jakarta.servlet.http.Cookie;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Helpers repetidos nos testes de segurança: criar paróquia/usuário e logar de verdade. */
final class ApoioTeste {

    private ApoioTeste() {
    }

    static Paroquia criarParoquia(ParoquiaRepository repo, String nome) {
        Paroquia paroquia = new Paroquia();
        paroquia.setNome(nome);
        paroquia.setEmailContato("contato+" + System.nanoTime() + "@servio.dev");
        return repo.save(paroquia);
    }

    static Usuario criarUsuario(UsuarioRepository repo, PasswordEncoder encoder, Long paroquiaId,
            String email, String senha, Perfil perfil) {
        Usuario usuario = new Usuario();
        usuario.setNome("Usuário de Teste");
        usuario.setEmail(email);
        usuario.setSenha(encoder.encode(senha));
        usuario.setPerfil(perfil);
        usuario.setParoquiaId(paroquiaId);
        return repo.save(usuario);
    }

    /** Cria a paróquia junto: uso comum quando o teste não precisa reaproveitar a paróquia. */
    static Usuario criarUsuarioComParoquia(ParoquiaRepository paroquiaRepo, UsuarioRepository usuarioRepo,
            PasswordEncoder encoder, String email, String senha, Perfil perfil) {
        Paroquia paroquia = criarParoquia(paroquiaRepo, "Paróquia de Teste");
        return criarUsuario(usuarioRepo, encoder, paroquia.getId(), email, senha, perfil);
    }

    static Cookie login(MockMvc mvc, String email, String senha) throws Exception {
        MvcResult resultado = mvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("email", email)
                        .param("senha", senha))
                .andExpect(status().isNoContent())
                .andReturn();
        return resultado.getResponse().getCookie("SERVIO_SESSION");
    }
}
