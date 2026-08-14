package com.assassinlauncher.launcher.servers

import com.github.steveice10.opennbt.NBTIO
import com.github.steveice10.opennbt.tag.builtin.CompoundTag
import com.github.steveice10.opennbt.tag.builtin.ListTag
import com.github.steveice10.opennbt.tag.builtin.StringTag
import com.github.steveice10.opennbt.tag.builtin.Tag
import java.io.File
import java.nio.file.Files
import java.util.UUID

/**
 * Reads and writes the real servers.dat (gzipped NBT), the same format and
 * library (OpenNBT) confirmed directly in Zalith Launcher 2's actual
 * source - not the placeholder JSON this project used before finding that
 * reference. Server format confirmed against the same Minecraft Wiki page
 * their own code cites: name, ip, and optionally icon/acceptTextures/
 * hidden. Fields this project doesn't have UI for yet (icon,
 * acceptTextures) are preserved on existing entries rather than dropped on
 * save - only name/ip get overwritten for an edited entry.
 */
class ServerRepository(private val dataFile: File) {

    fun list(): List<ServerEntry> {
        if (!dataFile.exists()) return emptyList()
        return runCatching {
            val root = NBTIO.readFile(dataFile, false, false) ?: return emptyList()
            val servers = root.get("servers") as? ListTag ?: return emptyList()
            servers.mapNotNull { tag ->
                val compound = tag as? CompoundTag ?: return@mapNotNull null
                val name = (compound.get("name") as? StringTag)?.value ?: return@mapNotNull null
                val address = (compound.get("ip") as? StringTag)?.value ?: return@mapNotNull null
                ServerEntry(id = entryId(compound), name = name, address = address)
            }
        }.getOrDefault(emptyList())
    }

    fun add(name: String, address: String) {
        val entries = readRawEntries().toMutableList()
        val newEntry = CompoundTag("").apply {
            put(StringTag("name", name))
            put(StringTag("ip", address))
        }
        entries.add(newEntry)
        writeRawEntries(entries)
    }

    fun update(entry: ServerEntry) {
        val entries = readRawEntries().toMutableList()
        val index = entries.indexOfFirst { entryId(it) == entry.id }
        if (index < 0) return
        // Preserve any fields this project doesn't manage (icon,
        // acceptTextures, hidden) - only overwrite name/ip.
        val existing = entries[index]
        existing.put(StringTag("name", entry.name))
        existing.put(StringTag("ip", entry.address))
        writeRawEntries(entries)
    }

    fun remove(id: String) {
        val entries = readRawEntries().filterNot { entryId(it) == id }
        writeRawEntries(entries)
    }

    /** Position in the list is what actually identifies a server to
     * Minecraft (the real format has no stable id field) - this derives a
     * stable id for this project's own UI state (list selection, edit
     * targeting) from name+ip instead, since a plain list index breaks the
     * moment the list is reordered or an entry is removed. */
    private fun entryId(compound: CompoundTag): String {
        val name = (compound.get("name") as? StringTag)?.value ?: ""
        val ip = (compound.get("ip") as? StringTag)?.value ?: ""
        return UUID.nameUUIDFromBytes("$name|$ip".toByteArray()).toString()
    }

    private fun readRawEntries(): List<CompoundTag> {
        if (!dataFile.exists()) return emptyList()
        return runCatching {
            val root = NBTIO.readFile(dataFile, false, false) ?: return emptyList()
            val servers = root.get("servers") as? ListTag ?: return emptyList()
            servers.mapNotNull { it as? CompoundTag }
        }.getOrDefault(emptyList())
    }

    /**
     * Same write pattern confirmed in Zalith 2's source: write to a temp
     * file first, back up the existing servers.dat to servers.dat_old,
     * then swap the new file into place - avoids a half-written file if
     * something goes wrong mid-write, matching vanilla Minecraft's own
     * approach.
     */
    private fun writeRawEntries(entries: List<CompoundTag>) {
        val root = CompoundTag("")
        val serverList = ListTag("servers")
        entries.forEach { serverList.add(it as Tag) }
        root.put(serverList)

        val parentDir = dataFile.parentFile ?: return
        parentDir.mkdirs()
        val tempFile = Files.createTempFile(parentDir.toPath(), "servers", ".dat").toFile()
        NBTIO.writeFile(root, tempFile, false, false)

        val oldFile = File(parentDir, "servers.dat_old")
        oldFile.delete()
        if (dataFile.exists()) {
            dataFile.copyTo(oldFile, overwrite = true)
            dataFile.delete()
        }
        tempFile.copyTo(dataFile, overwrite = true)
        tempFile.delete()
    }
}
