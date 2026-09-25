package br.com.zep.servio.pastoral;

import br.com.zep.servio.model.Funcao;
import br.com.zep.servio.model.Paroquia;
import br.com.zep.servio.model.Pastoral;
import br.com.zep.servio.model.Usuario;
import br.com.zep.servio.model.UsuarioPastoral;
import br.com.zep.servio.model.enumerated.PapelPastoral;
import br.com.zep.servio.model.enumerated.Perfil;
import br.com.zep.servio.repository.FuncaoRepository;
import br.com.zep.servio.repository.ParoquiaRepository;
import br.com.zep.servio.repository.PastoralRepository;
import br.com.zep.servio.repository.UsuarioPastoralRepository;
import br.com.zep.servio.repository.UsuarioRepository;
import br.com.zep.servio.security.UsuarioPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Cenário compartilhado pelos testes da Parte 5: Paróquia A com PADRE, pastorais Pascom
 * (COORDENADOR, VICE, 2 SECRETARIOS, TESOUREIRO, 2 MEMBROS) e ECC (COORDENADOR, TESOUREIRO,
 * MEMBRO); um ADMIN de paróquia de origem diferente (pra testar "assumir"); Paróquia B com
 * PADRE e uma pastoral com COORDENADOR; funções de comunicação na Pascom e "Barraca" no ECC.
 * Montado à mão (sem passar pela API) para não acoplar os testes de negócio à camada HTTP.
 */
public class CenarioParoquia {

    public static final String SENHA = "senha12345";

    private final ParoquiaRepository paroquiaRepository;
    private final UsuarioRepository usuarioRepository;
    private final PastoralRepository pastoralRepository;
    private final FuncaoRepository funcaoRepository;
    private final UsuarioPastoralRepository usuarioPastoralRepository;
    private final PasswordEncoder passwordEncoder;

    public Paroquia paroquiaA;
    public Paroquia paroquiaB;
    public Paroquia paroquiaOrigemAdmin;

    public Usuario padreA;
    public Usuario padreB;
    public Usuario admin;

    public Pastoral pascom;
    public Pastoral ecc;
    public Pastoral pastoralB;

    public Usuario coordenadorPascom;
    public Usuario vicePascom;
    public Usuario secretario1Pascom;
    public Usuario secretario2Pascom;
    public Usuario tesoureiroPascom;
    public Usuario membro1Pascom;
    public Usuario membro2Pascom;

    public Usuario coordenadorEcc;
    public Usuario tesoureiroEcc;
    public Usuario membroEcc;

    public Usuario coordenadorPastoralB;

    public Funcao funcaoComunicacaoPascom;
    public Funcao funcaoBarracaEcc;

