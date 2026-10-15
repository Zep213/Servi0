package br.com.zep.servio.service;

import br.com.zep.servio.exception.ServioException;
import br.com.zep.servio.model.Usuario;
import br.com.zep.servio.model.UsuarioFoto;
import br.com.zep.servio.repository.UsuarioFotoRepository;
import br.com.zep.servio.repository.UsuarioRepository;
import br.com.zep.servio.security.UsuarioLogado;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Foto de perfil opcional (Sprint 2, Cartão 6). Valida pelo CONTEÚDO (nome e Content-Type do cliente não valem),
 * confere as dimensões antes de decodificar o bitmap, recorta o quadrado central, reduz e recodifica em JPEG:
 * isso tira EXIF (localização), perfil de cor e qualquer coisa escondida no arquivo original.
 */
@Service
@RequiredArgsConstructor
public class FotoPerfilService {

    static final String MSG_FORMATO = "Envie uma foto JPEG ou PNG";

    private final UsuarioFotoRepository fotoRepository;
    private final UsuarioRepository usuarioRepository;
    private final UsuarioLogado usuarioLogado;
    private final AuditLogService auditLogService;
    private final Clock clock;

    @Value("${servio.foto.max-bytes:2097152}")
    private long maxBytes = 2_097_152;
    @Value("${servio.foto.lado-maximo:512}")
    private int ladoMaximo = 512;
    @Value("${servio.foto.pixels-maximo:6000}")
    private int pixelsMaximo = 6000;

    /** Processa o arquivo e grava (ou troca) a foto do usuário logado. */
    @Transactional
    public void salvar(MultipartFile arquivo) {
        if (arquivo == null || arquivo.isEmpty()) {
            throw new ServioException(MSG_FORMATO, HttpStatus.BAD_REQUEST);
        }
        if (arquivo.getSize() > maxBytes) {
            throw new ServioException("A foto pode ter no máximo 2 MB", HttpStatus.PAYLOAD_TOO_LARGE);
        }
        byte[] original;
        try {
            original = arquivo.getBytes();
        } catch (IOException e) {
            throw new ServioException(MSG_FORMATO, HttpStatus.BAD_REQUEST);
        }
        byte[] jpeg = processar(original);

        Usuario usuario = usuarioRepository.findById(usuarioLogado.id()).orElseThrow();
        UsuarioFoto foto = fotoRepository.findById(usuario.getId()).orElseGet(() -> {
            UsuarioFoto nova = new UsuarioFoto();
            nova.setUsuarioId(usuario.getId());
            return nova;
        });
        foto.setConteudo(jpeg);
        foto.setTipoConteudo("image/jpeg");
        foto.setAtualizadoEm(LocalDateTime.now(clock));
        fotoRepository.save(foto);
        auditLogService.registrar("FOTO_ALTERADA", usuario.getId(), usuario, usuario.getParoquiaId(), "Foto enviada");
    }

    /** Sem foto não é erro. */
    @Transactional
    public void remover() {
        Long id = usuarioLogado.id();
        if (fotoRepository.existsById(id)) {
            fotoRepository.deleteById(id);
            Usuario usuario = usuarioRepository.findById(id).orElseThrow();
            auditLogService.registrar("FOTO_ALTERADA", id, usuario, usuario.getParoquiaId(), "Foto removida");
        }
    }

    /** Foto de alguém da MESMA paróquia de quem pede e ativo; senão vazio (o controller responde 404). */
    @Transactional(readOnly = true)
    public Optional<UsuarioFoto> obterParaExibir(Long usuarioId) {
        return usuarioRepository.findByIdAndParoquiaIdAndActiveTrue(usuarioId, usuarioLogado.paroquiaId())
                .flatMap(u -> fotoRepository.findById(u.getId()));
    }

    @Transactional(readOnly = true)
    public String fotoUrl(Long usuarioId) {
        return fotoRepository.atualizadoEm(usuarioId).map(em -> urlDe(usuarioId, em)).orElse(null);
    }

    /** usuarioId -> fotoUrl só das pessoas que têm foto. */
    @Transactional(readOnly = true)
    public Map<Long, String> fotoUrls(List<Long> usuarioIds) {
        Map<Long, String> urls = new HashMap<>();
        if (usuarioIds.isEmpty()) {
            return urls;
        }
        for (Object[] linha : fotoRepository.datasDeFotos(usuarioIds)) {
            Long id = ((Number) linha[0]).longValue();
            urls.put(id, urlDe(id, (LocalDateTime) linha[1]));
        }
        return urls;
    }

