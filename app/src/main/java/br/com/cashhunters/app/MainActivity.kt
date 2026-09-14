package br.com.cashhunters.app

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { Surface(Modifier.fillMaxSize()) { CashHuntersApp() } } }
    }
}

@Composable
private fun CashHuntersApp() {
    val api = remember { SupabaseApi(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY) }
    var token by remember { mutableStateOf<String?>(null) }
    if (!api.isConfigured()) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
            Text("Configure o Supabase", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(12.dp))
            Text("Crie local.properties na raiz com SUPABASE_URL e SUPABASE_ANON_KEY. Use somente a chave pública anon/publishable.")
        }
    } else if (token == null) Login(api) { token = it } else Leads(api, token!!)
}

@Composable
private fun Login(api: SupabaseApi, onLogin: (String) -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val main = remember { Handler(Looper.getMainLooper()) }
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text("Cash Hunters", style = MaterialTheme.typography.headlineLarge)
        Text("Entre com sua conta do CRM")
        Spacer(Modifier.height(20.dp))
        OutlinedTextField(email, { email = it }, label = { Text("E-mail") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(password, { password = it }, label = { Text("Senha") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
        Spacer(Modifier.height(16.dp))
        Button(enabled = !loading && email.isNotBlank() && password.isNotBlank(), onClick = {
            loading = true; error = null
            Thread {
                runCatching { api.signIn(email, password) }.onSuccess { value ->
                    main.post { loading = false; onLogin(value) }
                }.onFailure { failure -> main.post { loading = false; error = failure.message ?: "Não foi possível entrar." } }
            }.start()
        }, modifier = Modifier.fillMaxWidth()) { if (loading) CircularProgressIndicator() else Text("Entrar") }
    }
}

@Composable
private fun Leads(api: SupabaseApi, token: String) {
    var leads by remember { mutableStateOf<List<Lead>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val main = remember { Handler(Looper.getMainLooper()) }
    fun refresh() {
        loading = true; error = null
        Thread {
            runCatching { api.leads(token) }.onSuccess { value -> main.post { leads = value; loading = false } }
                .onFailure { failure -> main.post { error = failure.message ?: "Não foi possível carregar os leads."; loading = false } }
        }.start()
    }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Cash Hunters", style = MaterialTheme.typography.headlineSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Leads", style = MaterialTheme.typography.titleLarge)
            Button(onClick = ::refresh, enabled = !loading) { Text("Atualizar") }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 8.dp)) }
        if (loading) CircularProgressIndicator(Modifier.padding(24.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(leads) { lead -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
                Text(lead.name, style = MaterialTheme.typography.titleMedium)
                Text(listOfNotNull(lead.phone, lead.stage).joinToString(" • ").ifBlank { "Sem detalhes" })
            } } }
        }
    }
}

private data class Lead(val name: String, val phone: String?, val stage: String?)

private class SupabaseApi(private val url: String, private val key: String, private val client: OkHttpClient = OkHttpClient()) {
    fun isConfigured() = url.startsWith("https://") && key.isNotBlank()
    fun signIn(email: String, password: String): String {
        val body = JSONObject().put("email", email.trim()).put("password", password).toString()
            .toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder().url("${url.trimEnd('/')}/auth/v1/token?grant_type=password")
            .header("apikey", key).post(body).build()
        return client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IOException(error(text, response.code))
            JSONObject(text).optString("access_token").ifBlank { throw IOException("O Supabase não retornou uma sessão válida.") }
        }
    }
    fun leads(token: String): List<Lead> {
        val request = Request.Builder().url("${url.trimEnd('/')}/rest/v1/leads?select=*&order=updated_at.desc&limit=100")
            .header("apikey", key).header("Authorization", "Bearer $token").build()
        return client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IOException(error(text, response.code))
            val result = JSONArray(text)
            List(result.length()) { index ->
                val item = result.getJSONObject(index)
                Lead(item.first("nome", "name", "nome_lead", "email").ifBlank { "Lead sem nome" }, item.first("telefone", "phone", "celular").ifBlank { null }, item.first("status", "status_kanban", "etapa").ifBlank { null })
            }
        }
    }
    private fun JSONObject.first(vararg names: String) = names.firstNotNullOfOrNull { optString(it).takeIf(String::isNotBlank) }.orEmpty()
    private fun error(text: String, code: Int) = try { JSONObject(text).optString("msg").ifBlank { JSONObject(text).optString("message") } } catch (_: Exception) { "Erro do Supabase ($code)." }
}
