package com.example.tradejournal

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.os.Bundle
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.spec.MGF1ParameterSpec
import java.security.spec.X509EncodedKeySpec
import java.util.concurrent.Executors
import javax.crypto.Cipher
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val journal = TradeStore(this)
        setContent { MaterialTheme { JournalApp(journal) } }
    }
}

data class Trade(val id: Long, val symbol: String, val side: String, val entry: Double, val exit: Double, val quantity: Double, val pnl: Double)

private class TradeStore(context: android.content.Context) : SQLiteOpenHelper(context, "journal.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE trades (id INTEGER PRIMARY KEY AUTOINCREMENT, symbol TEXT NOT NULL, side TEXT NOT NULL, entry REAL NOT NULL, exit REAL NOT NULL, quantity REAL NOT NULL)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    fun all(): List<Trade> {
        val result = mutableListOf<Trade>()
        readableDatabase.rawQuery("SELECT id,symbol,side,entry,exit,quantity FROM trades ORDER BY id DESC", null).use { c ->
            while (c.moveToNext()) {
                val side = c.getString(2)
                val entry = c.getDouble(3)
                val exit = c.getDouble(4)
                val quantity = c.getDouble(5)
                val pnl = (exit - entry) * quantity * if (side.equals("SHORT", true)) -1 else 1
                result += Trade(c.getLong(0), c.getString(1), side, entry, exit, quantity, pnl)
            }
        }
        return result
    }
    fun add(symbol: String, side: String, entry: Double, exit: Double, quantity: Double) {
        val values = ContentValues().apply {
            put("symbol", symbol.uppercase()); put("side", side); put("entry", entry); put("exit", exit); put("quantity", quantity)
        }
        writableDatabase.insertOrThrow("trades", null, values)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun JournalApp(store: TradeStore) {
    var trades by remember { mutableStateOf(store.all()) }
    var showAdd by remember { mutableStateOf(false) }
    var showConnect by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Nur Leseberechtigungen für Börsen-API-Schlüssel verwenden.") }
    val executor = remember { Executors.newSingleThreadExecutor() }

    Scaffold(topBar = { TopAppBar(title = { Text("Trade Journal") }) }, floatingActionButton = {
        FloatingActionButton(onClick = { showAdd = true }) { Text("+") }
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text("Dein Trading-Tagebuch", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(6.dp))
                Text("Lokale Einträge: ${trades.size} · Geschlossene Trades")
                Spacer(Modifier.height(10.dp))
                Card { Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("Realisierter P&L (manuell erfasst)", style = MaterialTheme.typography.titleMedium)
                    Text(trades.sumOf { it.pnl }.formatMoney(), style = MaterialTheme.typography.headlineMedium)
                } }
                Spacer(Modifier.height(10.dp))
                Button(onClick = { showConnect = true }, modifier = Modifier.fillMaxWidth()) { Text("Börse sicher verbinden") }
                Text(status, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                Spacer(Modifier.height(12.dp))
                Text("Journal", style = MaterialTheme.typography.titleLarge)
            }
            if (trades.isEmpty()) item { Text("Noch keine Trades. Tippe auf +, um einen Eintrag anzulegen.") }
            items(trades, key = { it.id }) { trade ->
                Card { Column(Modifier.fillMaxWidth().padding(14.dp)) {
                    Text("${trade.symbol} · ${trade.side}", style = MaterialTheme.typography.titleMedium)
                    Text("Einstieg ${trade.entry} · Ausstieg ${trade.exit} · Menge ${trade.quantity}")
                    Text("P&L: ${trade.pnl.formatMoney()}")
                } }
            }
        }
    }
    if (showAdd) AddTradeDialog(onDismiss = { showAdd = false }, onSave = { symbol, side, entry, exit, qty ->
        store.add(symbol, side, entry, exit, qty)
        trades = store.all()
        showAdd = false
    })
    if (showConnect) ConnectDialog(onDismiss = { showConnect = false }, onConnect = { exchange, apiKey, secret ->
        showConnect = false
        status = "Schlüssel werden verschlüsselt übertragen …"
        executor.execute {
            val result = runCatching { BackendApi.connect(exchange, apiKey, secret) }.fold(
                onSuccess = { "Backend-Antwort: $it" },
                onFailure = { "Verbindung nicht möglich: ${it.message ?: "unbekannter Fehler"}" }
            )
            runOnUiThread { status = result }
        }
    })
}

