package com.github.onozaty.sample.mapper;

import com.github.onozaty.sample.domain.RefreshToken;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface RefreshTokenMapper {

  @Insert(
      """
      INSERT INTO refresh_tokens (session_id, token_hash, expires_at, created_at)
      VALUES (#{sessionId}, #{tokenHash}, #{expiresAt}, CURRENT_TIMESTAMP)
      """)
  void insert(
      @Param("sessionId") String sessionId,
      @Param("tokenHash") String tokenHash,
      @Param("expiresAt") OffsetDateTime expiresAt);

  @Select(
      """
      SELECT *
      FROM refresh_tokens
      WHERE token_hash = #{tokenHash}
      """)
  Optional<RefreshToken> findByTokenHash(String tokenHash);

  @Delete(
      """
      DELETE FROM refresh_tokens
      WHERE token_hash = #{tokenHash}
      """)
  int deleteByTokenHash(String tokenHash);
}
