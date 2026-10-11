# CookArchive 🍳

CookArchive is a fully-featured Android application built with modern Jetpack Compose designed to help you manage your recipes, plan your meals, and track your grocery shopping. It leverages Gemini AI to import recipes effortlessly from photos or PDFs, and features a built-in household web sync so your family always knows what's for dinner.

## ✨ Features

*   **📖 Recipe Management**: Store, edit, and organize all your favorite recipes in one place using a local Room database (works 100% offline).
*   **🔍 Ingredient Search**: Search recipes instantly by ingredient name, recipe title, or category. Matching ingredients are highlighted on the recipe cards.
*   **📐 Serving Size Scaling & Memory**: Adjust serving sizes using an interactive slider. The app remembers your preferred slider position per recipe (without altering the base recipe default servings) and scales ingredient quantities in Cooking Mode, Menu Plan, and Shopping List.
*   **🤖 AI Recipe Scanner**: Import recipes instantly by picking photos or PDFs from your device. Gemini AI extracts the title, ingredients, and instruction steps seamlessly.
*   **🌐 Web Scraper**: Paste a URL from popular recipe websites and let the app automatically extract the ingredients and steps using standard `schema.org/Recipe` formatting.
*   **📅 Household Menu Plan**: Schedule meals up to 5 days in advance, with support for free-text custom notes.
*   **☁️ Live Web Sync & Meal History**: Automatically syncs your Menu Plan to cloud storage (JSONBin), letting household members view the live meal plan and the last 7 cooked meals (with dates) from any web browser.
*   **⭐ Web Ratings & Highest Rated Sorting**: Household members can rate Today's meals and Past History meals on the website using an interactive 5-star rating control. Ratings sync back to the app, allowing you to sort your recipe catalog by "Highest Rated".
*   **🛒 Interactive Shopping List**: Add a planned recipe to your shopping list and the app intelligently scales and merges ingredient quantities (e.g., `250g + 500g = 750g`). Add custom items, check off what you've bought, and clear your cart with one tap.
*   **🧑‍🍳 Cooking Mode**: A distraction-free, full-screen step-by-step cooking pager with an ingredients quick-sheet scaled to your serving size, keeping your screen awake while your hands are messy.
*   **📊 Auto-Tracking**: The app automatically tracks how many times you've cooked a meal and when you last made it based on your Menu Plan history.
*   **📦 Backup & Restore**: Export recipes and ratings to a ZIP file, with smart in-place imports that protect your active menu plan.

## 🚀 Tech Stack

*   **100% Kotlin**
*   **UI**: Jetpack Compose & Material 3
*   **Architecture**: MVVM (Model-View-ViewModel)
*   **Database**: Room SQLite (with non-destructive migrations & schema exports)
*   **AI Vision**: Google Generative AI SDK (`google-generativeai`)
*   **Web Scraping**: Jsoup
*   **Image Loading**: Coil
*   **Cloud Sync**: JSONBin.io REST API

## 🛠️ Setup Instructions

To run this app, configure your API credentials:

1.  **Clone the repository.**
2.  **Get your API Keys:**
    *   **Gemini API Key**: Get a free API key from [Google AI Studio](https://aistudio.google.com/).
    *   **JSONBin Access Key & Bin ID**: Create a free account at [JSONBin.io](https://jsonbin.io/), create a new Bin (use `{}` as the content), and grab your Master Key and Bin ID.
3.  **Configure local.properties:**
    Add your keys to `local.properties` in the root directory:
    ```properties
    GEMINI_API_KEY=AIzaSyYourGeminiKeyHere...
    JSONBIN_ACCESS_KEY=$2a$10$YourJsonBinMasterKeyHere...
    JSONBIN_BIN_ID=YourJsonBinIdHere...
    ```
4.  **(Optional) Host the Household Website:**
    Host `web/index.html` (e.g. on Netlify or Vercel) so household members can view the live plan from any web browser.
5.  **Build and Run!**

## 📄 License

This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.
