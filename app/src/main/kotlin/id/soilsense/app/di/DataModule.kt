package id.soilsense.app.di

import android.content.ContentResolver
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import id.soilsense.app.data.SystemWallClock
import id.soilsense.app.data.WallClock
import id.soilsense.app.data.history.HistoryRepository
import id.soilsense.app.data.history.ReadingDao
import id.soilsense.app.data.history.RoomHistoryRepository
import id.soilsense.app.data.history.SoilSenseDatabase
import id.soilsense.app.data.sensor.SoilSensorRepository
import id.soilsense.app.data.sensor.UsbSoilSensorRepository
import id.soilsense.app.data.settings.DataStoreSettingsRepository
import id.soilsense.app.data.settings.SettingsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    abstract fun bindSoilSensorRepository(impl: UsbSoilSensorRepository): SoilSensorRepository

    @Binds
    abstract fun bindSettingsRepository(impl: DataStoreSettingsRepository): SettingsRepository

    @Binds
    abstract fun bindHistoryRepository(impl: RoomHistoryRepository): HistoryRepository

    @Binds
    abstract fun bindWallClock(impl: SystemWallClock): WallClock
}

@Module
@InstallIn(SingletonComponent::class)
object StorageModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): SoilSenseDatabase =
        Room.databaseBuilder(context, SoilSenseDatabase::class.java, SoilSenseDatabase.NAME).build()

    @Provides
    fun provideReadingDao(database: SoilSenseDatabase): ReadingDao = database.readingDao()

    @Provides
    @Singleton
    fun providePreferencesDataStore(
        @ApplicationContext context: Context,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        scope = CoroutineScope(SupervisorJob() + ioDispatcher),
        produceFile = { context.preferencesDataStoreFile("sensor_settings") },
    )

    @Provides
    fun provideContentResolver(@ApplicationContext context: Context): ContentResolver = context.contentResolver
}
