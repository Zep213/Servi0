import { mkdir, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { ClienteApi } from './apiCliente';

export interface DadosSemeados {
  coordenador: { email: string; senha: string };
  servidor: { email: string; senha: string };
  pastoralId: number;
  celebracaoId: number;
  celebracaoData: string;
}

const ARQUIVO_SAIDA = path.join(import.meta.dirname, '..', 'test-results', 'semeadura.json');

function proximoDomingoIso(): string {
  const hoje = new Date();
  const diasAteDomingo = (7 - hoje.getDay()) % 7 || 7;
  const domingo = new Date(hoje);
  domingo.setDate(hoje.getDate() + diasAteDomingo);
  return domingo.toISOString().slice(0, 10);
}

function exigirEnv(nome: string): string {
  const valor = process.env[nome];
  if (!valor) {
    throw new Error(
      `Faltou a variável de ambiente ${nome} — ela tem que ser a mesma usada para subir o ` +
        'docker compose (o admin inicial só existe com essas credenciais).',
    );
  }
  return valor;
}

/**
 * Semeia pela API (sem UI) o mínimo pra sortear uma missa: comunidade, pastoral, função,
 * celebração de domingo, um coordenador (indisponível no dia), um servidor membro e a vaga
 * (quantidade=1) pronta.
 * A UI entra só depois, no teste, pra sortear e confirmar — ver `escalacao.spec.ts`.
 */
export default async function globalSetup(): Promise<void> {
  const baseUrl = process.env.E2E_BASE_URL ?? 'http://localhost';
  const adminEmail = exigirEnv('SERVIO_ADMIN_EMAIL');
  const adminSenha = exigirEnv('SERVIO_ADMIN_SENHA');
  const sufixo = Date.now();

  const api = new ClienteApi(baseUrl);
  await api.login(adminEmail, adminSenha);

  const comunidade = await api.post<{ id: number }>('/api/comunidades', {
    nome: `Comunidade E2E ${String(sufixo)}`,
  });
  const pastoral = await api.post<{ id: number }>('/api/pastorais', {
    nome: `Pascom E2E ${String(sufixo)}`,
  });
  const funcao = await api.post<{ id: number }>('/api/funcoes', {
    nome: 'Comunicação',
    pastoralId: pastoral.id,
  });
  const celebracaoData = proximoDomingoIso();
  const celebracao = await api.post<{ id: number }>('/api/celebracoes', {
    comunidadeId: comunidade.id,
    data: celebracaoData,
    hora: '08:00:00',
    tipo: 'MISSA_DOMINICAL',
  });

  const coordenador = { email: `coordenador.e2e.${String(sufixo)}@exemplo.com`, senha: 'senha-e2e-123' };
  const servidor = { email: `servidor.e2e.${String(sufixo)}@exemplo.com`, senha: 'senha-e2e-123' };

  const usuarioCoordenador = await api.post<{ id: number }>('/api/usuarios', {
    nome: 'Coordenadora E2E',
    email: coordenador.email,
    senha: coordenador.senha,
    perfil: 'SERVIDOR',
  });
  await api.post('/api/usuarios-pastorais', {
    usuarioId: usuarioCoordenador.id,
    pastoralId: pastoral.id,
    papel: 'COORDENADOR',
  });
  // Coordenadora também é candidata no sorteio; indisponível no dia, só a servidora pode sair.
  await api.post('/api/indisponibilidades', {
    usuarioId: usuarioCoordenador.id,
    dataInicio: celebracaoData,
    dataFim: celebracaoData,
    motivo: 'E2E: deixa só a servidora elegível',
  });

  const usuarioServidor = await api.post<{ id: number }>('/api/usuarios', {
    nome: 'Servidora E2E',
    email: servidor.email,
    senha: servidor.senha,
    perfil: 'SERVIDOR',
  });
  await api.post('/api/usuarios-pastorais', {
    usuarioId: usuarioServidor.id,
    pastoralId: pastoral.id,
    papel: 'MEMBRO',
  });

  await api.post('/api/vagas', {
    celebracaoId: celebracao.id,
    funcaoId: funcao.id,
    quantidade: 1,
  });

  const dados: DadosSemeados = {
    coordenador,
    servidor,
    pastoralId: pastoral.id,
    celebracaoId: celebracao.id,
    celebracaoData,
  };
  await mkdir(path.dirname(ARQUIVO_SAIDA), { recursive: true });
  await writeFile(ARQUIVO_SAIDA, JSON.stringify(dados, null, 2));
}
