// Copyright (c) 2026 Dominic Sykes. All rights reserved.
package com.emergencycard

import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

class Model {
    var card by mutableStateOf(CardData())
    var nfcShare by mutableStateOf(false)
    var message by mutableStateOf("")
    var reading by mutableStateOf(false)
    var readResult by mutableStateOf("")
    var tab by mutableIntStateOf(0)
}

class MainActivity : ComponentActivity() {

    private val model = Model()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        model.card = Cards.load(this)
        model.nfcShare = Cards.nfcOn(this)
        if (Cards.isEmpty(model.card)) model.tab = 1 // brand new: go straight to Edit

        setContent {
            MaterialTheme {
                Screen(
                    m = model,
                    onSave = { c ->
                        model.card = c
                        Cards.save(this, c)
                        model.message = "Saved."
                        model.tab = 0
                    },
                    onNfc = { on ->
                        model.nfcShare = on
                        Cards.setNfc(this, on)
                        model.message = if (on) "Sharing is ON. Other phones can read your card when tapped."
                        else "Sharing is OFF."
                    },
                    onNfcSettings = { startActivity(Intent(Settings.ACTION_NFC_SETTINGS)) },
                    onShare = { shareText() },
                    onSaveFile = { saveFile() },
                    onDial = { dial(it) },
                    onRead = { if (model.reading) stopReading() else startReading() }
                )
            }
        }
    }

    override fun onPause() {
        stopReading()
        super.onPause()
    }

    // ---------- actions ----------

    private fun dial(phone: String) {
        startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(phone))))
    }

    private fun shareText() {
        val i = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Emergency Card")
            putExtra(Intent.EXTRA_TEXT, Cards.toText(model.card))
        }
        startActivity(Intent.createChooser(i, "Share card"))
    }

    private fun saveFile() {
        try {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, "EmergencyCard.json")
                put(MediaStore.Downloads.MIME_TYPE, "application/json")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/EmergencyCard")
            }
            val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("Cannot create the file")
            contentResolver.openOutputStream(uri)?.use {
                it.write(Cards.toJson(model.card).toString(2).toByteArray())
            }
            model.message = "Saved to Downloads/EmergencyCard/EmergencyCard.json"
        } catch (e: Exception) {
            model.message = "Could not save the file: ${e.message}"
        }
    }

    // ---------- reading someone else's card ----------

    private fun startReading() {
        val adapter = NfcAdapter.getDefaultAdapter(this)
        if (adapter == null) {
            model.message = "This phone has no NFC."
            return
        }
        if (!adapter.isEnabled) {
            model.message = "NFC is switched off. Turn it on in the NFC settings."
            return
        }
        model.readResult = ""
        model.reading = true
        model.message = "Hold the back of this phone against the other phone."
        adapter.enableReaderMode(
            this,
            { tag -> readTag(tag) },
            NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B,
            null
        )
    }

    private fun stopReading() {
        if (!model.reading) return
        model.reading = false
        try {
            NfcAdapter.getDefaultAdapter(this)?.disableReaderMode(this)
        } catch (_: Exception) {
        }
    }

    private fun readTag(tag: Tag) {
        var text: String? = null
        var problem = ""
        try {
            val ndef = Ndef.get(tag)
            if (ndef == null) {
                problem = "That isn't an Emergency Card, or sharing is switched off on the other phone."
            } else {
                ndef.connect()
                val msg = ndef.ndefMessage
                ndef.close()
                for (r in msg.records) {
                    if (r.tnf == NdefRecord.TNF_WELL_KNOWN && r.type.contentEquals(NdefRecord.RTD_TEXT)) {
                        val p = r.payload
                        val langLen = p[0].toInt() and 0x3F
                        val charset = if ((p[0].toInt() and 0x80) != 0) Charsets.UTF_16 else Charsets.UTF_8
                        text = String(p, 1 + langLen, p.size - 1 - langLen, charset)
                        break
                    }
                }
                if (text == null) problem = "The other phone didn't send a card."
            }
        } catch (e: Exception) {
            problem = "Couldn't read the card (${e.message}). Keep the phones together and try again."
        }
        val result = text
        runOnUiThread {
            if (result != null) {
                model.readResult = result
                model.message = "Card received."
                stopReading()
            } else {
                model.message = problem
            }
        }
    }
}

// ------------------------------------------------------------------ UI

@Composable
fun Screen(
    m: Model,
    onSave: (CardData) -> Unit,
    onNfc: (Boolean) -> Unit,
    onNfcSettings: () -> Unit,
    onShare: () -> Unit,
    onSaveFile: () -> Unit,
    onDial: (String) -> Unit,
    onRead: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
    ) {
        Text(
            "Emergency Card",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp)
        )
        TabRow(selectedTabIndex = m.tab) {
            listOf("My card", "Edit", "Read").forEachIndexed { i, t ->
                Tab(selected = m.tab == i, onClick = { m.tab = i }, text = { Text(t) })
            }
        }
        if (m.message.isNotEmpty()) {
            Text(
                m.message,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, top = 8.dp, end = 16.dp)
            )
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (m.tab) {
                0 -> MyCardTab(m, onNfc, onNfcSettings, onShare, onSaveFile, onDial)
                1 -> EditTab(m.card, onSave)
                else -> ReadTab(m, onRead)
            }
            Text(
                "Emergency Card  ©  2026 Dominic Sykes. All rights reserved.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
            )
        }
    }
}

