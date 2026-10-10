package com.android.cookarchive.data

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.android.cookarchive.data.dao.RecipeDao
import com.android.cookarchive.data.entities.Ingredient
import com.android.cookarchive.data.entities.InstructionStep
import com.android.cookarchive.data.entities.MealHistoryItem
import com.android.cookarchive.data.entities.MealPlan
import com.android.cookarchive.data.entities.Recipe
import com.android.cookarchive.data.entities.RecipeRating
import com.android.cookarchive.data.entities.ShoppingListItem

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `meal_plans_new` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `recipeId` INTEGER,
                `customTitle` TEXT,
                `date` TEXT NOT NULL,
                `mealType` TEXT NOT NULL DEFAULT 'General',
                FOREIGN KEY(`recipeId`) REFERENCES `recipes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO `meal_plans_new` (`id`, `recipeId`, `customTitle`, `date`, `mealType`)
            SELECT `id`, `recipeId`, NULL AS `customTitle`, `date`, `mealType` FROM `meal_plans`
            """.trimIndent()
        )
        db.execSQL("DROP TABLE `meal_plans`")
        db.execSQL("ALTER TABLE `meal_plans_new` RENAME TO `meal_plans`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_meal_plans_recipeId` ON `meal_plans` (`recipeId`)")
    }
}

val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        try {
            db.execSQL("ALTER TABLE `meal_history` ADD COLUMN `recipeId` INTEGER DEFAULT NULL")
        } catch (e: Exception) {
            e.printStackTrace()
        }
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `recipe_ratings` (
                `id` TEXT NOT NULL,
                `recipeId` INTEGER,
                `title` TEXT NOT NULL,
                `rating` INTEGER NOT NULL,
                `date` TEXT NOT NULL,
                `comment` TEXT,
                `timestamp` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
    }
}

val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        try {
            db.execSQL("ALTER TABLE `meal_history` ADD COLUMN `recipeId` INTEGER DEFAULT NULL")
        } catch (e: Exception) {
            e.printStackTrace()
        }
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `recipe_ratings` (
                `id` TEXT NOT NULL,
                `recipeId` INTEGER,
                `title` TEXT NOT NULL,
                `rating` INTEGER NOT NULL,
                `date` TEXT NOT NULL,
                `comment` TEXT,
                `timestamp` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
    }
}

val MIGRATION_6_8 = object : Migration(6, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        try {
            db.execSQL("ALTER TABLE `meal_history` ADD COLUMN `recipeId` INTEGER DEFAULT NULL")
        } catch (e: Exception) {
            e.printStackTrace()
        }
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `recipe_ratings` (
                `id` TEXT NOT NULL,
                `recipeId` INTEGER,
                `title` TEXT NOT NULL,
                `rating` INTEGER NOT NULL,
                `date` TEXT NOT NULL,
                `comment` TEXT,
                `timestamp` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
    }
}

@Database(
    entities = [
        Recipe::class, 
        Ingredient::class, 
        InstructionStep::class, 
        MealPlan::class, 
        ShoppingListItem::class,
        MealHistoryItem::class,
        RecipeRating::class
    ],
    version = 8,
    autoMigrations = [
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 4, to = 5),
        AutoMigration(from = 5, to = 6)
    ],
    exportSchema = true
)
abstract class RecipeDatabase : RoomDatabase() {
    abstract fun recipeDao(): RecipeDao

    companion object {
        @Volatile
        private var INSTANCE: RecipeDatabase? = null

        fun getDatabase(context: Context): RecipeDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    RecipeDatabase::class.java,
                    "recipe_database"
                )
                .addMigrations(MIGRATION_3_4, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_6_8)
                .fallbackToDestructiveMigration(true)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
