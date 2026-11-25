package com.dincer552.dyssh.cost

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.NumberFormat
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private val CostBg = Color(0xFF080D12)
private val CostPanel = Color(0xFF121B24)
private val CostGreen = Color(0xFF38D98A)
private val CostMuted = Color(0xFF91A1B2)

private data class AzureAccount(
    val id: String, val name: String, val tenant: String, val client: String,
    val subscription: String, val resourceGroup: String, val vmName: String,
    val resourceIds: List<String>, val refreshToken: String = ""
)
private data class CostData(val currency: String, val total: Double, val estimate: Double, val days: List<Pair<String, Double>>)

class AzureCostActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme(colorScheme = darkColorScheme(primary = CostGreen, background = CostBg, surface = CostPanel)) { CostScreen { url -> runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } } } }
    }
}

@Composable
private fun CostScreen(openUrl: (String) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var accounts by remember { mutableStateOf(AzureStore.load(context)) }
    var selectedId by remember { mutableStateOf(accounts.firstOrNull()?.id.orEmpty()) }
    var showEditor by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<AzureAccount?>(null) }
    var status by remember { mutableStateOf("Azure hesabı ekleyin.") }
    var code by remember { mutableStateOf<String?>(null) }
    var verifyUrl by remember { mutableStateOf("https://microsoft.com/devicelogin") }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<CostData?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val selected = accounts.firstOrNull { it.id == selectedId }

    fun save(a: AzureAccount) {
        accounts = accounts.filterNot { it.id == a.id } + a
        selectedId = a.id
        AzureStore.save(context, accounts)
    }
    fun connect(a: AzureAccount) {
        busy = true; error = null; status = "Microsoft cihaz kodu alınıyor…"
        scope.launch {
            try {
                val dc = withContext(Dispatchers.IO) { AzureApi.deviceCode(a) }
                code = dc.first; verifyUrl = dc.second
                status = "Microsoft girişini tamamla. Kod: " + dc.first
                openUrl(dc.second)
                val refresh = AzureApi.poll(a, dc.third)
                val updated = a.copy(refreshToken = refresh)
                save(updated); code = null
                status = "Azure hesabı bağlandı; maliyet sorgulanıyor…"
                result = withContext(Dispatchers.IO) { AzureApi.monthToDate(updated) }
                status = "Azure Cost Management verisi alındı."
            } catch (e: Exception) { error = e.message ?: "Azure bağlantı hatası"; status = "Bağlantı başarısız." }
            finally { busy = false }
        }
    }
    fun refresh(a: AzureAccount) {
        if (busy) return
        busy = true; error = null
        scope.launch {
            try { result = withContext(Dispatchers.IO) { AzureApi.monthToDate(a) }; status = "Maliyet güncellendi." }
            catch (e: Exception) { error = e.message ?: "Maliyet alınamadı." }
            finally { busy = false }
        }
    }

    Column(Modifier.fillMaxSize().background(CostBg).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column { Text("AZURE MALİYETİ", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold); Text("Seçilen VM ve bağlı kaynakları", color = CostMuted, fontSize = 11.sp) }
            TextButton(onClick = { editing = null; showEditor = true }) { Text("+ HESAP") }
        }
        accounts.forEach { a ->
            Card(colors = CardDefaults.cardColors(containerColor = if (a.id == selectedId) Color(0xFF1B3029) else CostPanel)) {
                Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) { Text(a.name, color = Color.White); Text(a.vmName + " • " + a.subscription.take(8), color = CostMuted, fontSize = 10.sp) }
                    TextButton(onClick = { selectedId = a.id; result = null; error = null }) { Text(if (a.id == selectedId) "SEÇİLİ" else "SEÇ") }
                    TextButton(onClick = { editing = a; showEditor = true }) { Text("DÜZENLE") }
                }
            }
        }
        if (accounts.isEmpty()) Button(onClick = { editing = null; showEditor = true }) { Text("AZURE HESABI EKLE") }
        selected?.let { a ->
            Card(colors = CardDefaults.cardColors(containerColor = CostPanel)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(status, color = Color.White, fontSize = 12.sp)
                    if (code != null) {
                        Text("Giriş kodu: " + code, color = CostGreen, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Text(verifyUrl, color = CostMuted, fontSize = 11.sp)
                        OutlinedButton(onClick = { openUrl(verifyUrl) }) { Text("MICROSOFT GİRİŞİNİ AÇ") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { connect(a) }, enabled = !busy) { Text(if (a.refreshToken.isBlank()) "AZURE'A BAĞLAN" else "YENİDEN BAĞLAN") }
                        OutlinedButton(onClick = { refresh(a) }, enabled = !busy && a.refreshToken.isNotBlank()) { Text("YENİLE") }
                    }
                }
            }
            result?.let { d ->
                Card(colors = CardDefaults.cardColors(containerColor = CostPanel)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text("BU AY ŞU ANA KADAR", color = CostMuted, fontSize = 10.sp)
                        Text(money(d.total, d.currency), color = CostGreen, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                        Text("Ay sonu tahmini: " + money(d.estimate, d.currency), color = Color.White)
                        Text("Tahmin, bugüne kadarki günlük ortalamanın ay sonuna uzatılmasıdır; Azure resmi tahmini değildir.", color = CostMuted, fontSize = 10.sp)
                        Text("GÜNLÜK MALİYET", color = CostMuted, fontSize = 10.sp)
                        d.days.takeLast(12).forEach { p -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(p.first, color = CostMuted, fontSize = 11.sp); Text(money(p.second, d.currency), color = Color.White, fontSize = 11.sp) } }
                        Text("Azure maliyet verileri gecikmeli güncellenebilir.", color = CostMuted, fontSize = 10.sp)
                    }
                }
            }
        }
        error?.let { Text(it, color = Color(0xFFFF8A80), fontSize = 11.sp) }
        Text("Azure'da abonelik kapsamına Cost Management Reader rolü verilmeli. Refresh token Android Keystore ile şifrelenir; client secret APK'ya konmaz.", color = CostMuted, fontSize = 10.sp)
    }
    if (showEditor) AccountEditor(editing, { showEditor = false }, { save(it); showEditor = false })
}

