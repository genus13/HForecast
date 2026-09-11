<p align="center">
  <img src="docs/hforecast-logo.svg" width="144" alt="HForecast logo">
</p>

<h1 align="center">HForecast</h1>

<p align="center"><strong>Honest multi-model forecasting, with the uncertainty left visible.</strong></p>

<p align="center">
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-MIT-1768d2" alt="MIT License"></a>
</p>

HForecast (Honest Forecast) is a native Android/Kotlin environment app. It combines a transparent multi-model weather forecast with aurora and solar activity, a compass-oriented sky map, air quality and pollen information. All calculations remain inspectable: the app exposes the model inputs, spread and confidence rather than presenting an unexplained score.

> **Project status:** actively developed personal-use application. Forecasts and environmental estimates are not a substitute for official warnings or safety-critical guidance.

## What works

- One-shot, battery-conscious current location using the fused Android location provider. A location is reused for 30 minutes and GPS is never continuously monitored.
- Four isolated Open-Meteo requests (`metno_nordic`, `ecmwf_ifs`, `icon_seamless`, `ncep_gfs_seamless`) executed concurrently. One failure does not cancel the others.
- Provider responses normalized to UTC `Instant` timestamps plus an IANA presentation timezone. Internal units are °C, m/s, mm and hPa.
- Bounded interpolation onto a common hourly timeline.
- Variable- and horizon-specific configurable weights with renormalization when models or individual values are missing.
- Explicit range-based model spread and transparent HIGH/MEDIUM/LOW confidence rules.
- Swipeable top-level navigation plus current, full-horizon hourly, 16-day daily and per-model comparison views in Jetpack Compose.
- Technical hourly and multi-model charts open on a 24-hour viewport centred on now (12 hours past and 12 hours ahead). Pinch zoom-out reaches a 144-hour span (72 hours in either direction), horizontal panning moves through it, the visible Y scale adapts automatically, and a labelled NOW line keeps past and future unambiguous.
- The chart history comes from Open-Meteo's 72 recent archived model hours and is explicitly treated as retrospective model output, not observation data or a substitute for forecasts previously captured by HForecast. The original future-only hourly table remains available.
- The interactive model view adds corrected-value mean, median, population standard deviation, range, consensus-minus-median, effective model count and dominant weight. Matching verified skill exposes sample count, bias, MAE and RMSE.
- An interactive MapLibre map for selecting an exact point and inspecting the actual grid coordinate returned by every model. OpenFreeMap/OpenStreetMap vector styles provide roads and place labels, follow the app's light/dark appearance, and keep map gestures isolated from the scrolling information panel. MET Nordic adds approximately 1 km short-range coverage in the Nordic region.
- Latest successful run per model and exact 0.01° location bucket cached in Room. Cached data is labelled with its age and is never reused for a broad county-sized radius.
- A unique three-hour WorkManager refresh with connected-network and battery-not-low constraints. It uses the saved location and never wakes GPS in the background.
- Opt-in WorkManager notifications at a selectable 1, 3, 6 or 12 hour interval, constrained by a user-selected local-time window. Android 13+ permission is requested only when enabling them.
- A dedicated Aurora screen using NOAA SWPC's current OVATION grid, local nearest-cell probability, planetary Kp, Kp outlook, solar-wind speed, IMF Bt/Bz, 10.7 cm solar flux and NOAA G/R/S activity scales. The viewing assessment keeps the raw model probability separate from local darkness and cloud cover.
- Optional local aurora-opportunity alerts with a selectable OVATION threshold. An alert requires darkness and cloud cover below 75%, and follows the same quiet-hours window as weather notifications.
- An offline-calculated astronomy screen with solar/twilight state, Moon phase and illumination, rise/set times, planets above the horizon and a curated catalogue of bright stars. The user drags the circular chart itself and aligns its labelled N/E/S/W rim with a compass; no automatic sensor rotation is applied. Collision-aware bright-object names open an observation card when tapped.
- CAMS-backed air-quality forecasts through Open-Meteo: European AQI, PM2.5, PM10, NO2, O3, SO2, CO, dust, aerosol optical depth, UV and European pollen fields, plus a seven-day outlook.
- System, light and dark appearance modes.
- One native detailed home-screen widget. It follows Android system light/dark mode, reads the latest Room cache, shows its age and opens HForecast when the body is tapped. Its refresh symbol requests a one-off WorkManager network update using the saved location and never starts GPS.
- In-app English documentation explaining normalization, weighting, spread, precipitation probability, spatial resolution, caching and adaptive verification.
- Unit tests for conversion, timestamp alignment/interpolation, weighting, missing values, bias correction, model spread, confidence and verification metrics.

