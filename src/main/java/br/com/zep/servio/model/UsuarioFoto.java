package br.com.zep.servio.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Foto de perfil já processada (JPEG de até 512 x 512, sem EXIF). Não é {@code TenantEntity}: a tabela não tem
 * is_active/version e a paróquia vem do {@code Usuario} dono.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "usuario_foto")
public class UsuarioFoto {

    @Id
    @Column(name = "usuario_id")
    private Long usuarioId;

    @Column(nullable = false)
    private byte[] conteudo;

    @Column(name = "tipo_conteudo", nullable = false, length = 20)
    private String tipoConteudo = "image/jpeg";

    @Column(name = "atualizado_em", nullable = false)
    private LocalDateTime atualizadoEm;
}
