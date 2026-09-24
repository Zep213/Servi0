package br.com.zep.servio.model.dto;

public record ResumoPlataformaDTO(
    long paroquias,
    long usuarios,
    long pastorais,
    long celebracoesProximos30Dias
) {}
