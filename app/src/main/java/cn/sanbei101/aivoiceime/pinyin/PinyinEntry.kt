package cn.sanbei101.aivoiceime.pinyin

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "pinyin_dict",
    indices = [Index(value = ["pinyin", "frequency"], name = "idx_pinyin_freq")]
)
data class PinyinEntry(
    @PrimaryKey val id: Long,
    @ColumnInfo(name = "word") val word: String,
    @ColumnInfo(collate = ColumnInfo.NOCASE, name = "pinyin") val pinyin: String,
    @ColumnInfo(name = "frequency") val frequency: Double?
)
