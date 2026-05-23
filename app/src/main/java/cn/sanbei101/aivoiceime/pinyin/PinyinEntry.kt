package cn.sanbei101.aivoiceime.pinyin

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "pinyin_dict",
    indices = [Index(value = ["pinyin"], name = "idx_pinyin")]
)
data class PinyinEntry(
    @PrimaryKey val id: Long,
    val word: String?,
    val pinyin: String?,
    @ColumnInfo(name = "frequency") val frequency: Double?
)
