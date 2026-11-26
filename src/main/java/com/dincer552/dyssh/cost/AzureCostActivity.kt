package com.dincer552.dyssh.cost

import android.app.Activity
import android.content.Context
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
internal const val DYSSH_AZURE_CLIENT_ID = "821c50df-c62c-430e-8691-10d0f005d2b6"

private data class AzureAccount(
    val id: String, val name: String, val tenant: String, val client: String,
    val subscription: String, val resourceGroup: String, val vmName: String,
    val resourceIds: List<String>,
    val refreshToken: String = "",
    val msalAccountId: String = "",
    val msalUsername: String = ""
)
private data class CostData(val currency: String, val currentMonth: Double, val previousMonth: Double)

class AzureCostActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme(colorScheme = darkColorScheme(primary = CostGreen, background = CostBg, surface = CostPanel)) { CostScreen() } }
    }
}

@Composable
private fun CostScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var accounts by remember { mutableStateOf(AzureStore.load(context)) }
    var selectedId by remember { mutableStateOf(accounts.firstOrNull()?.id.orEmpty()) }
    var showEditor by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<AzureAccount?>(null) }
    var status by remember { mutableStateOf("Azure hesabı ekleyin.") }
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
        if (busy) return
        val hostActivity = context as? Activity
        if (hostActivity == null) {
            error = "Microsoft girişini başlatmak için etkin uygulama ekranı bulunamadı."
            return
        }
        busy = true
        error = null
        result = null
        status = "Microsoft güvenli tarayıcı oturumu başlatılıyor…"
        scope.launch {
            try {
                val auth = AzureMsalAuth(context).acquireInteractive(hostActivity, a.tenant)
                val authenticated = a.copy(
                    refreshToken = "",
                    msalAccountId = auth.accountId,
                    msalUsername = auth.username
                )
                save(authenticated)
                status = "Microsoft hesabı doğrulandı; Azure maliyeti sorgulanıyor…"
                val summary = withContext(Dispatchers.IO) {
                    AzureApi.getSummary(authenticated, auth.accessToken)
                }
                result = summary
                status = "Azure Cost Management verisi alındı."
            } catch (e: Exception) {
                error = e.message ?: "Microsoft/Azure bağlantı hatası."
                status = "Bağlantı başarısız."
            } finally {
                busy = false
            }
        }
    }

    fun refresh(a: AzureAccount) {
        if (busy) return
        if (a.msalAccountId.isBlank()) {
            error = "Önce Microsoft hesabını bağla."
            return
        }
        busy = true
        error = null
        scope.launch {
            try {
                val auth = AzureMsalAuth(context).acquireSilent(
                    a.tenant, a.msalAccountId, a.msalUsername
                )
                val summary = withContext(Dispatchers.IO) {
                    AzureApi.getSummary(a, auth.accessToken)
                }
                save(a.copy(
                    refreshToken = "",
                    msalAccountId = auth.accountId,
                    msalUsername = auth.username
                ))
                result = summary
                status = "Maliyet güncellendi."
            } catch (e: Exception) {
                error = e.message ?: "Maliyet alınamadı."
                if (error.orEmpty().contains("interaction_required", ignoreCase = true) ||
                    error.orEmpty().contains("ui_required", ignoreCase = true)) {
                    error = error + " Yeniden bağlan düğmesiyle oturumu yenile."
                }
            } finally {
                busy = false
            }
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
        if (accounts.isEmpty()) {
            Text("Azure bağlantısı için hesabın hazırlandı. Bilgileri kontrol edip kaydetmen yeterli.", color = CostMuted, fontSize = 11.sp)
            Button(onClick = { editing = null; showEditor = true }) { Text("HAZIR AZURE HESABINI EKLE") }
        }
        selected?.let { a ->
            Card(colors = CardDefaults.cardColors(containerColor = CostPanel)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(status, color = Color.White, fontSize = 12.sp)
                    if (a.msalUsername.isNotBlank()) {
                        Text("Microsoft hesabı: " + a.msalUsername, color = CostMuted, fontSize = 11.sp)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { connect(a) }, enabled = !busy) {
                            Text(if (a.msalAccountId.isBlank()) "MICROSOFT İLE BAĞLAN" else "HESABI DEĞİŞTİR")
                        }
                        OutlinedButton(onClick = { refresh(a) }, enabled = !busy && a.msalAccountId.isNotBlank()) {
                            Text("YENİLE")
                        }
                    }
                }
            }
            result?.let { d ->
                Card(colors = CardDefaults.cardColors(containerColor = CostPanel), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("BU AYIN MEVCUT MALİYETİ", color = CostMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(money(d.currentMonth, d.currency), color = CostGreen, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                        Divider(color = Color(0xFF293541))
                        Text("GEÇEN AYIN MALİYETİ", color = CostMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(money(d.previousMonth, d.currency), color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.SemiBold)
                        Text("Tutarlar seçtiğin VM ve eklediğin kaynakların Azure maliyet kayıtlarıdır. Faturaya yansıyan kesin borç, vergi ve ödeme düzeltmeleri nedeniyle farklı olabilir.", color = CostMuted, fontSize = 10.sp)
                    }
                }
            }
        }
        error?.let { Text(it, color = Color(0xFFFF8A80), fontSize = 11.sp) }
        Text("Azure aboneliğinde Cost Management Reader rolü gerekli. Microsoft oturum belirteçleri MSAL'in güvenli yerel önbelleğinde yönetilir; uygulamada client secret veya elle saklanan refresh token bulunmaz.", color = CostMuted, fontSize = 10.sp)
    }
    if (showEditor) AccountEditor(editing, { showEditor = false }, { save(it); showEditor = false })
}

