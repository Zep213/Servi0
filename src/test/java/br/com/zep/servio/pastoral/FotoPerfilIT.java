package br.com.zep.servio.pastoral;

import br.com.zep.servio.model.Usuario;
import br.com.zep.servio.repository.FuncaoRepository;
import br.com.zep.servio.repository.ParoquiaRepository;
import br.com.zep.servio.repository.PastoralRepository;
import br.com.zep.servio.repository.UsuarioFotoRepository;
import br.com.zep.servio.repository.UsuarioPastoralRepository;
import br.com.zep.servio.repository.UsuarioRepository;
import br.com.zep.servio.seguranca.TestcontainersConfig;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Foto de perfil: PUT/DELETE /api/me/foto e GET /api/fotos/{id}. Imagens geradas no próprio teste. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
@Transactional
class FotoPerfilIT {

    @Autowired
    MockMvc mvc;
    @Autowired
    ParoquiaRepository paroquiaRepository;
    @Autowired
    UsuarioRepository usuarioRepository;
    @Autowired
    PastoralRepository pastoralRepository;
    @Autowired
    FuncaoRepository funcaoRepository;
    @Autowired
    UsuarioPastoralRepository usuarioPastoralRepository;
    @Autowired
    UsuarioFotoRepository usuarioFotoRepository;
    @Autowired
    PasswordEncoder passwordEncoder;

    CenarioParoquia cenario;

    @BeforeEach
    void setUp() {
        cenario = new CenarioParoquia(paroquiaRepository, usuarioRepository, pastoralRepository,
                funcaoRepository, usuarioPastoralRepository, passwordEncoder).criar();
    }

    private static byte[] imagem(String formato, int largura, int altura) throws Exception {
        BufferedImage img = new BufferedImage(largura, altura, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.ORANGE);
        g.fillRect(0, 0, largura, altura);
        g.dispose();
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        ImageIO.write(img, formato, saida);
        return saida.toByteArray();
    }

    private ResultActions enviar(Usuario quem, byte[] bytes, String nome) throws Exception {
        return mvc.perform(multipart(HttpMethod.PUT, "/api/me/foto")
                .file(new MockMultipartFile("arquivo", nome, "image/png", bytes))
                .with(user(cenario.principal(quem))).with(csrf()));
    }

    private ResultActions buscar(Usuario quem, String caminho) throws Exception {
        return mvc.perform(get(caminho).with(user(cenario.principal(quem))));
    }

    private static String semQuery(String url) {
        return url.substring(0, url.indexOf('?'));
    }

    @Test
    void pngRetangularVira512x512ESaiComoJpegComCache() throws Exception {
        String corpo = enviar(cenario.membro1Pascom, imagem("png", 1200, 800), "foto.png")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fotoUrl").isString())
                .andReturn().getResponse().getContentAsString();
        String url = JsonPath.read(corpo, "$.fotoUrl");
        assertThat(url).startsWith("/api/fotos/" + cenario.membro1Pascom.getId() + "?v=");

        MvcResult resposta = buscar(cenario.membro2Pascom, semQuery(url))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/jpeg"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().exists("ETag"))
                .andReturn();
        assertThat(resposta.getResponse().getHeader("Cache-Control")).contains("private").contains("max-age=86400");
        BufferedImage lida = ImageIO.read(new ByteArrayInputStream(resposta.getResponse().getContentAsByteArray()));
        assertThat(lida.getWidth()).isEqualTo(512);
        assertThat(lida.getHeight()).isEqualTo(512);
    }

    @Test
    void ifNoneMatchIgualAoEtagDa304() throws Exception {
        enviar(cenario.membro1Pascom, imagem("png", 300, 300), "foto.png").andExpect(status().isOk());
        String caminho = "/api/fotos/" + cenario.membro1Pascom.getId();
        String etag = buscar(cenario.membro1Pascom, caminho).andReturn().getResponse().getHeader("ETag");

        mvc.perform(get(caminho).header("If-None-Match", etag).with(user(cenario.principal(cenario.membro1Pascom))))
                .andExpect(status().isNotModified());
    }

