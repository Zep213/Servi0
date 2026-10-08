import { zodResolver } from '@hookform/resolvers/zod';
import { useQueryClient } from '@tanstack/react-query';
import { useForm } from 'react-hook-form';
import { Navigate, useLocation } from 'react-router';
import { z } from 'zod';
import { Botao } from '../../componentes/Botao';
import { Campo } from '../../componentes/Campo';
import { entrar, mensagemDeErroDeLogin } from '../../auth/api';
import { destinoInicial } from '../../auth/destino';
import { useSessao } from '../../auth/sessaoContexto';
import { usuarioQuery } from '../../auth/usuarioQuery';
import { useState } from 'react';

const esquema = z.object({
  email: z.email('Informe um e-mail válido'),
  senha: z.string().min(1, 'Informe a senha'),
});
type Dados = z.infer<typeof esquema>;

interface EstadoDaRota {
  de?: string;
  aviso?: string;
}

export function Entrar() {
  const { usuario, pastorais, pastoralAtiva } = useSessao();
  const local = useLocation();
  const cliente = useQueryClient();
  const [erroGeral, definirErroGeral] = useState<string | null>(null);
  const [acabouDeEntrar, definirAcabouDeEntrar] = useState(false);
  const estado = (local.state ?? {}) as EstadoDaRota;

  const formulario = useForm<Dados>({
    resolver: zodResolver(esquema),
    defaultValues: { email: '', senha: '' },
  });

  // com aviso (ex.: senha trocada), a tela fica para a pessoa ler; não volta sozinha ao início
  if (usuario && !estado.aviso) {
    // Quem participa de mais de uma pastoral escolhe em qual vai trabalhar a cada login.
    if (acabouDeEntrar && pastorais.length > 1) {
      return <Navigate to="/escolher-pastoral" replace state={{ de: estado.de }} />;
    }
    return (
      <Navigate
        to={estado.de ?? destinoInicial(usuario, pastorais, pastoralAtiva?.id ?? null)}
        replace
      />
    );
  }

  const enviar = formulario.handleSubmit(async (dados) => {
    definirErroGeral(null);
    try {
      await entrar(dados.email, dados.senha);
      definirAcabouDeEntrar(true);
      // Com a sessão carregada, o redirecionamento acima leva ao destino (ou à escolha da pastoral).
      await cliente.invalidateQueries({ queryKey: usuarioQuery.queryKey });
    } catch (erro) {
      definirErroGeral(mensagemDeErroDeLogin(erro));
    }
  });

  return (
    <main className="entrada">
      <h1>Entrar no Servio</h1>
      {estado.aviso ? (
        <p className="aviso" role="status">
          {estado.aviso}
        </p>
      ) : null}
      <form onSubmit={(e) => void enviar(e)} noValidate>
        <Campo
          rotulo="E-mail"
          type="email"
          autoComplete="username"
          inputMode="email"
          erro={formulario.formState.errors.email?.message}
          {...formulario.register('email')}
        />
        <Campo
          rotulo="Senha"
          type="password"
          autoComplete="current-password"
          erro={formulario.formState.errors.senha?.message}
          {...formulario.register('senha')}
        />
        {erroGeral ? (
          <p className="campo__erro" role="alert">
            {erroGeral}
          </p>
        ) : null}
        <Botao type="submit" disabled={formulario.formState.isSubmitting}>
          {formulario.formState.isSubmitting ? 'Entrando…' : 'Entrar'}
        </Botao>
      </form>
    </main>
  );
}
