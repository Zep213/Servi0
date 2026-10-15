package br.com.zep.servio.service;

import br.com.zep.servio.exception.ServioException;
import br.com.zep.servio.repository.UsuarioFotoRepository;
import br.com.zep.servio.repository.UsuarioRepository;
import br.com.zep.servio.security.UsuarioLogado;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.zip.CRC32;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class FotoPerfilServiceTest {

    private final FotoPerfilService service = new FotoPerfilService(mock(UsuarioFotoRepository.class),
            mock(UsuarioRepository.class), mock(UsuarioLogado.class), mock(AuditLogService.class), Clock.systemUTC());

    private static byte[] imagem(String formato, int largura, int altura, int tipo) throws IOException {
        BufferedImage img = new BufferedImage(largura, altura, tipo);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.ORANGE);
        g.fillRect(0, 0, largura, altura);
        g.setColor(Color.BLUE);
        g.fillOval(largura / 4, altura / 4, largura / 2, altura / 2);
        g.dispose();
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        ImageIO.write(img, formato, saida);
        return saida.toByteArray();
    }

    private static BufferedImage ler(byte[] bytes) throws IOException {
        return ImageIO.read(new ByteArrayInputStream(bytes));
    }

    private static boolean contem(byte[] bytes, String texto) {
        return new String(bytes, StandardCharsets.ISO_8859_1).contains(texto);
    }

    private ServioException recusa(byte[] bytes) {
        return assertThrows(ServioException.class, () -> service.processar(bytes));
    }

    @Test
    void pngRetangularVira512x512JpegQuadrado() throws Exception {
        byte[] saida = service.processar(imagem("png", 1200, 800, BufferedImage.TYPE_INT_ARGB));

        BufferedImage resultado = ler(saida);
        assertEquals(512, resultado.getWidth());
        assertEquals(512, resultado.getHeight());
        // JPEG começa com FF D8
        assertEquals((byte) 0xFF, saida[0]);
        assertEquals((byte) 0xD8, saida[1]);
    }

    @Test
    void imagemPequenaNaoEhAmpliada() throws Exception {
        BufferedImage resultado = ler(service.processar(imagem("png", 200, 100, BufferedImage.TYPE_INT_RGB)));
        assertEquals(100, resultado.getWidth());
        assertEquals(100, resultado.getHeight());
    }

    @Test
    void jpegComExifSaiSemExif() throws Exception {
        byte[] original = imagem("jpg", 600, 600, BufferedImage.TYPE_INT_RGB);
        byte[] comExif = comSegmentoExif(original);
        assertTrue(contem(comExif, "Exif"), "o teste precisa partir de um JPEG com EXIF");

        byte[] saida = service.processar(comExif);

        assertFalse(contem(saida, "Exif"));
        assertFalse(contem(saida, "GPS-FAKE"));
    }

    /** Insere um APP1 "Exif" logo depois do SOI (FF D8), como o celular faz. */
    private static byte[] comSegmentoExif(byte[] jpeg) {
        byte[] dados = "Exif\0\0GPS-FAKE-LAT-LON".getBytes(StandardCharsets.ISO_8859_1);
        int tamanho = dados.length + 2;
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        saida.write(jpeg, 0, 2);
        saida.write(0xFF);
        saida.write(0xE1);
        saida.write((tamanho >> 8) & 0xFF);
        saida.write(tamanho & 0xFF);
        saida.write(dados, 0, dados.length);
        saida.write(jpeg, 2, jpeg.length - 2);
        return saida.toByteArray();
    }

    @Test
    void gifEhRecusadoPeloConteudo() throws Exception {
        ServioException erro = recusa(imagem("gif", 100, 100, BufferedImage.TYPE_INT_RGB));
        assertEquals(HttpStatus.BAD_REQUEST, erro.getStatus());
        assertEquals("Envie uma foto JPEG ou PNG", erro.getMessage());
    }

    @Test
    void htmlComExtensaoDePngEhRecusado() {
        ServioException erro = recusa("<html><script>alert(1)</script></html>".getBytes(StandardCharsets.UTF_8));
        assertEquals(HttpStatus.BAD_REQUEST, erro.getStatus());
        assertEquals("Envie uma foto JPEG ou PNG", erro.getMessage());
    }

    @Test
    void svgEPdfSaoRecusados() {
        assertEquals(HttpStatus.BAD_REQUEST,
                recusa("<svg xmlns=\"http://www.w3.org/2000/svg\"/>".getBytes(StandardCharsets.UTF_8)).getStatus());
        assertEquals(HttpStatus.BAD_REQUEST, recusa("%PDF-1.7 ...".getBytes(StandardCharsets.UTF_8)).getStatus());
    }

    @Test
    void pngCortadoNoMeioEhRecusadoSemErro500() throws Exception {
        byte[] inteiro = imagem("png", 400, 400, BufferedImage.TYPE_INT_RGB);
        byte[] cortado = java.util.Arrays.copyOf(inteiro, inteiro.length / 2);
        assertEquals(HttpStatus.BAD_REQUEST, recusa(cortado).getStatus());
    }

    @Test
    void imagemComVintMilPorVintMilPixelsEhRecusadaSemDecodificar() throws Exception {
        byte[] png = imagem("png", 8, 8, BufferedImage.TYPE_INT_RGB);
        // troca largura e altura no IHDR (e o CRC) por 20000 x 20000: poucos bytes, pixels de sobra
        escreverInt(png, 16, 20000);
        escreverInt(png, 20, 20000);
        CRC32 crc = new CRC32();
        crc.update(png, 12, 17);
        escreverInt(png, 29, (int) crc.getValue());

        ServioException erro = recusa(png);

        assertEquals(HttpStatus.BAD_REQUEST, erro.getStatus());
        assertEquals("Imagem grande demais", erro.getMessage());
    }

    private static void escreverInt(byte[] destino, int posicao, int valor) {
        destino[posicao] = (byte) (valor >>> 24);
        destino[posicao + 1] = (byte) (valor >>> 16);
        destino[posicao + 2] = (byte) (valor >>> 8);
        destino[posicao + 3] = (byte) valor;
    }

    @Test
    void arquivoAcimaDe2MbDa413AntesDeProcessar() {
        MockMultipartFile grande = new MockMultipartFile("arquivo", "foto.png", "image/png", new byte[2 * 1024 * 1024 + 1]);
        ServioException erro = assertThrows(ServioException.class, () -> service.salvar(grande));
        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, erro.getStatus());
        assertEquals("A foto pode ter no máximo 2 MB", erro.getMessage());
    }

    @Test
    void parteAusenteOuVaziaDa400() {
        assertEquals(HttpStatus.BAD_REQUEST,
                assertThrows(ServioException.class, () -> service.salvar(null)).getStatus());
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ServioException.class,
                () -> service.salvar(new MockMultipartFile("arquivo", new byte[0]))).getStatus());
    }

    @Test
    void urlTemVersaoEEtagMudaComAData() {
        java.time.LocalDateTime a = java.time.LocalDateTime.of(2026, 11, 9, 12, 0, 0);
        assertNotNull(FotoPerfilService.urlDe(7L, a));
        assertTrue(FotoPerfilService.urlDe(7L, a).startsWith("/api/fotos/7?v="));
        assertFalse(FotoPerfilService.etagDe(a).equals(FotoPerfilService.etagDe(a.plusSeconds(1))));
    }
}
