// Copyright (c) 2026 Dominic Sykes. All rights reserved.
package com.emergencycard

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Contact(val name: String = "", val relation: String = "", val phone: String = "")

data class CardData(
    val fullName: String = "",
    val dob: String = "",
    val bloodType: String = "",
    val conditions: String = "",
    val allergies: String = "",
    val medications: String = "",
    val doctor: String = "",
    val insurance: String = "",
    val contacts: List<Contact> = emptyList(),
    val accounts: String = "",
    val notes: String = ""
)

/** Saving, loading and formatting the card. No passcode: the card is stored on this phone only. */
object Cards {
    private const val PREFS = "emergency_card"

    fun toJson(c: CardData): JSONObject {
        val contacts = JSONArray()
        c.contacts.forEach {
            contacts.put(JSONObject().put("name", it.name).put("relation", it.relation).put("phone", it.phone))
        }
        return JSONObject()
            .put("app", "EmergencyCard")
            .put("version", 1)
            .put("fullName", c.fullName)
            .put("dob", c.dob)
            .put("bloodType", c.bloodType)
            .put("conditions", c.conditions)
            .put("allergies", c.allergies)
            .put("medications", c.medications)
            .put("doctor", c.doctor)
            .put("insurance", c.insurance)
            .put("contacts", contacts)
            .put("accounts", c.accounts)
            .put("notes", c.notes)
    }

    fun fromJson(j: JSONObject): CardData {
        val list = ArrayList<Contact>()
        val arr = j.optJSONArray("contacts")
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(Contact(o.optString("name"), o.optString("relation"), o.optString("phone")))
            }
        }
        return CardData(
            fullName = j.optString("fullName"),
            dob = j.optString("dob"),
            bloodType = j.optString("bloodType"),
            conditions = j.optString("conditions"),
            allergies = j.optString("allergies"),
            medications = j.optString("medications"),
            doctor = j.optString("doctor"),
            insurance = j.optString("insurance"),
            contacts = list,
            accounts = j.optString("accounts"),
            notes = j.optString("notes")
        )
    }

    fun load(ctx: Context): CardData {
        val s = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("card", null) ?: return CardData()
        return try {
            fromJson(JSONObject(s))
        } catch (_: Exception) {
            CardData()
        }
    }

    fun save(ctx: Context, c: CardData) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("card", toJson(c).toString()).apply()
    }

    /** Whether other phones may read the card by tapping. Off until the owner turns it on. */
    fun nfcOn(ctx: Context): Boolean = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("nfc", false)

    fun setNfc(ctx: Context, on: Boolean) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("nfc", on).apply()
    }

    fun isEmpty(c: CardData): Boolean = toText(c).trim() == "EMERGENCY CARD"

    /** Plain-text version of the card. This is what a tapping phone receives. */
    fun toText(c: CardData): String {
        val sb = StringBuilder("EMERGENCY CARD\n")
        fun line(label: String, v: String) {
            if (v.isNotBlank()) sb.append(label).append(": ").append(v.trim()).append('\n')
        }
        line("Name", c.fullName)
        line("Date of birth", c.dob)
        line("Blood type", c.bloodType)
        line("Medical conditions", c.conditions)
        line("Allergies", c.allergies)
        line("Medications", c.medications)
        line("Doctor", c.doctor)
        line("Insurance", c.insurance)
        val cs = c.contacts.filter { it.name.isNotBlank() || it.phone.isNotBlank() }
        if (cs.isNotEmpty()) {
            sb.append("Emergency contacts:\n")
            cs.forEach {
                sb.append("  - ").append(it.name.trim())
                if (it.relation.isNotBlank()) sb.append(" (").append(it.relation.trim()).append(")")
                if (it.phone.isNotBlank()) sb.append(": ").append(it.phone.trim())
                sb.append('\n')
            }
        }
        line("Accounts and important info", c.accounts)
        line("Notes", c.notes)
        return sb.toString().trimEnd()
    }
}