@Composable
private fun AccountEditor(initial: AzureAccount?, dismiss: () -> Unit, save: (AzureAccount) -> Unit) {
    var name by remember(initial?.id) { mutableStateOf(initial?.name ?: "") }
    var tenant by remember(initial?.id) { mutableStateOf(initial?.tenant ?: "") }
    var client by remember(initial?.id) { mutableStateOf(initial?.client ?: "") }
    var subscription by remember(initial?.id) { mutableStateOf(initial?.subscription ?: "") }
    var group by remember(initial?.id) { mutableStateOf(initial?.resourceGroup ?: "") }
    var vm by remember(initial?.id) { mutableStateOf(initial?.vmName ?: "") }
    var ids by remember(initial?.id) { mutableStateOf(initial?.resourceIds?.joinToString("\n") ?: "") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = dismiss, title = { Text(if (initial == null) "Azure hesabı ekle" else "Azure hesabını düzenle") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text("Hesap adı") }, singleLine = true)
            OutlinedTextField(tenant, { tenant = it.trim() }, label = { Text("Tenant ID") }, singleLine = true)
            OutlinedTextField(client, { client = it.trim() }, label = { Text("Client ID (Public client)") }, singleLine = true)
            OutlinedTextField(subscription, { subscription = it.trim() }, label = { Text("Subscription ID") }, singleLine = true)
            OutlinedTextField(group, { group = it }, label = { Text("Resource Group") }, singleLine = true)
            OutlinedTextField(vm, { vm = it }, label = { Text("VM adı") }, singleLine = true)
            OutlinedTextField(ids, { ids = it }, label = { Text("VM ve dahil edilecek disk/NIC/IP Resource ID'leri (satır satır)") }, minLines = 3)
            Text("En az VM'nin tam Resource ID'si gerekli. Ek kaynakları ayrıca girerek yalnızca seçtiklerinin maliyetini toplarız.", color = CostMuted, fontSize = 10.sp)
            error?.let { Text(it, color = Color(0xFFFF8A80), fontSize = 11.sp) }
        } },
        confirmButton = { TextButton(onClick = {
            val resourceIds = ids.lines().map { it.trim() }.filter { it.startsWith("/subscriptions/", true) }.distinct()
            error = when {
                listOf(name, tenant, client, subscription, group, vm).any { it.isBlank() } -> "Tüm alanlar zorunlu."
                resourceIds.isEmpty() -> "En az bir tam Resource ID gir."
                resourceIds.any { !it.contains("/subscriptions/" + subscription + "/", true) } -> "Resource ID farklı aboneliğe ait."
                else -> null
            }
            if (error == null) save(AzureAccount(initial?.id ?: java.util.UUID.randomUUID().toString(), name.trim(), tenant, client, subscription, group.trim(), vm.trim(), resourceIds, initial?.refreshToken ?: ""))
        }) { Text("KAYDET") } },
        dismissButton = { TextButton(onClick = dismiss) { Text("İPTAL") } }, containerColor = CostPanel)
}

