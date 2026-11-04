@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package com.zenith.iptv.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import kotlinx.serialization.builtins.ListSerializer
import java.io.File
import java.util.UUID

/** Small JSON-file persistence layer exposed as StateFlows. */
class Store(context: Context) {
    private val dir = File(context.filesDir, "data").apply { mkdirs() }
    private val catalogDir = File(context.filesDir, "catalogs").apply { mkdirs() }
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Net.json

    private val _playlists = MutableStateFlow(read("playlists.json", ListSerializer(Playlist.serializer())) ?: emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists

    private val _settings = MutableStateFlow(read("settings.json", Settings.serializer()) ?: Settings())
    val settings: StateFlow<Settings> = _settings

    private val _profiles = MutableStateFlow(
        read("profiles.json", ListSerializer(Profile.serializer()))?.takeIf { it.isNotEmpty() }
            ?: listOf(Profile("default", "Me", 0xFF22D3EE))
    )
    val profiles: StateFlow<List<Profile>> = _profiles

    private val _profileData = MutableStateFlow(readProfileData(activeProfileId()))
    val profileData: StateFlow<ProfileData> = _profileData

    private val _reminders = MutableStateFlow(read("reminders.json", ListSerializer(Reminder.serializer())) ?: emptyList())
    val reminders: StateFlow<List<Reminder>> = _reminders

    private val _recordings = MutableStateFlow(read("recordings.json", ListSerializer(Recording.serializer())) ?: emptyList())
    val recordings: StateFlow<List<Recording>> = _recordings

    fun activeProfileId(): String = _settings.value.activeProfileId.ifBlank { "default" }
    fun activeProfile(): Profile = _profiles.value.firstOrNull { it.id == activeProfileId() } ?: _profiles.value.first()

    // ---- playlists ----
    fun savePlaylist(p: Playlist) {
        val list = _playlists.value.filterNot { it.id == p.id } + p
        _playlists.value = list
        write("playlists.json", ListSerializer(Playlist.serializer()), list)
    }

    fun deletePlaylist(id: String) {
        val list = _playlists.value.filterNot { it.id == id }
        _playlists.value = list
        write("playlists.json", ListSerializer(Playlist.serializer()), list)
        File(catalogDir, "$id.json").delete()
        if (_settings.value.activePlaylistId == id) updateSettings { it.copy(activePlaylistId = list.firstOrNull()?.id ?: "") }
    }

    // ---- settings ----
    fun updateSettings(block: (Settings) -> Settings) {
        val s = block(_settings.value)
        _settings.value = s
        write("settings.json", Settings.serializer(), s)
    }

    // ---- profiles ----
    fun saveProfile(p: Profile) {
        val list = _profiles.value.filterNot { it.id == p.id } + p
        _profiles.value = list
        write("profiles.json", ListSerializer(Profile.serializer()), list)
    }

    fun deleteProfile(id: String) {
        if (_profiles.value.size <= 1) return
        val list = _profiles.value.filterNot { it.id == id }
        _profiles.value = list
        write("profiles.json", ListSerializer(Profile.serializer()), list)
        File(dir, "profile_$id.json").delete()
        if (activeProfileId() == id) switchProfile(list.first().id)
    }

    fun switchProfile(id: String) {
        updateSettings { it.copy(activeProfileId = id) }
        _profileData.value = readProfileData(id)
    }

    fun newId(): String = UUID.randomUUID().toString().take(8)

    // ---- per-profile data ----
    fun updateProfileData(block: (ProfileData) -> ProfileData) {
        val d = block(_profileData.value)
        _profileData.value = d
        write("profile_${activeProfileId()}.json", ProfileData.serializer(), d)
    }

    fun toggleFavorite(key: String) = updateProfileData { d ->
        d.copy(favorites = if (key in d.favorites) d.favorites - key else listOf(key) + d.favorites)
    }

    fun addRecent(key: String) = updateProfileData { d ->
        d.copy(recentChannels = (listOf(key) + d.recentChannels.filterNot { it == key }).take(30))
    }

    fun saveProgress(p: WatchProgress) = updateProfileData { d ->
        val finished = p.durationMs > 0 && p.positionMs > p.durationMs * 0.95
        val map = if (finished) d.progress - p.key else d.progress + (p.key to p)
        // keep the 60 most recent
        val trimmed = map.values.sortedByDescending { it.updatedAt }.take(60).associateBy { it.key }
        d.copy(progress = trimmed)
    }

    private fun readProfileData(id: String) = read("profile_$id.json", ProfileData.serializer()) ?: ProfileData()

    // ---- reminders & recordings ----
    fun saveReminders(list: List<Reminder>) {
        _reminders.value = list
        write("reminders.json", ListSerializer(Reminder.serializer()), list)
    }

    @Synchronized
    fun updateRecordings(block: (List<Recording>) -> List<Recording>) {
        val list = block(_recordings.value)
        _recordings.value = list
        write("recordings.json", ListSerializer(Recording.serializer()), list)
    }

    // ---- catalog cache ----
    suspend fun saveCatalog(c: Catalog) = withContext(Dispatchers.IO) {
        runCatching {
            val f = File(catalogDir, "${c.playlistId}.json")
            val tmp = File(catalogDir, "${c.playlistId}.json.tmp")
            tmp.outputStream().buffered(64 * 1024).use { json.encodeToStream(Catalog.serializer(), c, it) }
            tmp.renameTo(f)
        }
    }

    suspend fun loadCatalog(id: String): Catalog? = withContext(Dispatchers.IO) {
        val f = File(catalogDir, "$id.json")
        if (!f.exists()) null else runCatching { f.inputStream().buffered(64 * 1024).use { json.decodeFromStream(Catalog.serializer(), it) } }.getOrNull()
    }

    // ---- io helpers ----
    private fun <T> read(name: String, ser: KSerializer<T>): T? {
        val f = File(dir, name)
        if (!f.exists()) return null
        return runCatching { json.decodeFromString(ser, f.readText()) }.getOrNull()
    }

    private fun <T> write(name: String, ser: KSerializer<T>, value: T) {
        io.launch {
            synchronized(this@Store) {
                val tmp = File(dir, "$name.tmp")
                tmp.writeText(json.encodeToString(ser, value))
                tmp.renameTo(File(dir, name))
            }
        }
    }
}