@Composable
private fun AccountEditor(initial: AzureAccount?, dismiss: () -> Unit, save: (AzureAccount) -> Unit) {
    var name by remember(initial?.id) { mutableStateOf(initial?.name ?: "Azure - UK South") }
    var tenant by remember(initial?.id) { mutableStateOf(initial?.tenant ?: "59c6ec38-fc8e-43e1-ac46-f849530b2fed") }
    var client by remember(initial?.id) { mutableStateOf(DYSSH_AZURE_CLIENT_ID) }
    var subscription by remember(initial?.id) { mutableStateOf(initial?.subscription ?: "b85ee923-e4da-43d4-a18b-71ba91dc0f23") }
    var group by remember(initial?.id) { mutableStateOf(initial?.resourceGroup ?: "HATTRICK-RG") }
    var vm by remember(initial?.id) { mutableStateOf(initial?.vmName ?: "hattrick-vm-uks") }
    var ids by remember(initial?.id) { mutableStateOf(initial?.resourceIds?.joinToString("\n") ?: "/subscriptions/b85ee923-e4da-43d4-a18b-71ba91dc0f23/resourceGroups/HATTRICK-RG/providers/Microsoft.Compute/virtualMachines/hattrick-vm-uks") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = dismiss, title = { Text(if (initial == null) "Azure hesabı ekle" else "Azure hesabını düzenle") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text("Hesap adı") }, singleLine = true)
            OutlinedTextField(tenant, { tenant = it.trim() }, label = { Text("Tenant ID") }, singleLine = true)
            OutlinedTextField(client, {}, label = { Text("Microsoft uygulama kimliği") }, singleLine = true, readOnly = true)
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
                !client.equals(DYSSH_AZURE_CLIENT_ID, ignoreCase = true) -> "Bu uygulama yalnızca kayıtlı DYSSH Microsoft kimliğini kullanabilir."
                resourceIds.isEmpty() -> "En az bir tam Resource ID gir."
                resourceIds.any { !it.contains("/subscriptions/" + subscription + "/", true) } -> "Resource ID farklı aboneliğe ait."
                else -> null
            }
            if (error == null) save(AzureAccount(initial?.id ?: java.util.UUID.randomUUID().toString(), name.trim(), tenant, client, subscription, group.trim(), vm.trim(), resourceIds, initial?.refreshToken ?: "", initial?.msalAccountId.orEmpty(), initial?.msalUsername.orEmpty()))
        }) { Text("KAYDET") } },
        dismissButton = { TextButton(onClick = dismiss) { Text("İPTAL") } }, containerColor = CostPanel)
}

private object AzureApi {
    fun getSummary(a: AzureAccount, accessToken: String): CostData {
        val current = queryCost(a, accessToken, "MonthToDate")
        val previous = queryCost(a, accessToken, "TheLastMonth")
        val currency = current.second.ifBlank { previous.second }
        return CostData(currency, current.first, previous.first)
    }