private object AzureApi {
    private const val SCOPE = "https://management.azure.com/user_impersonation offline_access"
    private fun postForm(url: String, fields: Map<String, String>): JSONObject {
        val body = fields.entries.joinToString("&") { URLEncoder.encode(it.key, "UTF-8") + "=" + URLEncoder.encode(it.value, "UTF-8") }.toByteArray()
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = "POST"; c.connectTimeout = 15000; c.readTimeout = 30000; c.doOutput = true
        c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        c.outputStream.use { it.write(body) }
        val status = c.responseCode
        val text = (if (status in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (status !in 200..299) throw IllegalStateException(azureError(text, status))
        return JSONObject(text)
    }
    fun deviceCode(a: AzureAccount): Triple<String, String, String> {
        val j = postForm("https://login.microsoftonline.com/" + a.tenant + "/oauth2/v2.0/devicecode", mapOf("client_id" to a.client, "scope" to SCOPE))
        return Triple(j.getString("user_code"), j.optString("verification_uri", "https://microsoft.com/devicelogin"), j.getString("device_code"))
    }
    suspend fun poll(a: AzureAccount, device: String): String {
        val until = System.currentTimeMillis() + 900000
        while (System.currentTimeMillis() < until) {
            delay(5000)
            try {
                val j = withContext(Dispatchers.IO) { postForm("https://login.microsoftonline.com/" + a.tenant + "/oauth2/v2.0/token", mapOf("grant_type" to "urn:ietf:params:oauth:grant-type:device_code", "client_id" to a.client, "device_code" to device)) }
                return j.getString("refresh_token")
            } catch (e: Exception) {
                val m = e.message.orEmpty()
                if (m.contains("authorization_pending")) continue
                if (m.contains("slow_down")) { delay(5000); continue }
                throw e
            }
        }
        throw IllegalStateException("Microsoft cihaz kodu zaman aşımına uğradı.")
    }
    private fun token(a: AzureAccount): String = postForm("https://login.microsoftonline.com/" + a.tenant + "/oauth2/v2.0/token", mapOf("client_id" to a.client, "grant_type" to "refresh_token", "refresh_token" to a.refreshToken, "scope" to SCOPE)).getString("access_token")
    fun monthToDate(a: AzureAccount): CostData {
        val filter = JSONObject().put("dimensions", JSONObject().put("name", "ResourceId").put("operator", "In").put("values", JSONArray(a.resourceIds)))
        val body = JSONObject().put("type", "ActualCost").put("timeframe", "MonthToDate").put("dataset", JSONObject()
            .put("granularity", "Daily")
            .put("aggregation", JSONObject().put("totalCost", JSONObject().put("name", "PreTaxCost").put("function", "Sum")))
            .put("grouping", JSONArray().put(JSONObject().put("type", "Dimension").put("name", "ResourceId")))
            .put("filter", filter))
        val c = URL("https://management.azure.com/subscriptions/" + a.subscription + "/providers/Microsoft.CostManagement/query?api-version=2023-11-01").openConnection() as HttpURLConnection
        c.requestMethod = "POST"; c.connectTimeout = 15000; c.readTimeout = 60000; c.doOutput = true
        c.setRequestProperty("Authorization", "Bearer " + token(a)); c.setRequestProperty("Content-Type", "application/json")
        c.outputStream.use { it.write(body.toString().toByteArray()) }
        val status = c.responseCode
        val text = (if (status in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (status !in 200..299) throw IllegalStateException(azureError(text, status))
        val props = JSONObject(text).getJSONObject("properties")
        val cols = props.getJSONArray("columns"); val rows = props.optJSONArray("rows") ?: JSONArray()
        var costCol = 0; var dateCol = -1; var currencyCol = -1
        for (i in 0 until cols.length()) {
            val n = cols.getJSONObject(i).optString("name")
            if (n.equals("PreTaxCost", true)) costCol = i
            if (n.equals("UsageDate", true)) dateCol = i
            if (n.equals("Currency", true)) currencyCol = i
        }
        var total = 0.0; var currency = "USD"; val days = linkedMapOf<String, Double>()
        for (i in 0 until rows.length()) {
            val row = rows.getJSONArray(i); val amount = row.optDouble(costCol, 0.0); total += amount
            val raw = if (dateCol >= 0) row.optString(dateCol) else "Unknown"
            val day = if (raw.length >= 8) raw.substring(0,4) + "-" + raw.substring(4,6) + "-" + raw.substring(6,8) else raw
            days[day] = (days[day] ?: 0.0) + amount
            if (currencyCol >= 0) currency = row.optString(currencyCol, currency)
        }
        val now = java.time.LocalDate.now()
        return CostData(currency, total, total / now.dayOfMonth * now.lengthOfMonth(), days.entries.map { it.key to it.value }.sortedBy { it.first })
    }
    private fun azureError(body: String, status: Int): String {
        val detail = runCatching { JSONObject(body).getJSONObject("error").optString("message") }.getOrNull().orEmpty()
        return if (detail.isBlank()) "Azure API HTTP " + status else "Azure API HTTP " + status + ": " + detail
    }
}

private object AzureStore {
    private const val PREF = "dyssh_azure_accounts"
    fun load(c: Context): List<AzureAccount> = runCatching {
        val encoded = c.getSharedPreferences(PREF, 0).getString("data", null) ?: return emptyList()
        val arr = JSONArray(AzureCipher.decrypt(encoded))
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i); val ids = o.getJSONArray("resourceIds")
            AzureAccount(o.getString("id"), o.getString("name"), o.getString("tenant"), o.getString("client"), o.getString("subscription"), o.getString("resourceGroup"), o.getString("vmName"), (0 until ids.length()).map { ids.getString(it) }, o.optString("refreshToken"))
        }
    }.getOrDefault(emptyList())
    fun save(c: Context, list: List<AzureAccount>) {
        val arr = JSONArray()
        list.forEach { a -> arr.put(JSONObject().put("id", a.id).put("name", a.name).put("tenant", a.tenant).put("client", a.client).put("subscription", a.subscription).put("resourceGroup", a.resourceGroup).put("vmName", a.vmName).put("resourceIds", JSONArray(a.resourceIds)).put("refreshToken", a.refreshToken)) }
        c.getSharedPreferences(PREF, 0).edit().putString("data", AzureCipher.encrypt(arr.toString())).apply()
    }
}
private object AzureCipher {
    private const val ALIAS = "dyssh_azure_cost_key"
    private fun key(): SecretKey {
        val ks = java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setRandomizedEncryptionRequired(true).build())
        return gen.generateKey()
    }
    fun encrypt(s: String): String { val c = Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.ENCRYPT_MODE, key()); return Base64.encodeToString(c.iv + c.doFinal(s.toByteArray()), Base64.NO_WRAP) }
    fun decrypt(s: String): String { val b = Base64.decode(s, Base64.NO_WRAP); val c = Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, b.copyOfRange(0, 12))); return String(c.doFinal(b.copyOfRange(12, b.size))) }
}
private fun money(value: Double, currency: String): String = NumberFormat.getCurrencyInstance(Locale.getDefault()).apply { this.currency = runCatching { java.util.Currency.getInstance(currency) }.getOrNull() }.format(value)
