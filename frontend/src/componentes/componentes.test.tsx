import { fireEvent, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { Botao } from './Botao';
import { Campo } from './Campo';
import { DialogoConfirmacao } from './DialogoConfirmacao';
import { Modal } from './Modal';
import { SeloStatus } from './SeloStatus';
import { Tabela } from './Tabela';
import { ProvedorToast } from './Toast';
import { useToast } from './toastContexto';

describe('Botao', () => {
  it('é um botão do tipo button por padrão e chama o clique', async () => {
    const clique = vi.fn();
    render(<Botao onClick={clique}>Confirmar presença</Botao>);
    await userEvent.click(screen.getByRole('button', { name: 'Confirmar presença' }));
    expect(clique).toHaveBeenCalledOnce();
    expect(screen.getByRole('button')).toHaveAttribute('type', 'button');
  });

  it('respeita desabilitado', () => {
    render(<Botao disabled>Enviar</Botao>);
    expect(screen.getByRole('button', { name: 'Enviar' })).toBeDisabled();
  });
});

describe('Campo', () => {
  it('liga o rótulo ao campo e o erro ao campo (aria-describedby)', () => {
    render(<Campo rotulo="E-mail" erro="Informe um e-mail válido" />);
    const input = screen.getByLabelText('E-mail');
    expect(input).toHaveAttribute('aria-invalid', 'true');
    expect(input.getAttribute('aria-describedby')).toContain('erro');
    expect(screen.getByText('Informe um e-mail válido')).toBeInTheDocument();
  });

  it('sem erro, não marca como inválido', () => {
    render(<Campo rotulo="Nome" />);
    expect(screen.getByLabelText('Nome')).not.toHaveAttribute('aria-invalid');
  });
});

describe('Modal', () => {
  it('não aparece quando fechado', () => {
    render(
      <Modal titulo="Detalhe" aberto={false} onFechar={() => undefined}>
        conteúdo
      </Modal>,
    );
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('abre como diálogo modal e fecha com Escape', () => {
    const fechar = vi.fn();
    render(
      <Modal titulo="Detalhe" aberto onFechar={fechar}>
        conteúdo
      </Modal>,
    );
    expect(screen.getByRole('dialog', { name: 'Detalhe' })).toHaveAttribute('aria-modal', 'true');
    fireEvent.keyDown(document, { key: 'Escape' });
    expect(fechar).toHaveBeenCalledOnce();
  });
});

describe('DialogoConfirmacao', () => {
  it('confirma e cancela pelos botões', async () => {
    const confirmar = vi.fn();
    const cancelar = vi.fn();
    render(
      <DialogoConfirmacao
        aberto
        titulo="Remover pessoa"
        mensagem="A pessoa sai da pastoral."
        textoConfirmar="Remover"
        perigoso
        onConfirmar={confirmar}
        onCancelar={cancelar}
      />,
    );
    await userEvent.click(screen.getByRole('button', { name: 'Voltar' }));
    expect(cancelar).toHaveBeenCalledOnce();
    await userEvent.click(screen.getByRole('button', { name: 'Remover' }));
    expect(confirmar).toHaveBeenCalledOnce();
  });
});

describe('SeloStatus', () => {
  it('mostra o texto do status, não só a cor', () => {
    render(<SeloStatus status="pendente" />);
    expect(screen.getByText('Aguardando resposta')).toBeInTheDocument();
  });
});

describe('Tabela', () => {
  it('marca cada célula com o nome da coluna (para virar cartão no celular)', () => {
    const linhas = [{ id: 1, nome: 'Maria', funcao: 'Leitora' }];
    render(
      <Tabela
        titulo="Escala"
        linhas={linhas}
        chaveDaLinha={(l) => l.id}
        colunas={[
          { chave: 'nome', titulo: 'Pessoa', celula: (l) => l.nome },
          { chave: 'funcao', titulo: 'Função', celula: (l) => l.funcao },
        ]}
      />,
    );
    const celula = screen.getByText('Leitora');
    expect(celula).toHaveAttribute('data-label', 'Função');
  });
});

describe('Toast', () => {
  function Disparador() {
    const { mostrar } = useToast();
    return (
      <button
        onClick={() => {
          mostrar('Presença confirmada', 'sucesso');
        }}
      >
        ok
      </button>
    );
  }

  it('mostra o aviso numa região anunciada', async () => {
    render(
      <ProvedorToast>
        <Disparador />
      </ProvedorToast>,
    );
    await userEvent.click(screen.getByRole('button', { name: 'ok' }));
    expect(screen.getByText('Presença confirmada')).toBeInTheDocument();
    expect(screen.getByRole('status')).toHaveAttribute('aria-live', 'polite');
  });
});