    public CenarioParoquia(ParoquiaRepository paroquiaRepository, UsuarioRepository usuarioRepository,
                            PastoralRepository pastoralRepository, FuncaoRepository funcaoRepository,
                            UsuarioPastoralRepository usuarioPastoralRepository, PasswordEncoder passwordEncoder) {
        this.paroquiaRepository = paroquiaRepository;
        this.usuarioRepository = usuarioRepository;
        this.pastoralRepository = pastoralRepository;
        this.funcaoRepository = funcaoRepository;
        this.usuarioPastoralRepository = usuarioPastoralRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public CenarioParoquia criar() {
        paroquiaA = paroquia("Paróquia A - " + System.nanoTime());
        paroquiaB = paroquia("Paróquia B - " + System.nanoTime());
        paroquiaOrigemAdmin = paroquia("Paróquia de origem do ADMIN - " + System.nanoTime());

        padreA = usuario(paroquiaA, "padre.a", Perfil.PADRE);
        padreB = usuario(paroquiaB, "padre.b", Perfil.PADRE);
        admin = usuario(paroquiaOrigemAdmin, "admin", Perfil.ADMIN);

        pascom = pastoral(paroquiaA, "Pascom");
        ecc = pastoral(paroquiaA, "ECC");
        pastoralB = pastoral(paroquiaB, "Pastoral de B");

        coordenadorPascom = usuario(paroquiaA, "coordenador.pascom", Perfil.SERVIDOR);
        vicePascom = usuario(paroquiaA, "vice.pascom", Perfil.SERVIDOR);
        secretario1Pascom = usuario(paroquiaA, "secretario1.pascom", Perfil.SERVIDOR);
        secretario2Pascom = usuario(paroquiaA, "secretario2.pascom", Perfil.SERVIDOR);
        tesoureiroPascom = usuario(paroquiaA, "tesoureiro.pascom", Perfil.SERVIDOR);
        membro1Pascom = usuario(paroquiaA, "membro1.pascom", Perfil.SERVIDOR);
        membro2Pascom = usuario(paroquiaA, "membro2.pascom", Perfil.SERVIDOR);

        participacao(coordenadorPascom, pascom, PapelPastoral.COORDENADOR);
        participacao(vicePascom, pascom, PapelPastoral.VICE);
        participacao(secretario1Pascom, pascom, PapelPastoral.SECRETARIO);
        participacao(secretario2Pascom, pascom, PapelPastoral.SECRETARIO);
        participacao(tesoureiroPascom, pascom, PapelPastoral.TESOUREIRO);
        participacao(membro1Pascom, pascom, PapelPastoral.MEMBRO);
        participacao(membro2Pascom, pascom, PapelPastoral.MEMBRO);

        coordenadorEcc = usuario(paroquiaA, "coordenador.ecc", Perfil.SERVIDOR);
        tesoureiroEcc = usuario(paroquiaA, "tesoureiro.ecc", Perfil.SERVIDOR);
        membroEcc = usuario(paroquiaA, "membro.ecc", Perfil.SERVIDOR);

        participacao(coordenadorEcc, ecc, PapelPastoral.COORDENADOR);
        participacao(tesoureiroEcc, ecc, PapelPastoral.TESOUREIRO);
        participacao(membroEcc, ecc, PapelPastoral.MEMBRO);

        coordenadorPastoralB = usuario(paroquiaB, "coordenador.b", Perfil.SERVIDOR);
        participacao(coordenadorPastoralB, pastoralB, PapelPastoral.COORDENADOR);

        funcaoComunicacaoPascom = funcao(paroquiaA, pascom, "Comunicação");
        funcaoBarracaEcc = funcao(paroquiaA, ecc, "Barraca");

        return this;
    }

    /** Principal pronto pra usar com SecurityMockMvcRequestPostProcessors.user(...). */
    public UsuarioPrincipal principal(Usuario usuario) {
        return new UsuarioPrincipal(usuario.getId(), usuario.getParoquiaId(), usuario.getNome(),
                usuario.getEmail(), null, usuario.getPerfil());
    }

    private Paroquia paroquia(String nome) {
        Paroquia paroquia = new Paroquia();
        paroquia.setNome(nome);
        paroquia.setEmailContato("contato+" + System.nanoTime() + "@servio.dev");
        return paroquiaRepository.save(paroquia);
    }

    private Usuario usuario(Paroquia paroquia, String apelido, Perfil perfil) {
        Usuario usuario = new Usuario();
        usuario.setNome(apelido);
        usuario.setEmail(apelido + "." + System.nanoTime() + "@servio.dev");
        usuario.setSenha(passwordEncoder.encode(SENHA));
        usuario.setPerfil(perfil);
        usuario.setParoquiaId(paroquia.getId());
        return usuarioRepository.save(usuario);
    }

    private Pastoral pastoral(Paroquia paroquia, String nome) {
        Pastoral pastoral = new Pastoral();
        pastoral.setNome(nome);
        pastoral.setParoquiaId(paroquia.getId());
        return pastoralRepository.save(pastoral);
    }

    private Funcao funcao(Paroquia paroquia, Pastoral pastoral, String nome) {
        Funcao funcao = new Funcao();
        funcao.setNome(nome);
        funcao.setPastoral(pastoral);
        funcao.setParoquiaId(paroquia.getId());
        return funcaoRepository.save(funcao);
    }

    private UsuarioPastoral participacao(Usuario usuario, Pastoral pastoral, PapelPastoral papel) {
        UsuarioPastoral up = new UsuarioPastoral();
        up.setUsuario(usuario);
        up.setPastoral(pastoral);
        up.setPapel(papel);
        up.setParoquiaId(usuario.getParoquiaId());
        return usuarioPastoralRepository.save(up);
    }
}
