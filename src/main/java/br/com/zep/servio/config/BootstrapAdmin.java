package br.com.zep.servio.config;

import br.com.zep.servio.model.Paroquia;
import br.com.zep.servio.model.Usuario;
import br.com.zep.servio.model.enumerated.Perfil;
import br.com.zep.servio.repository.ParoquiaRepository;
import br.com.zep.servio.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cria a primeira paróquia e o primeiro ADMIN quando o banco não tem nenhum usuário.
 * Os dados vêm das variáveis SERVIO_ADMIN_EMAIL / SERVIO_ADMIN_SENHA.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BootstrapAdmin implements ApplicationRunner {

    private final UsuarioRepository usuarioRepository;
    private final ParoquiaRepository paroquiaRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${servio.bootstrap.admin-email:}")
    private String email;

    @Value("${servio.bootstrap.admin-senha:}")
    private String senha;

    @Value("${servio.bootstrap.admin-nome:Administrador}")
    private String adminNome;

    @Value("${servio.bootstrap.paroquia-nome:Paróquia Inicial}")
    private String paroquiaNome;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (usuarioRepository.count() > 0) {
            return;
        }
        if (email.isBlank() || senha.isBlank()) {
            log.warn("Nenhum usuário cadastrado. Defina SERVIO_ADMIN_EMAIL e SERVIO_ADMIN_SENHA para criar o primeiro ADMIN.");
            return;
        }
        if (senha.length() < 8) {
            log.error("SERVIO_ADMIN_SENHA precisa ter pelo menos 8 caracteres. ADMIN não foi criado.");
            return;
        }

        Paroquia paroquia = new Paroquia();
        paroquia.setNome(paroquiaNome);
        paroquia.setEmailContato(email);
        paroquiaRepository.save(paroquia);

        Usuario admin = new Usuario();
        admin.setNome(adminNome);
        admin.setEmail(email);
        admin.setSenha(passwordEncoder.encode(senha));
        admin.setPerfil(Perfil.ADMIN);
        admin.setParoquia(paroquia);        // etapa 2: admin.setParoquiaId(paroquia.getId());
        usuarioRepository.save(admin);

        log.info("Primeiro ADMIN criado: {}", email);
    }
}