No API key is needed for Open-Meteo's non-commercial endpoint. Review its current usage/licensing policy before distributing the app or increasing traffic.

## Modules

| Module | Responsibility |
| --- | --- |
| `app` | Hilt composition root, repository orchestration, ViewModel and WorkManager |
| `weather-core` | Pure JVM provider contracts and normalized weather model |
| `weather-providers` | Pure JVM Retrofit DTOs, Open-Meteo clients and mapping |
| `weather-ensemble` | Pure JVM alignment, bias seam, weights, spread, confidence and daily aggregation |
| `weather-verification` | Pure JVM MAE, RMSE, bias, correlation, Brier score and inverse-error weights |
| `database` | Room cache schema and domain mapping |
| `location` | One-shot fused location and saved location store |
| `ui` | Compose-only presentation |
| `settings` | Persistent notification preferences independent of the UI |
| `environment` | Pure JVM air-quality and NOAA space-weather API clients and normalized models |
| `astronomy` | Pure JVM Sun, Moon, planet, bright-star and rise/set calculations |

The forecasting, environment and astronomy engines have no Android or Compose dependency.

## Forecast pipeline

```text
single GPS fix / fresh saved location
              │
              ▼
ECMWF ─┐
ICON  ─┼─ independent fetch + provider mapper
GFS   ─┤
MET Nordic ─┘
              │
              ▼
common UTC model → hourly alignment/interpolation
              │
              ▼
optional bias correction → variable/horizon weights
              │
              ▼
weighted consensus + model range + confidence
              │
              ├── Room latest-run cache + append-only verification archive
              ├── SMHI observation matching + adaptive skill update
              └── Compose current/hourly/daily/model views and technical charts
```

## Build and run

Requirements:

- JDK 17
- Android SDK Platform 36 and Build Tools 35.0.0 or newer
- Android Studio with an Android 6.0 (API 23) or newer device/emulator

From a configured shell:

```bash
export JAVA_HOME=/path/to/jdk-17
export ANDROID_HOME=/path/to/android-sdk
./gradlew test lintDebug assembleDebug
```

The APK filename always includes the application version. For example, version 0.5.3 is generated at
`app/build/outputs/apk/debug/HForecast_0.5.3.apk`.

Version 0.5.3 uses the Android application ID `dev.weather.hforecast`. Android may therefore keep a much earlier development build as a separate app. Remove the old launcher entry if both appear; its local cache is not migrated.

## Adaptive forecast learning

HForecast now archives each forecast at download time and later verifies it against quality-controlled SMHI station observations in Sweden. It learns exponentially weighted bias, MAE and RMSE independently for provider, variable, lead-time bucket, half-degree region and season. After at least 24 samples are available for every participating model, inverse-squared-RMSE weights are blended gradually with the manual starter weights, with a maximum learned share of 75%.

The adaptive consensus and the original fixed-weight consensus are both archived and verified. A context falls back to the fixed baseline if the adaptive RMSE becomes more than 2% worse. Verification requires the saved run's issue time to precede its target time, so the recent model history downloaded for chart context cannot contaminate learning. No historical model or reanalysis data is used as a substitute for forecasts that were not actually archived on the device. Outside SMHI observation coverage, HForecast continues to use its explicit starter weights.

### Add a home-screen widget

After installing HForecast and loading the first forecast, long-press an empty area of the Android home screen, choose **Widgets**, find **HForecast**, then drag **HForecast detailed** onto the home screen. Tap the top-left refresh symbol for fresh network data from the saved location; tap the rest of the widget to open the app. The displayed age makes stale data explicit.

