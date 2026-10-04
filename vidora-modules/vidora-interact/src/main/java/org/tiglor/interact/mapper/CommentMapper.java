package org.tiglor.interact.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.tiglor.interact.entity.Comment;

import java.util.Collection;
import java.util.List;

@Mapper
public interface CommentMapper extends BaseMapper<Comment> {

    /**
     * 一次取出多条顶层评论各自的前 {@code limit} 条回复。
     * <p>
     * 用窗口函数而不是「查全部回复再在内存里截断」：一条热门顶层评论可能有上万条回复，
     * 全捞出来会撑爆内存；也不能对每条顶层评论各查一次，一页 20 条就是 20 次往返（N+1）。
     * {@code ROW_NUMBER() OVER (PARTITION BY root_id)} 让数据库只回传每组的前 N 行。
     * </p>
     * <p>
     * is_deleted / status 必须写在 SQL 里：注解 SQL 是原生语句，MP 的逻辑删除插件
     * 不会替它追加 {@code is_deleted = 0}，漏了就会把已删评论显示出来。
     * </p>
     */
    @Select("""
            <script>
            SELECT * FROM (
                SELECT c.*, ROW_NUMBER() OVER (PARTITION BY c.root_id ORDER BY c.id) AS rn
                FROM interact_comment c
                WHERE c.is_deleted = 0
                  AND c.status = 1
                  AND c.root_id IN
                  <foreach collection="rootIds" item="rootId" open="(" separator="," close=")">#{rootId}</foreach>
            ) t
            WHERE t.rn &lt;= #{limit}
            ORDER BY t.root_id, t.rn
            </script>
            """)
    List<Comment> previewReplies(@Param("rootIds") Collection<Long> rootIds, @Param("limit") int limit);
}
