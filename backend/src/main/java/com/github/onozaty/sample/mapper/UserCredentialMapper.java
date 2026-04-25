package com.github.onozaty.sample.mapper;

import java.util.Optional;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface UserCredentialMapper {

  @Insert(
      """
      INSERT INTO user_credentials (user_id, password_hash, created_at, updated_at)
      VALUES (#{userId}, #{passwordHash}, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
      """)
  void insert(@Param("userId") long userId, @Param("passwordHash") String passwordHash);

  @Update(
      """
      UPDATE user_credentials
      SET password_hash = #{passwordHash},
          updated_at = CURRENT_TIMESTAMP
      WHERE user_id = #{userId}
      """)
  int updatePassword(@Param("userId") long userId, @Param("passwordHash") String passwordHash);

  @Select(
      """
      SELECT password_hash
      FROM user_credentials
      WHERE user_id = #{userId}
      """)
  Optional<String> findPasswordHashByUserId(long userId);
}
