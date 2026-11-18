package com.example.tally.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.tally.TallyApp
import com.example.tally.data.backup.BackupFrequency
import com.example.tally.data.backup.BackupStatus
import com.example.tally.data.backup.FolderWithBackup
import com.example.tally.data.backup.PickedBackup
import com.example.tally.ui.tallyApp
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BackupMessage(val text: String, val canUndo: Boolean = false)

data class SettingsUiState(
    val status: BackupStatus = BackupStatus(),
    val working: Boolean = false,
    /** A picked folder that already holds a backup, waiting for the user to restore or replace it. */
    val folderWithBackup: FolderWithBackup? = null,
    /** A backup file waiting for the user to confirm restoring it. */
    val picked: PickedBackup? = null,
    val message: BackupMessage? = null,
)

class SettingsViewModel(app: TallyApp) : ViewModel() {
    private val backup = app.backup
    private val local = MutableStateFlow(SettingsUiState())

    val uiState: StateFlow<SettingsUiState> =
        combine(backup.status, local) { status, local -> local.copy(status = status) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState(status = backup.status.value))

    fun chooseFolder(tree: Uri) = work {
        val found = try {
            backup.chooseFolder(tree)
        } catch (e: SecurityException) {
            show("Tally can't use that folder. Pick another one.")
            return@work
        } catch (e: IllegalArgumentException) {
            show("Tally can't use that folder. Pick another one.")
            return@work
        }
        if (found != null) local.update { it.copy(folderWithBackup = found) } else reportBackup()
    }

    /** Keeps the picked folder and overwrites its backup with what's in the app now. */
    fun replaceFolderBackup() = resolveFolderChoice { choice ->
        backup.useFolder(choice.folder)
        reportBackup()
    }

    /** Restores the backup found in the picked folder, then keeps backing up there. */
    fun restoreFolderBackup() = resolveFolderChoice { choice ->
        val existing = choice.existing ?: return@resolveFolderChoice
        if (restoreFrom(existing)) backup.useFolder(choice.folder) else backup.cancelFolder(choice.folder)
    }

    fun cancelFolderChoice() {
        local.value.folderWithBackup?.let { backup.cancelFolder(it.folder) }
        local.update { it.copy(folderWithBackup = null) }
    }

    fun setFrequency(frequency: BackupFrequency) = backup.setFrequency(frequency)

    fun backUpNow() = work { reportBackup() }

    fun turnOff() {
        backup.turnOff()
        show("Automatic backups are off. The last backup file is still in the folder.")
    }

    fun pickBackupFile(uri: Uri) = work {
        val picked = backup.inspect(uri)
        if (picked == null) {
            show("That file isn't a Tally backup, or it's from a newer version of the app.")
        } else {
            local.update { it.copy(picked = picked) }
        }
    }

    fun confirmRestore() {
        val picked = local.value.picked ?: return
        local.update { it.copy(picked = null) }
        work { restoreFrom(picked) }
    }

    fun cancelRestore() = local.update { it.copy(picked = null) }

    fun undoRestore() = work {
        show(if (backup.undoRestore()) "Put back what was there before the restore" else "Couldn't undo the restore")
    }

    /** Writes a fresh backup and hands [send] a link to it for the share sheet. */
    fun prepareShare(send: (Uri) -> Unit) = work {
        val uri = try {
            backup.shareableBackup()
        } catch (e: IOException) {
            null
        } catch (e: IllegalStateException) {
            null
        }
        if (uri != null) send(uri) else show("Couldn't make a backup to send")
    }

    fun messageShown() = local.update { it.copy(message = null) }

    private suspend fun restoreFrom(picked: PickedBackup): Boolean {
        val restored = backup.restore(picked)
        show(if (restored) "Restored" else "Couldn't restore that backup. Nothing was changed.", canUndo = restored)
        return restored
    }

    /** Writes a backup now and says how it went. */
    private suspend fun reportBackup() {
        val written = backup.backUpNow()
        val folder = backup.status.value.folderName ?: "your folder"
        show(if (written) "Backed up to $folder" else "Couldn't write to $folder. Choose the folder again.")
    }

    private fun resolveFolderChoice(block: suspend (FolderWithBackup) -> Unit) {
        val choice = local.value.folderWithBackup ?: return
        local.update { it.copy(folderWithBackup = null) }
        work { block(choice) }
    }

    private fun show(text: String, canUndo: Boolean = false) =
        local.update { it.copy(message = BackupMessage(text, canUndo)) }

    private fun work(block: suspend () -> Unit) {
        viewModelScope.launch {
            local.update { it.copy(working = true) }
            try {
                block()
            } finally {
                local.update { it.copy(working = false) }
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { SettingsViewModel(tallyApp()) }
        }
    }
}