    @Test
    void getDeMeEListaDeMembrosTrazemAFotoUrl() throws Exception {
        buscar(cenario.membro1Pascom, "/api/me").andExpect(jsonPath("$.fotoUrl").doesNotExist());
        enviar(cenario.membro1Pascom, imagem("png", 300, 300), "foto.png").andExpect(status().isOk());

        buscar(cenario.membro1Pascom, "/api/me").andExpect(jsonPath("$.fotoUrl").isString());
        String membros = buscar(cenario.coordenadorPascom, "/api/pastorais/" + cenario.pascom.getId() + "/membros")
                .andReturn().getResponse().getContentAsString();
        String fotoUrl = JsonPath.read(membros,
                "$[?(@.usuario.id == " + cenario.membro1Pascom.getId() + ")].fotoUrl[0]");
        assertThat(fotoUrl).startsWith("/api/fotos/" + cenario.membro1Pascom.getId());
    }

    @Test
    void deleteRemoveAFotoEDeleteSemFotoEh204() throws Exception {
        enviar(cenario.membro1Pascom, imagem("png", 300, 300), "foto.png").andExpect(status().isOk());

        mvc.perform(delete("/api/me/foto").with(user(cenario.principal(cenario.membro1Pascom))).with(csrf()))
                .andExpect(status().isNoContent());
        buscar(cenario.membro1Pascom, "/api/me").andExpect(jsonPath("$.fotoUrl").doesNotExist());
        buscar(cenario.membro1Pascom, "/api/fotos/" + cenario.membro1Pascom.getId()).andExpect(status().isNotFound());
        mvc.perform(delete("/api/me/foto").with(user(cenario.principal(cenario.membro1Pascom))).with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    void acimaDe2MbDa413() throws Exception {
        enviar(cenario.membro1Pascom, new byte[2 * 1024 * 1024 + 1], "foto.png")
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.detail").value("A foto pode ter no máximo 2 MB"));
    }

    @Test
    void htmlComExtensaoPngEGifDao400() throws Exception {
        enviar(cenario.membro1Pascom, "<html>oi</html>".getBytes(StandardCharsets.UTF_8), "foto.png")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Envie uma foto JPEG ou PNG"));
        enviar(cenario.membro1Pascom, imagem("gif", 100, 100), "foto.gif").andExpect(status().isBadRequest());
        assertThat(usuarioFotoRepository.existsById(cenario.membro1Pascom.getId())).isFalse();
    }

    @Test
    void semPartearquivoDa400() throws Exception {
        mvc.perform(multipart(HttpMethod.PUT, "/api/me/foto")
                        .with(user(cenario.principal(cenario.membro1Pascom))).with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void semLoginDa401ESemCsrfDa403() throws Exception {
        mvc.perform(multipart(HttpMethod.PUT, "/api/me/foto")
                        .file(new MockMultipartFile("arquivo", "f.png", "image/png", imagem("png", 100, 100)))
                        .with(csrf()))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/fotos/" + cenario.membro1Pascom.getId())).andExpect(status().isUnauthorized());
        mvc.perform(multipart(HttpMethod.PUT, "/api/me/foto")
                        .file(new MockMultipartFile("arquivo", "f.png", "image/png", imagem("png", 100, 100)))
                        .with(user(cenario.principal(cenario.membro1Pascom))))
                .andExpect(status().isForbidden());
    }

    @Test
    void fotoDeOutraParoquiaDa404() throws Exception {
        enviar(cenario.membro1Pascom, imagem("png", 300, 300), "foto.png").andExpect(status().isOk());

        buscar(cenario.padreB, "/api/fotos/" + cenario.membro1Pascom.getId()).andExpect(status().isNotFound());
        buscar(cenario.padreA, "/api/fotos/" + cenario.membro1Pascom.getId()).andExpect(status().isOk());
    }
}
