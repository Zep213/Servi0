package br.com.zep.servio.model.enumerated;

/**
 * Situação da conta de um membro (lista e detalhe da pastoral, só para a coordenação). Sai dos dados que já existem,
 * sem coluna nova: tem senha = ATIVA; sem senha e nunca ativada = PENDENTE (o link de ativação espera); sem senha mas
 * já ativada = PROTEGIDA (a pessoa usou "Proteger minha conta" e falta escolher a senha nova por e-mail; só existe
 * depois que esse recurso entrar, mas o estado já é representável).
 */
public enum StatusAtivacao {
    PENDENTE,
    PROTEGIDA,
    ATIVA
}