    /** A versão (segundos) muda ao trocar a foto, e o navegador busca de novo apesar do cache. */
    public static String urlDe(Long usuarioId, LocalDateTime atualizadoEm) {
        return "/api/fotos/" + usuarioId + "?v=" + atualizadoEm.toEpochSecond(ZoneOffset.UTC);
    }

    /** ETag da foto: a data de atualização (o conteúdo só muda quando ela muda). */
    public static String etagDe(LocalDateTime atualizadoEm) {
        return "\"" + atualizadoEm.toEpochSecond(ZoneOffset.UTC) + "-" + atualizadoEm.getNano() + "\"";
    }

    /** Bytes enviados -> JPEG quadrado de no máximo {@code ladoMaximo}, sem metadados. */
    byte[] processar(byte[] bytes) {
        ImageReader leitor = null;
        try (ImageInputStream entrada = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> leitores = ImageIO.getImageReaders(entrada);
            if (!leitores.hasNext()) {
                throw new ServioException(MSG_FORMATO, HttpStatus.BAD_REQUEST);
            }
            leitor = leitores.next();
            String formato = leitor.getFormatName().toLowerCase();
            if (!formato.equals("jpeg") && !formato.equals("png")) {
                throw new ServioException(MSG_FORMATO, HttpStatus.BAD_REQUEST);
            }
            leitor.setInput(entrada, true, true);

            // dimensões antes de decodificar: imagem pequena em bytes pode ser gigante em pixels
            int largura = leitor.getWidth(0);
            int altura = leitor.getHeight(0);
            if (largura <= 0 || altura <= 0) {
                throw new ServioException(MSG_FORMATO, HttpStatus.BAD_REQUEST);
            }
            if (largura > pixelsMaximo || altura > pixelsMaximo) {
                throw new ServioException("Imagem grande demais", HttpStatus.BAD_REQUEST);
            }

            // lê só o quadrado central e já reduzido (amostragem), sem montar o bitmap inteiro
            int lado = Math.min(largura, altura);
            ImageReadParam parametros = leitor.getDefaultReadParam();
            parametros.setSourceRegion(new Rectangle((largura - lado) / 2, (altura - lado) / 2, lado, lado));
            int passo = Math.max(1, lado / ladoMaximo);
            parametros.setSourceSubsampling(passo, passo, 0, 0);
            BufferedImage lida = leitor.read(0, parametros);
            return codificarJpeg(redimensionar(lida));
        } catch (ServioException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            // arquivo corrompido, JPEG em CMYK, PNG truncado...: tudo é "não é uma foto que aceitamos"
            throw new ServioException(MSG_FORMATO, HttpStatus.BAD_REQUEST);
        } finally {
            if (leitor != null) {
                leitor.dispose();
            }
        }
    }

    private BufferedImage redimensionar(BufferedImage lida) {
        int lado = Math.min(lida.getWidth(), lida.getHeight());
        int destino = Math.min(ladoMaximo, lado);
        BufferedImage saida = new BufferedImage(destino, destino, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = saida.createGraphics();
        try {
            g.setColor(Color.WHITE);                       // PNG com transparência vira fundo branco
            g.fillRect(0, 0, destino, destino);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(lida, 0, 0, destino, destino, 0, 0, lado, lado, null);
        } finally {
            g.dispose();
        }
        return saida;
    }

    private byte[] codificarJpeg(BufferedImage imagem) throws IOException {
        ImageWriter escritor = ImageIO.getImageWritersByFormatName("jpeg").next();
        try {
            ImageWriteParam parametros = escritor.getDefaultWriteParam();
            parametros.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            parametros.setCompressionQuality(0.85f);
            ByteArrayOutputStream saida = new ByteArrayOutputStream();
            try (ImageOutputStream destino = new MemoryCacheImageOutputStream(saida)) {
                escritor.setOutput(destino);
                escritor.write(null, new IIOImage(imagem, null, null), parametros);   // sem metadados
            }
            return saida.toByteArray();
        } finally {
            escritor.dispose();
        }
    }
}
