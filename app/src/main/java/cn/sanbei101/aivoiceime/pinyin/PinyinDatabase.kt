package cn.sanbei101.aivoiceime.pinyin

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [PinyinEntry::class], version = 1, exportSchema = false)
abstract class PinyinDatabase : RoomDatabase() {
    abstract fun pinyinDao(): PinyinDao

    companion object {
        @Volatile
        private var instance: PinyinDatabase? = null

        fun getInstance(context: Context): PinyinDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    PinyinDatabase::class.java,
                    "pinyin.db"
                )
                    .createFromAsset("databases/pinyin.db")
                    .fallbackToDestructiveMigration(true)
                    .build()
                    .also { instance = it }
            }
        }
    }
}
