package br.com.zep.servio.config;

import br.com.zep.servio.model.Paroquia;
import br.com.zep.servio.model.Pastoral;
import br.com.zep.servio.model.Usuario;
import br.com.zep.servio.model.UsuarioPastoral;
import br.com.zep.servio.model.enumerated.PapelPastoral;
import br.com.zep.servio.model.enumerated.Perfil;
import br.com.zep.servio.repository.ParoquiaRepository;
import br.com.zep.servio.repository.PastoralRepository;
import br.com.zep.servio.repository.UsuarioPastoralRepository;
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
 * Cria a primeira paróquia e o primeiro ADMIN quando o banco não tem nenhum usuário, a
 * partir de SERVIO_ADMIN_EMAIL/SENHA. Também cria, sempre que configuradas e ainda não
 * existentes, as contas de demonstração (somente leitura — ver SomenteLeituraFilter):
 * uma com visão de ADMIN (SERVIO_DEMO_*) e outra com visão de membro (SERVIO_DEMO_MEMBRO_*).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BootstrapAdmin implements ApplicationRunner {

    private final UsuarioRepository usuarioRepository;
    private final ParoquiaRepository paroquiaRepository;
    private final PastoralRepository pastoralRepository;
    private final UsuarioPastoralRepository usuarioPastoralRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${servio.bootstrap.admin-email:}")
    private String email;

    @Value("${servio.bootstrap.admin-senha:}")
    private String senha;

    @Value("${servio.bootstrap.admin-nome:Administrador}")
    private String adminNome;

    @Value("${servio.bootstrap.paroquia-nome:Paróquia Inicial}")
    private String paroquiaNome;

    @Value("${servio.demo.email:}")
    private String demoEmail;

    @Value("${servio.demo.senha:}")
    private String demoSenha;

    @Value("${servio.demo.nome:Visitante (somente leitura)}")
    private String demoNome;

    @Value("${servio.demo-membro.email:}")
    private String demoMembroEmail;

    @Value("${servio.demo-membro.senha:}")
    private String demoMembroSenha;

    @Value("${servio.demo-membro.nome:Visitante Membro (somente leitura)}")
    private String demoMembroNome;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (usuarioRepository.count() == 0) {
            criarAdminInicial();
        }
        criarContaDemoSeConfigurada(demoEmail, demoSenha, demoNome, Perfil.ADMIN, "SERVIO_DEMO_SENHA", false);
        criarContaDemoSeConfigurada(demoMembroEmail, demoMembroSenha, demoMembroNome, Perfil.SERVIDOR,
                "SERVIO_DEMO_MEMBRO_SENHA", true);
    }

    private void criarAdminInicial() {
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
        admin.setParoquiaId(paroquia.getId());
        usuarioRepository.save(admin);

        log.info("Primeiro ADMIN criado: {}", email);
    }

    /** entrarNaPrimeiraPastoral: só faz sentido pra conta "membro" — ADMIN já vê tudo sem precisar disso. */
    private void criarContaDemoSeConfigurada(String demoEmail, String demoSenha, String demoNome, Perfil perfil,
            String nomeVariavelSenha, boolean entrarNaPrimeiraPastoral) {
        if (demoEmail.isBlank() || demoSenha.isBlank()) {
            return;
        }
        if (usuarioRepository.findByEmailIgnoreCaseAndActiveTrue(demoEmail).isPresent()) {
            return;
        }
        if (demoSenha.length() < 8) {
            log.error("{} precisa ter pelo menos 8 caracteres. Conta de demonstração não foi criada.", nomeVariavelSenha);
            return;
        }
        Paroquia paroquia = paroquiaRepository.findAll().stream().findFirst().orElse(null);
        if (paroquia == null) {
            log.warn("Nenhuma paróquia cadastrada ainda. A conta de demonstração será criada no próximo início da aplicação.");
            return;
        }

        Usuario demo = new Usuario();
        demo.setNome(demoNome);
        demo.setEmail(demoEmail);
        demo.setSenha(passwordEncoder.encode(demoSenha));
        demo.setPerfil(perfil);
        demo.setParoquiaId(paroquia.getId());
        usuarioRepository.save(demo);

        if (entrarNaPrimeiraPastoral) {
            pastoralRepository.findByParoquiaIdAndActiveTrue(paroquia.getId()).stream().findFirst()
                    .ifPresent(pastoral -> adicionarComoMembro(demo, pastoral));
        }

        log.info("Conta de demonstração (somente leitura) criada: {}", demoEmail);
    }

    private void adicionarComoMembro(Usuario demo, Pastoral pastoral) {
        UsuarioPastoral vinculo = new UsuarioPastoral();
        vinculo.setUsuario(demo);
        vinculo.setPastoral(pastoral);
        vinculo.setPapel(PapelPastoral.MEMBRO);
        vinculo.setParoquiaId(demo.getParoquiaId());
        usuarioPastoralRepository.save(vinculo);
    }
}
