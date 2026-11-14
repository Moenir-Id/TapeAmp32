package com.projectzero.tapeamp32.desktop.shim

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import java.io.File

/**
 * Pengganti `Context.preferencesDataStore(name = "tapeamp32_settings")` milik Android.
 * File: %APPDATA%\TapeAmp32\tapeamp32_settings.preferences_pb. SettingsRepository (disalin
 * apa adanya dari app Android, dengan satu baris diganti saat sync) memakai instance ini.
 */
object DesktopDataStore {
    val instance: DataStore<Preferences> by lazy {
        PreferenceDataStoreFactory.create(
            produceFile = { File(DesktopPaths.appDir, "tapeamp32_settings.preferences_pb") }
        )
    }
}
