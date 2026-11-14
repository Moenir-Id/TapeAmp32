package com.projectzero.tapeamp32.desktop

import com.projectzero.tapeamp32.desktop.audio.AudioMetadata
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import javax.swing.JFileChooser
import javax.swing.JOptionPane
import javax.swing.UIManager

/** Dialog file/folder native untuk menggantikan SAF (OpenDocument/CreateDocument/OpenDocumentTree). */
object DesktopDialogs {

    init {
        // Tampilan JFileChooser mengikuti Windows (bukan Metal bawaan Swing).
        runCatching { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()) }
    }

    fun pickAudioFiles(parent: Frame): List<File> {
        val d = FileDialog(parent, "Open audio file(s)", FileDialog.LOAD).apply {
            isMultipleMode = true
            setFilenameFilter { _, name -> name.substringAfterLast('.', "").lowercase() in AudioMetadata.audioExtensions }
            isVisible = true
        }
        return d.files?.toList().orEmpty()
    }

    fun pickFolder(parent: Frame): File? {
        val chooser = JFileChooser().apply {
            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            dialogTitle = "Choose music folder"
        }
        return if (chooser.showOpenDialog(parent) == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
    }

    fun pickJsonToOpen(parent: Frame): File? {
        val d = FileDialog(parent, "Open JSON", FileDialog.LOAD).apply {
            setFilenameFilter { _, name -> name.lowercase().endsWith(".json") }
            isVisible = true
        }
        val name = d.file ?: return null
        return File(d.directory, name)
    }

    fun pickJsonToSave(parent: Frame, suggestedName: String): File? {
        val d = FileDialog(parent, "Save JSON", FileDialog.SAVE).apply {
            file = suggestedName
            isVisible = true
        }
        val name = d.file ?: return null
        return File(d.directory, if (name.lowercase().endsWith(".json")) name else "$name.json")
    }

    fun info(parent: Frame, message: String) {
        JOptionPane.showMessageDialog(parent, message, "TapeAmp 32", JOptionPane.INFORMATION_MESSAGE)
    }
}
