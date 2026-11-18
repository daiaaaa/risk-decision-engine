# Realm `motor-risco` (somente desenvolvimento local)

`motor-risco-realm.json` é importado pelo Keycloak na subida (`docker compose up -d`).

**Os segredos e a senha deste arquivo são públicos de propósito.** Servem só para rodar o
projeto na máquina local. Em produção, os segredos dos clientes ficam em um cofre (por
exemplo, HashiCorp Vault) e os usuários vêm do diretório corporativo.

| Cliente | Tipo | Fluxo | Papel | Segredo (dev) |
|---|---|---|---|---|
| `sistema-pagamentos` | confidencial | `client_credentials` | `avaliador` | `segredo-dev-sistema-pagamentos` |
| `prometheus` | confidencial | `client_credentials` | `monitoramento` | `segredo-dev-prometheus` |
| `painel-analistas` | público | `authorization_code` + PKCE | `analista` (via usuário) | — |

Usuário: `analista` / senha `analista`, com o papel `analista`.

Cada cliente tem dois mapeadores de token:

- **Audiência:** coloca `motor-risco-api` no `aud`. A API recusa tokens sem isso, para não
  aceitar um token emitido para outro sistema do mesmo Keycloak.
- **Papéis do realm:** coloca os papéis em `realm_access.roles`, que a API converte em
  autoridades do Spring Security.

O cliente `painel-analistas` também está com o *password grant* (`directAccessGrantsEnabled`)
ligado, **só para a demonstração pela linha de comando**. Esse fluxo é desaconselhado pelo
OAuth 2.1; um front-end real usa `authorization_code` com PKCE, que também está habilitado.
