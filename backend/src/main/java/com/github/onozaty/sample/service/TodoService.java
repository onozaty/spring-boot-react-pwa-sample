package com.github.onozaty.sample.service;

import com.github.onozaty.sample.domain.Todo;
import com.github.onozaty.sample.domain.TodoCreateInput;
import com.github.onozaty.sample.domain.TodoUpdateInput;
import com.github.onozaty.sample.mapper.TodoMapper;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class TodoService {

  private final TodoMapper todoMapper;

  public TodoService(TodoMapper todoMapper) {
    this.todoMapper = todoMapper;
  }

  @Transactional(readOnly = true)
  public List<Todo> findAll(Long userId) {
    return todoMapper.findAllByUserId(userId);
  }

  public Todo create(Long userId, TodoCreateInput input) {
    return todoMapper.insert(userId, input);
  }

  public Optional<Todo> update(Long userId, Long todoId, TodoUpdateInput input) {
    return Optional.ofNullable(todoMapper.update(todoId, userId, input));
  }

  public boolean delete(Long userId, Long todoId) {
    return todoMapper.delete(todoId, userId) > 0;
  }
}
