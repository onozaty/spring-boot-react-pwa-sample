package com.github.onozaty.sample.mapper;

import com.github.onozaty.sample.domain.Session;
import java.util.Optional;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface SessionMapper {

  @Insert(
      """
      INSERT INTO sessions (id, user_id, created_at, last_used_at)
      VALUES (#{id}, #{userId}, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
      """)
  void insert(@Param("id") String id, @Param("userId") long userId);

  @Select(
      """
      SELECT *
      FROM sessions
      WHERE id = #{id}
      """)
  Optional<Session> findById(String id);

  @Update(
      """
      UPDATE sessions
      SET last_used_at = CURRENT_TIMESTAMP
      WHERE id = #{id}
      """)
  int touch(String id);

  @Delete(
      """
      DELETE FROM sessions
      WHERE id = #{id}
      """)
  int deleteById(String id);

  @Delete(
      """
      DELETE FROM sessions
      WHERE user_id = #{userId}
        AND id <> #{exceptId}
      """)
  int deleteByUserIdExcept(@Param("userId") long userId, @Param("exceptId") String exceptId);

  // 有効な refresh_token を 1 件も持たない session を削除する。
  // refresh_tokens は session_id への FK 制約 (ON DELETE CASCADE) を持つため、
  // session の削除に追従して期限切れ refresh_token も消える。
  @Delete(
      """
      DELETE FROM sessions
      WHERE NOT EXISTS (
        SELECT 1
        FROM refresh_tokens r
        WHERE r.session_id = sessions.id
          AND r.expires_at >= CURRENT_TIMESTAMP
      )
      """)
  int deleteExpired();
}
