# Δelta

Δelta is a beautiful, modern, local-first personal finance and transaction tracking application for Android. Built with Jetpack Compose, Material 3, and Kotlin, Δelta provides an intuitive interface to manage, filter, and visualize your financial transactions while keeping your data 100% private on your device.

---

## Key Features

- **Premium Modern Design**: Built from the ground up with Jetpack Compose, featuring vibrant dark mode themes, elegant typography, smooth micro-animations, and interactive components.
- **Local-First & Private**: No cloud sync, no logins, and no external trackers. Your financial records are stored securely on your device using a local Room Database.
- **Transaction Management**: Add, update, and delete transactions. Categorize expenses and income to see where your money goes.
- **Interactive Analytics**: Rich visualizations of your financial data, showing income-to-expense ratios, category distributions, and monthly trends.
- **Customizable Preferences**:
  - **Dynamic Theme**: Seamless switching between light and dark mode.
  - **Multi-Currency Support**: Support for major global currencies including USD ($), EUR (€), GBP (£), JPY (¥), CAD (CA$), AUD (AU$), INR (₹), CNY (CN¥), and more.

---

## Tech Stack & Architecture

- **Language**: [Kotlin](https://kotlinlang.org/)
- **UI Framework**: [Jetpack Compose](https://developer.android.com/compose) with Material Design 3
- **Architecture**: MVVM (Model-View-ViewModel) + Repository Pattern
- **Database**: [Room ORM](https://developer.android.com/training/data-storage/room) for structured local persistence
- **Preferences**: [Preferences DataStore](https://developer.android.com/topic/libraries/architecture/datastore) for lightweight key-value storage
- **Reactive Streams**: Kotlin [Coroutines](https://kotlinlang.org/docs/coroutines-overview.html) & [Flow](https://kotlinlang.org/docs/flow.html) for asynchronous reactive UI updates
- **Dependency Management**: Gradle Version Catalogs (`libs.versions.toml`)

---

## Getting Started

### Prerequisites
- Android Studio (Ladybug or newer recommended)
- Android SDK 31 (Android 12) or higher (Target SDK: 36)
- JDK 17 or higher

### Installation & Build

1. Clone this repository to your local machine:
   ```bash
   git clone https://github.com/your-username/delta.git
   ```
2. Open the project in Android Studio.
3. Gradle will automatically sync and download the required dependencies.
4. Connect an Android device (USB debugging enabled) or start an Emulator.
5. Click the **Run** button (green play icon) in Android Studio to build and deploy.

---

## Project Structure

```
elta/
├── app/
│   ├── src/
│   │   └── main/
│   │       ├── java/com/example/elta/
│   │       │   ├── data/                 # Room Database, DAOs, Entities, and Repository
│   │       │   ├── ui/theme/             # Theme tokens, Colors, Typography, and Palette definitions
│   │       │   ├── MainActivity.kt       # Application entry point & screen navigation
│   │       │   ├── MainViewModel.kt      # Main ViewModel managing UI state and transactions
│   │       │   ├── SettingsManager.kt    # DataStore configuration for theme and currency
│   │       │   ├── DeltaHomeScreen.kt    # Main dashboard, transaction history, and entry forms
│   │       │   └── DeltaAnalyticsScreen.kt # Interactive financial charts and analytics
│   │       └── res/                      # Icons, layouts, launcher assets, and string resources
│   └── build.gradle.kts                  # App-module build configuration
├── gradle/
│   └── libs.versions.toml                # Centralized dependency catalog
├── settings.gradle.kts                   # Project settings and plugin resolution
└── build.gradle.kts                      # Root build configuration
```

---

## Contributing

Contributions are welcome! If you have suggestions for improvements, styling tweaks, or new features:

1. Fork the Project.
2. Create your Feature Branch (`git checkout -b feature/AmazingFeature`).
3. Commit your Changes (`git commit -m 'Add some AmazingFeature'`).
4. Push to the Branch (`git push origin feature/AmazingFeature`).
5. Open a Pull Request.

---

## License

Distributed under the MIT License. See `LICENSE` for more information.