@Composable
fun MyCardTab(
    m: Model,
    onNfc: (Boolean) -> Unit,
    onNfcSettings: () -> Unit,
    onShare: () -> Unit,
    onSaveFile: () -> Unit,
    onDial: (String) -> Unit
) {
    val c = m.card
    if (Cards.isEmpty(c)) {
        Text("No card yet. Open the Edit tab to fill yours in.")
    } else {
        Card(Modifier.fillMaxWidth()) {
            Text(Cards.toText(c), modifier = Modifier.padding(16.dp))
        }
        c.contacts.filter { it.phone.isNotBlank() }.forEach { k ->
            OutlinedButton(onClick = { onDial(k.phone) }, modifier = Modifier.fillMaxWidth()) {
                Text("Call " + k.name.ifBlank { k.phone })
            }
        }
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Share by tapping phones", style = MaterialTheme.typography.titleMedium)
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Let other phones read my card", modifier = Modifier.weight(1f))
                Switch(checked = m.nfcShare, onCheckedChange = onNfc)
            }
            Text(
                "When this is on, anyone who holds an NFC phone against the back of yours can read the card. " +
                    "Keep the screen on. Turn it off when you don’t need it.",
                style = MaterialTheme.typography.bodySmall
            )
            TextButton(onClick = onNfcSettings) { Text("Open NFC settings") }
        }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onShare) { Text("Share as text") }
        OutlinedButton(onClick = onSaveFile) { Text("Save file") }
    }
    Text(
        "Anyone who can unlock this phone can see the card. Don’t put passwords or full bank or card numbers in it.",
        style = MaterialTheme.typography.bodySmall
    )
}

@Composable
fun Field(
    label: String,
    value: String,
    minLines: Int = 1,
    keyboard: KeyboardType = KeyboardType.Text,
    onChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        minLines = minLines,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
fun EditTab(initial: CardData, onSave: (CardData) -> Unit) {
    var d by remember { mutableStateOf(initial) }

    Text("About me", style = MaterialTheme.typography.titleMedium)
    Field("Full name", d.fullName) { d = d.copy(fullName = it) }
    Field("Date of birth", d.dob) { d = d.copy(dob = it) }
    Field("Blood type", d.bloodType) { d = d.copy(bloodType = it) }

    Text("Medical", style = MaterialTheme.typography.titleMedium)
    Field("Medical conditions", d.conditions, 2) { d = d.copy(conditions = it) }
    Field("Allergies", d.allergies, 2) { d = d.copy(allergies = it) }
    Field("Medications and doses", d.medications, 3) { d = d.copy(medications = it) }
    Field("Doctor and phone", d.doctor) { d = d.copy(doctor = it) }
    Field("Insurance (provider and policy number)", d.insurance) { d = d.copy(insurance = it) }

    Text("Emergency contacts", style = MaterialTheme.typography.titleMedium)
    d.contacts.forEachIndexed { i, _ ->
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Field("Name", d.contacts[i].name) { v ->
                    d = d.copy(contacts = d.contacts.toMutableList().also { l -> l[i] = l[i].copy(name = v) })
                }
                Field("Relationship", d.contacts[i].relation) { v ->
                    d = d.copy(contacts = d.contacts.toMutableList().also { l -> l[i] = l[i].copy(relation = v) })
                }
                Field("Phone", d.contacts[i].phone, keyboard = KeyboardType.Phone) { v ->
                    d = d.copy(contacts = d.contacts.toMutableList().also { l -> l[i] = l[i].copy(phone = v) })
                }
                TextButton(onClick = {
                    d = d.copy(contacts = d.contacts.toMutableList().also { l -> l.removeAt(i) })
                }) { Text("Remove this contact") }
            }
        }
    }
    if (d.contacts.size < 6) {
        OutlinedButton(onClick = { d = d.copy(contacts = d.contacts + Contact()) }) { Text("Add a contact") }
    }

    Text("Other", style = MaterialTheme.typography.titleMedium)
    Field("Accounts and important info (where things are, who to call; no passwords)", d.accounts, 3) {
        d = d.copy(accounts = it)
    }
    Field("Notes", d.notes, 3) { d = d.copy(notes = it) }

    Button(onClick = { onSave(d) }, modifier = Modifier.fillMaxWidth()) { Text("Save card") }
}

@Composable
fun ReadTab(m: Model, onRead: () -> Unit) {
    Text("Read someone else’s Emergency Card", style = MaterialTheme.typography.titleMedium)
    Text(
        "Ask them to switch on “Let other phones read my card”, then tap Start and hold the back of your phone against theirs.",
    )
    Button(onClick = onRead, modifier = Modifier.fillMaxWidth()) {
        Text(if (m.reading) "Stop" else "Start reading")
    }
    if (m.readResult.isNotEmpty()) {
        Card(Modifier.fillMaxWidth()) {
            Text(m.readResult, modifier = Modifier.padding(16.dp))
        }
    }
}