### WSL notes

This repository includes the Gradle wrapper, but WSL still needs a Linux JDK and Linux Android SDK for command-line builds. Do not point a Linux Gradle process at Windows-only SDK executables.

Two practical setups are:

1. Open the project from Windows Android Studio through `\\wsl.localhost\<distribution>\home\<user>\<project-folder>`. Android Studio supplies its JDK/SDK and can deploy to its normal emulator or USB device. Cross-filesystem indexing may be slower.
2. Install JDK 17 and Android command-line tools inside WSL, set `JAVA_HOME` and `ANDROID_HOME`, and build with `./gradlew`. Use wireless debugging or a Windows-hosted `adb` server when the emulator/device is owned by Windows.

## Transparent ensemble behavior

Starter weights live in `ConfigurableWeightProvider.nordicStarter()`. MET Nordic has explicit short-range weights through 60 hours; ECMWF, ICON and GFS use separate 60–72 hour and longer-range rules. Precipitation and wind also differ from general variables. These values are configuration, not a claim that one model is permanently superior. `EnsembleWeightProvider` is the replacement seam for learned provider × variable × lead-time × region/season skill weights.

Confidence is deliberately simple:

- HIGH requires all expected models (four in the Nordic short range, three later) and a variable-specific tight range.
- MEDIUM requires at least two models and a broader acceptable range.
- LOW means fewer than two comparable values or excessive disagreement.

Thresholds are in `TransparentConfidencePolicy` and are unit tested.

Precipitation probability is only blended when Open-Meteo supplies a probability product. The app never converts disagreement between deterministic precipitation amounts into a fake probability.

## Aurora, astronomy and air-quality behavior

- **Aurora:** the percentage shown prominently is the nearest NOAA OVATION grid value, not a made-up HForecast probability. It covers a short 30–90 minute horizon. HForecast separately reports whether darkness and cloud cover are favourable; terrain, artificial light and obstructions are not modelled.
- **Solar activity:** Kp and the G/R/S scales describe different aspects of space weather. Solar wind and southward IMF Bz can provide context, but no single value guarantees a visible aurora.
- **Astronomy:** positions are calculated locally for the selected latitude, longitude, altitude and current instant with Astronomy Engine. A 0–359° control and N/E/S/W marks let the user align the chart manually with a separate compass reading. The bright-star list is intentionally curated, so this is an observation guide rather than a full planetarium or telescope control system.
- **Air quality:** Open-Meteo supplies CAMS model output at its returned grid coordinate. HForecast labels it as a forecast estimate, not a local regulatory measurement or medical warning.

## Current honest boundaries

- The standard Open-Meteo forecast response does not expose every native model initialization time. `modelInitializationTime` is therefore nullable, while `issuedAt` records when the app actually downloaded the forecast. It is not presented as a native run time.
- Room keeps a latest-run cache for offline presentation and a separate append-only archive for genuine forecast verification. Forecast runs are retained for 60 days; detailed observations and verification pairs are retained for 180 days, while aggregate skill persists.
- Production weights adapt only where genuine locally archived forecasts can be matched to supported quality-controlled observations. Current observation-backed learning uses SMHI stations in Sweden; other locations remain on the explicit starter configuration.
- Probabilistic ranges require real ensemble members and are intentionally deferred.
- Aurora and air-quality results are refreshed on demand but are not yet part of the Room offline cache; the core weather forecast remains the cached offline product.
- The sky catalogue contains the brightest reference stars rather than every catalogued object, and light pollution is not yet sourced.
- Multiple named saved locations and direct SMHI/MET Norway adapters are subsequent slices. The current map uses OpenFreeMap's public instance; before broad distribution, choose an appropriate hosted service or self-hosting plan and review availability requirements.

## License

HForecast is released under the [MIT License](LICENSE). You may use, copy, modify, merge, publish, distribute, sublicense and sell copies, provided that the copyright and permission notice are retained. The software is supplied without warranty.
