# Cash Hunters Android

Aplicativo Android em Kotlin/Compose para o CRM Cash Hunters. A base usa o mesmo Supabase do CRM web e nunca versiona chaves privadas.

## Abrir no Android Studio

1. Abra esta pasta no Android Studio e sincronize o Gradle.
2. Crie `local.properties` na raiz com as credenciais públicas do projeto:

   ```properties
   SUPABASE_URL=https://seu-projeto.supabase.co
   SUPABASE_ANON_KEY=sua-chave-publica-anon-ou-publishable
   ```

3. Rode o app em um emulador ou aparelho com Android 8 (API 26) ou superior.

O app autentica por Supabase Auth e a tela **Leads** consulta `leads` via REST respeitando a sessão e as políticas RLS. As áreas Dashboard, Kanban, Agenda e Admin estão presentes como pontos de extensão para as tabelas e regras já existentes no CRM.

Não use `SUPABASE_SERVICE_ROLE_KEY`, segredos da Meta ou tokens privados no aplicativo.
