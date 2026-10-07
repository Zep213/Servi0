import { zodResolver } from '@hookform/resolvers/zod';
import { useForm } from 'react-hook-form';
import { useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router';
import { z } from 'zod';
import { postMeSenha } from '../../api/generated/servio';
import { ErroApi } from '../../api/cliente';
import { Botao } from '../../componentes/Botao';
import { Campo } from '../../componentes/Campo';
import { Card } from '../../componentes/Card';
import { usuarioQuery } from '../../auth/usuarioQuery';
import { useState } from 'react';

const esquema = z
  .object({
    senhaAtual: z.string().min(1, 'Informe a senha atual'),
    senhaNova: z
      .string()
      .min(8, 'A senha nova precisa de pelo menos 8 caracteres')
      .max(72, 'A senha nova pode ter no máximo 72 caracteres'),
    confirmacao: z.string(),
  })
  .refine((d) => d.senhaNova === d.confirmacao, {
    path: ['confirmacao'],
    message: 'As senhas não são iguais',
  });
type Dados = z.infer<typeof esquema>;

export function TrocarSenha() {
  const navegar = useNavigate();
  const cliente = useQueryClient();
  const [erroGeral, definirErroGeral] = useState<string | null>(null);
  const formulario = useForm<Dados>({
    resolver: zodResolver(esquema),
    defaultValues: { senhaAtual: '', senhaNova: '', confirmacao: '' },
  });

  const enviar = formulario.handleSubmit(async (dados) => {
    definirErroGeral(null);
    try {
      await postMeSenha({ senhaAtual: dados.senhaAtual, senhaNova: dados.senhaNova });
      // o backend derruba todas as sessões, inclusive esta: o cache também deixa de dizer que há alguém logado
      // Primeiro leva ao login (com o aviso); só depois atualiza quem está logado. Na ordem inversa,
      // a rota antiga ainda montada redireciona de volta e o aviso se perde.
      await navegar('/entrar', {
        replace: true,
        state: { aviso: 'Senha trocada. Entre de novo com a senha nova.' },
      });
      await cliente.invalidateQueries({ queryKey: usuarioQuery.queryKey });
    } catch (erro) {
      if (erro instanceof ErroApi && erro.status === 422) {
        formulario.setError('senhaAtual', { message: erro.message });
        return;
      }
      definirErroGeral('Não foi possível trocar a senha agora. Tente de novo.');
    }
  });

  return (
    <Card titulo="Trocar a senha">
      <form onSubmit={(e) => void enviar(e)} noValidate>
        <Campo
          rotulo="Senha atual"
          type="password"
          autoComplete="current-password"
          erro={formulario.formState.errors.senhaAtual?.message}
          {...formulario.register('senhaAtual')}
        />
        <Campo
          rotulo="Senha nova"
          type="password"
          autoComplete="new-password"
          ajuda="Use pelo menos 8 caracteres."
          erro={formulario.formState.errors.senhaNova?.message}
          {...formulario.register('senhaNova')}
        />
        <Campo
          rotulo="Repita a senha nova"
          type="password"
          autoComplete="new-password"
          erro={formulario.formState.errors.confirmacao?.message}
          {...formulario.register('confirmacao')}
        />
        {erroGeral ? (
          <p className="campo__erro" role="alert">
            {erroGeral}
          </p>
        ) : null}
        <Botao type="submit" disabled={formulario.formState.isSubmitting}>
          Trocar senha
        </Botao>
      </form>
    </Card>
  );
}
