# CookArchive 🍳

CookArchive is a fully-featured Android application built with modern Jetpack Compose designed to help you manage your recipes, plan your meals, and track your grocery shopping. It leverages the power of Gemini 1.5 Flash AI to import recipes effortlessly from photos or PDFs, and features a built-in household web sync so your family always knows what's for dinner.

## ✨ Features

*   **📖 Recipe Management**: Store, edit, and organize all your favorite recipes in one place using a local Room database (works 100% offline).
*   **🤖 AI Recipe Scanner**: Import recipes instantly by picking photos or PDFs from your device. Gemini 1.5 Flash Vision AI extracts the title, ingredients, and instruction steps seamlessly.
*   **🌐 Web Scraper**: Paste a URL from popular recipe websites and let the app automatically extract the ingredients and steps using standard `schema.org/Recipe` formatting.
*   **📅 Household Menu Plan**: Schedule meals up to 5 days in advance.
*   **☁️ Live Web Sync**: Automatically syncs your Menu Plan to a cloud JSONBin. You can easily build a simple HTML companion website that fetches this JSON, allowing household members to view the live meal plan from any browser.
*   **🛒 Interactive Shopping List**: Add a planned recipe to your shopping list and the app intelligently scales and merges ingredient quantities (e.g., `250g + 500g = 750g`). Add custom items, check off what you've bought, and clear your cart with one tap.
*   **🧑‍🍳 Cooking Mode**: A distraction-free, full-screen step-by-step cooking pager that keeps your screen awake while your hands are messy.
*   **📊 Auto-Tracking**: The app automatically tracks how many times you've cooked a meal and when you last made it based on your Menu Plan history. Sort your recipes to find hidden gems you haven't cooked in months!

## 🚀 Tech Stack

*   **100% Kotlin**
*   **UI**: Jetpack Compose & Material 3
*   **Architecture**: MVVM (Model-View-ViewModel)
*   **Database**: Room SQLite (with schema exports & AutoMigrations)
*   **AI Vision**: Google Generative AI SDK (`gemini-1.5-flash`)
*   **Web Scraping**: Jsoup
*   **Image Loading**: Coil
*   **Cloud Sync**: JSONBin.io REST API

## 🛠️ Setup Instructions

To run this app yourself, you need to provide your own API keys.

1.  **Clone the repository:**
    ```bash
    git clone https://github.com/your-username/CookArchive.git
    cd CookArchive
    ```

2.  **Get your API Keys:**
    *   **Gemini API Key**: Get a free API key from [Google AI Studio](https://aistudio.google.com/).
    *   **JSONBin Access Key**: Create a free account at [JSONBin.io](https://jsonbin.io/), create a new Bin (use `{}` as the content), and grab your Master Key and the Bin ID.

3.  **Configure local properties:**
    Create a file named `local.properties` in the root directory of the project and add your keys:
    ```properties
    # local.properties
    GEMINI_API_KEY="AIzaSyYourGeminiKeyHere..."
    JSONBIN_ACCESS_KEY="$2a$10$YourJsonBinMasterKeyHere..."
    ```

4.  **Update the Bin ID in the App:**
    Open `app/src/main/java/com/android/cookarchive/util/WebSyncUtil.kt` and update the `JSONBIN_URL` with your specific Bin ID:
    ```kotlin
    private const val JSONBIN_URL = "https://api.jsonbin.io/v3/b/YOUR_BIN_ID"
    ```

5.  **(Optional) Host the Household Website:**
    The `web/` folder containing `index.html` is intentionally `.gitignore`d because it requires your private JSONBin Master Key to fetch data. If you want to host the companion website (e.g. on Netlify or Vercel):
    *   Create a `web/` folder in the project root.
    *   Create an `index.html` file inside it.
    *   Write a simple HTML/JS page that fetches data from `https://api.jsonbin.io/v3/b/YOUR_BIN_ID/latest` using your `X-Access-Key` header to parse the JSON and display the menu plan.

6.  **Build and Run!**

## 📄 License

This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.