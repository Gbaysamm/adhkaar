package org.adhkaar.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

/** A dua the user wrote. It can join the morning and/or evening session. */
@Serializable
data class UserDua(
    val id: String = "user_" + UUID.randomUUID().toString().take(8),
    val title: String,
    val arabic: String = "",
    val transliteration: String = "",
    val translation: String = "",
    val count: Int = 1,
    val inMorning: Boolean = false,
    val inEvening: Boolean = false,
) {
    val isValid get() = title.isNotBlank() && (arabic.isNotBlank() || translation.isNotBlank())

    fun toSessionDhikr() = SessionDhikr(
        id = id,
        title = title,
        count = count,
        arabic = arabic,
        transliteration = transliteration,
        translation = translation,
        reference = "Your dua",
        virtue = null,
    )

    fun includedIn(type: SessionType) = if (type == SessionType.MORNING) inMorning else inEvening
}

class UserDuaStore private constructor(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("user_duas", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val state = MutableStateFlow(read())
    val flow: StateFlow<List<UserDua>> = state.asStateFlow()

    fun save(dua: UserDua) {
        val list = state.value.toMutableList()
        val i = list.indexOfFirst { it.id == dua.id }
        if (i >= 0) list[i] = dua else list += dua
        write(list)
    }

    fun delete(id: String) = write(state.value.filterNot { it.id == id })

    fun forSession(type: SessionType): List<SessionDhikr> =
        state.value.filter { it.includedIn(type) && it.isValid }.map { it.toSessionDhikr() }

    private fun read(): List<UserDua> =
        prefs.getString(KEY, null)?.let { runCatching { json.decodeFromString<List<UserDua>>(it) }.getOrNull() } ?: emptyList()

    private fun write(list: List<UserDua>) {
        prefs.edit().putString(KEY, json.encodeToString(list)).apply()
        state.value = list
    }

    companion object {
        private const val KEY = "duas"

        @Volatile private var instance: UserDuaStore? = null
        fun get(context: Context): UserDuaStore = instance ?: synchronized(this) {
            instance ?: UserDuaStore(context).also { instance = it }
        }
    }
}
