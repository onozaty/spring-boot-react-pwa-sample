package com.github.onozaty.sample.mapper;

import com.github.onozaty.sample.domain.Todo;
import com.github.onozaty.sample.domain.TodoCreateInput;
import com.github.onozaty.sample.domain.TodoUpdateInput;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface TodoMapper {

  @Select(
      """
      SELECT *
      FROM todos
      WHERE user_id = #{userId}
      ORDER BY created_at
      """)
  List<Todo> findAllByUserId(Long userId);

  @Select(
      """
      SELECT *
      FROM todos
      WHERE id = #{id}
        AND user_id = #{userId}
      """)
  Optional<Todo> findByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

  @Select(
      """
      INSERT INTO todos (user_id, text, done, created_at, updated_at)
      VALUES (#{userId}, #{input.text}, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
      RETURNING *
      """)
  Todo insert(@Param("userId") Long userId, @Param("input") TodoCreateInput input);

  @Select(
      """
      UPDATE todos
      SET text = #{input.text},
          done = #{input.done},
          updated_at = CURRENT_TIMESTAMP
      WHERE id = #{id}
        AND user_id = #{userId}
      RETURNING *
      """)
  Todo update(
      @Param("id") Long id, @Param("userId") Long userId, @Param("input") TodoUpdateInput input);

  @Delete(
      """
      DELETE FROM todos
      WHERE id = #{id}
        AND user_id = #{userId}
      """)
  int delete(@Param("id") Long id, @Param("userId") Long userId);
}