@Composable
private fun AddTradeDialog(onDismiss: () -> Unit, onSave: (String, String, Double, Double, Double) -> Unit) {
    var symbol by remember { mutableStateOf("") }
    var side by remember { mutableStateOf("LONG") }
    var entry by remember { mutableStateOf("") }
    var exit by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Trade erfassen") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(symbol, { symbol = it }, label = { Text("Symbol, z. B. BTCUSDT") }, singleLine = true)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = side == "LONG", onClick = { side = "LONG" }, label = { Text("Long") })
                FilterChip(selected = side == "SHORT", onClick = { side = "SHORT" }, label = { Text("Short") })
            }
            OutlinedTextField(entry, { entry = it }, label = { Text("Einstiegspreis") }, singleLine = true)
            OutlinedTextField(exit, { exit = it }, label = { Text("Ausstiegspreis") }, singleLine = true)
            OutlinedTextField(quantity, { quantity = it }, label = { Text("Menge") }, singleLine = true)
            if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error)
        }
    }, confirmButton = { TextButton(onClick = {
        val values = listOf(entry.toDoubleOrNull(), exit.toDoubleOrNull(), quantity.toDoubleOrNull())
        if (symbol.isBlank() || values.any { it == null } || values.any { it!! <= 0.0 }) error = "Bitte Symbol und gültige positive Zahlen eingeben."
        else onSave(symbol.trim(), side, values[0]!!, values[1]!!, values[2]!!)
    }) { Text("Speichern") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } })
}

@Composable
private fun ConnectDialog(onDismiss: () -> Unit, onConnect: (String, String, String) -> Unit) {
    var exchange by remember { mutableStateOf("binance") }
    var apiKey by remember { mutableStateOf("") }
    var secret by remember { mutableStateOf("") }
    var warning by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Börse verbinden") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Verwende ausschließlich API-Schlüssel ohne Auszahlungs- oder Handelsrechte.")
            OutlinedTextField(exchange, { exchange = it }, label = { Text("Börse (z. B. binance)") }, singleLine = true)
            OutlinedTextField(apiKey, { apiKey = it }, label = { Text("API-Key") }, singleLine = true)
            OutlinedTextField(secret, { secret = it }, label = { Text("Secret-Key") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
            if (warning.isNotEmpty()) Text(warning, color = MaterialTheme.colorScheme.error)
        }
    }, confirmButton = { TextButton(onClick = {
        if (exchange.isBlank() || apiKey.isBlank() || secret.isBlank()) warning = "Alle Felder sind erforderlich."
        else onConnect(exchange.trim().lowercase(), apiKey, secret)
    }) { Text("Verschlüsselt senden") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } })
}

private object BackendApi {
    fun connect(exchange: String, apiKey: String, secret: String): String {
        require(BuildConfig.BACKEND_BASE_URL.startsWith("https://")) { "Backend nicht konfiguriert. Erforderlich ist eine HTTPS-URL." }
        require(BuildConfig.BACKEND_PUBLIC_KEY_PEM.isNotBlank()) { "Backend-Public-Key ist nicht konfiguriert." }
        val payload = JSONObject().apply {
            put("exchange", exchange)
            put("key_id", BuildConfig.BACKEND_KEY_ID)
            put("api_key_encrypted", encrypt(apiKey))
            put("secret_encrypted", encrypt(secret))
        }
        val url = URL(BuildConfig.BACKEND_BASE_URL.trimEnd('/') + "/v1/exchanges/connections")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 20_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
        }
        return try {
            connection.outputStream.use { it.write(payload.toString().toByteArray(StandardCharsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            check(code in 200..299) { "HTTP $code: $body" }
            body.ifBlank { "Verbindung angelegt." }
        } finally { connection.disconnect() }
    }

    private fun encrypt(value: String): String {
        val pem = BuildConfig.BACKEND_PUBLIC_KEY_PEM
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replace(Regex("\\s"), "")
        val bytes = Base64.decode(pem, Base64.DEFAULT)
        val key = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(bytes))
        val cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding")
        cipher.init(Cipher.ENCRYPT_MODE, key, OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT))
        return Base64.encodeToString(cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8)), Base64.NO_WRAP)
    }
}

private fun Double.formatMoney() = "%.2f".format(this)
