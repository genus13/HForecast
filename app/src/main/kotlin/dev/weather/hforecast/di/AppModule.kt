package dev.weather.hforecast.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import dev.weather.core.ProviderId
import dev.weather.core.WeatherProvider
import dev.weather.database.ForecastCacheDao
import dev.weather.database.WeatherDatabase
import dev.weather.database.WeatherDatabaseFactory
import dev.weather.ensemble.ConfigurableWeightProvider
import dev.weather.ensemble.AdaptiveEnsembleController
import dev.weather.ensemble.AdaptiveEnsembleModel
import dev.weather.ensemble.EnsembleWeightProvider
import dev.weather.ensemble.WeightedEnsembleEngine
import dev.weather.location.AndroidLocationFactory
import dev.weather.location.LocationClient
import dev.weather.location.LocationStore
import dev.weather.location.PreferencesLocationStore
import dev.weather.providers.openmeteo.OpenMeteoApi
import dev.weather.providers.openmeteo.OpenMeteoClientFactory
import dev.weather.providers.openmeteo.OpenMeteoModel
import dev.weather.providers.openmeteo.OpenMeteoWeatherProvider
import dev.weather.settings.PreferencesSettingsRepository
import dev.weather.settings.SettingsRepository
import dev.weather.environment.AirQualityRepository
import dev.weather.environment.SpaceWeatherRepository
import dev.weather.verification.ObservationProvider
import dev.weather.verification.SmhiObservationProvider
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class BaselineEnsemble

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun airQualityRepository(): AirQualityRepository = AirQualityRepository()

    @Provides
    @Singleton
    fun spaceWeatherRepository(): SpaceWeatherRepository = SpaceWeatherRepository()

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): WeatherDatabase =
        WeatherDatabaseFactory.create(context)

    @Provides
    fun forecastCacheDao(database: WeatherDatabase): ForecastCacheDao = database.forecastCacheDao()

    @Provides
    @Singleton
    fun observationProvider(): ObservationProvider = SmhiObservationProvider()

    @Provides
    @Singleton
    fun openMeteoApi(): OpenMeteoApi = OpenMeteoClientFactory.create()

    @Provides
    @IntoSet
    @Singleton
    fun ecmwfProvider(api: OpenMeteoApi): WeatherProvider = OpenMeteoWeatherProvider(
        ProviderId.ECMWF,
        OpenMeteoModel.ECMWF,
        api,
    )

    @Provides
    @IntoSet
    @Singleton
    fun iconProvider(api: OpenMeteoApi): WeatherProvider = OpenMeteoWeatherProvider(
        ProviderId.ICON,
        OpenMeteoModel.ICON,
        api,
    )

    @Provides
    @IntoSet
    @Singleton
    fun gfsProvider(api: OpenMeteoApi): WeatherProvider = OpenMeteoWeatherProvider(
        ProviderId.GFS,
        OpenMeteoModel.GFS,
        api,
    )

    @Provides
    @IntoSet
    @Singleton
    fun metNordicProvider(api: OpenMeteoApi): WeatherProvider = OpenMeteoWeatherProvider(
        ProviderId.MET_NORWAY,
        OpenMeteoModel.MET_NORDIC,
        api,
    )

    @Provides
    @Singleton
    fun starterWeights(): ConfigurableWeightProvider = ConfigurableWeightProvider.nordicStarter()

    @Provides
    @Singleton
    fun adaptiveModel(starter: ConfigurableWeightProvider): AdaptiveEnsembleController =
        AdaptiveEnsembleModel(starter)

    @Provides
    @Singleton
    fun ensembleWeights(adaptive: AdaptiveEnsembleController): EnsembleWeightProvider = adaptive

    @Provides
    @Singleton
    fun ensembleEngine(
        weights: EnsembleWeightProvider,
        adaptive: AdaptiveEnsembleController,
    ): WeightedEnsembleEngine = WeightedEnsembleEngine(weights, adaptive)

    @Provides
    @Singleton
    @BaselineEnsemble
    fun baselineEnsembleEngine(starter: ConfigurableWeightProvider): WeightedEnsembleEngine =
        WeightedEnsembleEngine(starter)

    @Provides
    @Singleton
    fun locationStore(@ApplicationContext context: Context): LocationStore =
        PreferencesLocationStore(context)

    @Provides
    @Singleton
    fun settingsRepository(@ApplicationContext context: Context): SettingsRepository =
        PreferencesSettingsRepository(context)

    @Provides
    @Singleton
    fun locationClient(
        @ApplicationContext context: Context,
        locationStore: LocationStore,
    ): LocationClient = AndroidLocationFactory.create(context, locationStore)
}
