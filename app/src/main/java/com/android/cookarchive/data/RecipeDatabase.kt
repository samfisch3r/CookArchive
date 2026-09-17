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
import com.android.cookarchive.data.entities.MealPlan
import com.android.cookarchive.data.entities.Recipe
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

@Database(
    entities = [
        Recipe::class, 
        Ingredient::class, 
        InstructionStep::class, 
        MealPlan::class, 
        ShoppingListItem::class
    ],
    version = 4,
    autoMigrations = [
        AutoMigration(from = 2, to = 3)
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
                .addMigrations(MIGRATION_3_4)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
