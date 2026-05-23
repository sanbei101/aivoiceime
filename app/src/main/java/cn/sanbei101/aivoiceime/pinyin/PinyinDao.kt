package cn.sanbei101.aivoiceime.pinyin

import androidx.room.Dao
import androidx.room.Query

@Dao
interface PinyinDao {
    @Query(
        """
        SELECT word, MAX(frequency) AS frequency
        FROM pinyin_dict
        -- 使用 LIKE 进行前缀匹配，输入 "zh" 可以匹配到 "zhe", "zhong" 等
        WHERE pinyin LIKE :pinyin || '%' AND word IS NOT NULL
        GROUP BY word
        ORDER BY 
            -- 优先显示拼音长度最短的
            MIN(LENGTH(pinyin)) ASC, 
            -- 其次按词频降序排列
            frequency DESC
        LIMIT :limit
        """
    )
    suspend fun candidates(pinyin: String, limit: Int = 12): List<PinyinCandidate>
}