    private fun queryCost(a: AzureAccount, bearer: String, timeframe: String): Pair<Double, String> {
        val filter = JSONObject().put("dimensions", JSONObject().put("name", "ResourceId").put("operator", "In").put("values", JSONArray(a.resourceIds)))
        val body = JSONObject().put("type", "ActualCost").put("timeframe", timeframe)
            .put("dataset", JSONObject()
                .put("aggregation", JSONObject().put("totalCost", JSONObject().put("name", "PreTaxCost").put("function", "Sum")))
                .put("filter", filter))
        val c = URL("https://management.azure.com/subscriptions/" + a.subscription + "/providers/Microsoft.CostManagement/query?api-version=2023-11-01").openConnection() as HttpURLConnection
        c.requestMethod = "POST"
        c.connectTimeout = 15000
        c.readTimeout = 60000
        c.doOutput = true
        c.setRequestProperty("Authorization", "Bearer " + bearer)
        c.setRequestProperty("Content-Type", "application/json")
        c.outputStream.use { it.write(body.toString().toByteArray()) }
        val status = c.responseCode
        if (status == HttpURLConnection.HTTP_NO_CONTENT) return 0.0 to ""
        val text = (if (status in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (status !in 200..299) throw IllegalStateException(azureError(text, status))
        val props = JSONObject(text).getJSONObject("properties")
        val cols = props.getJSONArray("columns")
        val rows = props.optJSONArray("rows") ?: JSONArray()
        var costCol = -1
        var currencyCol = -1
        for (i in 0 until cols.length()) {
            val n = cols.getJSONObject(i).optString("name")
            if (n.equals("PreTaxCost", true) || n.equals("totalCost", true)) costCol = i
            if (n.equals("Currency", true)) currencyCol = i
        }
        if (costCol < 0) throw IllegalStateException("Azure maliyet yanıtında tutar alanı bulunamadı.")
        var total = 0.0
        var currency = ""
        for (i in 0 until rows.length()) {
            val row = rows.getJSONArray(i)
            total += row.optDouble(costCol, 0.0)
            if (currencyCol >= 0) currency = row.optString(currencyCol, currency)
        }
        return total to currency
    }

    private fun azureError(body: String, status: Int): String {
        val json = runCatching { JSONObject(body) }.getOrNull()
        val errorValue = json?.opt("error")
        val code = if (errorValue is String) errorValue else (errorValue as? JSONObject)?.optString("code").orEmpty()
        val detail = json?.optString("error_description").orEmpty().ifBlank {
            (errorValue as? JSONObject)?.optString("message").orEmpty()
        }
        val parts = listOf(code, detail).filter { it.isNotBlank() }.distinct()
        return "Azure API HTTP " + status + if (parts.isEmpty()) "" else ": " + parts.joinToString(" — ")
    }
}
private object AzureStore {
    private const val PREF = "dyssh_azure_accounts"
    fun load(c: Context): List<AzureAccount> = runCatching {
        val encoded = c.getSharedPreferences(PREF, 0).getString("data", null) ?: return emptyList()
        val arr = JSONArray(AzureCipher.decrypt(encoded))
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i); val ids = o.getJSONArray("resourceIds")
            AzureAccount(
                o.getString("id"), o.getString("name"), o.getString("tenant"), o.getString("client"),
                o.getString("subscription"), o.getString("resourceGroup"), o.getString("vmName"),
                (0 until ids.length()).map { ids.getString(it) }, o.optString("refreshToken"),
                o.optString("msalAccountId"), o.optString("msalUsername")
            )
        }
    }.getOrDefault(emptyList())
    fun save(c: Context, list: List<AzureAccount>) {
        val arr = JSONArray()
        list.forEach { a -> arr.put(JSONObject().put("id", a.id).put("name", a.name).put("tenant", a.tenant).put("client", a.client).put("subscription", a.subscription).put("resourceGroup", a.resourceGroup).put("vmName", a.vmName).put("resourceIds", JSONArray(a.resourceIds)).put("refreshToken", "").put("msalAccountId", a.msalAccountId).put("msalUsername", a.msalUsername)) }
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
private fun money(value: Double, currency: String): String {
    val formatter = if (currency.isBlank()) {
        NumberFormat.getNumberInstance(Locale.getDefault())
    } else {
        val resolvedCurrency = runCatching { java.util.Currency.getInstance(currency) }.getOrNull()
        if (resolvedCurrency == null) NumberFormat.getNumberInstance(Locale.getDefault())
        else NumberFormat.getCurrencyInstance(Locale.getDefault()).apply { this.currency = resolvedCurrency }
    }
    return formatter.format(value)
}
