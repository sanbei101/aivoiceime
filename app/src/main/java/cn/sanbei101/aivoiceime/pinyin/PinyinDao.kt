package cn.sanbei101.aivoiceime.pinyin

import androidx.room.Dao
import androidx.room.Query

@Dao
interface PinyinDao {
    @Query(
        """
        SELECT word, MAX(frequency) AS frequency
        FROM pinyin_dict
        WHERE pinyin = :pinyin AND word IS NOT NULL
        GROUP BY word
        ORDER BY frequency DESC
        LIMIT :limit
        """
    )
    suspend fun candidates(pinyin: String, limit: Int = 12): List<PinyinCandidate>
}
