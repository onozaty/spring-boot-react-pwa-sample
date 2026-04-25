package com.github.onozaty.sample.mapper;

import com.github.onozaty.sample.domain.User;
import com.github.onozaty.sample.domain.UserCreateInput;
import com.github.onozaty.sample.domain.UserUpdateInput;
import com.github.onozaty.sample.domain.UserWithCredential;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserMapper {

  @Select(
      """
      SELECT *
      FROM users
      ORDER BY id
      """)
  List<User> findAll();

  @Select(
      """
      SELECT *
      FROM users
      WHERE id = #{id}
      """)
  Optional<User> findById(long id);

  @Select(
      """
      INSERT INTO users (name, email, created_at, updated_at)
      VALUES (#{input.name}, #{input.email}, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
      RETURNING *
      """)
  User insert(@Param("input") UserCreateInput input);

  @Select(
      """
      UPDATE users
      SET name = #{input.name},
          email = #{input.email},
          updated_at = CURRENT_TIMESTAMP
      WHERE id = #{id}
      RETURNING *
      """)
  User update(@Param("id") long id, @Param("input") UserUpdateInput input);

  @Delete(
      """
      DELETE FROM users
      WHERE id = #{id}
      """)
  int delete(long id);

  @Select(
      """
      SELECT id, name, email
      FROM users
      WHERE email = #{email}
      """)
  Optional<User> findByEmail(String email);

  @Select(
      """
      SELECT u.id, u.name, u.email, c.password_hash
      FROM users u
      INNER JOIN user_credentials c ON u.id = c.user_id
      WHERE u.email = #{email}
      """)
  Optional<UserWithCredential> findByEmailWithCredential(String email);
}
