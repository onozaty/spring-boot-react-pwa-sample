package com.github.onozaty.sample.service;

import com.github.onozaty.sample.domain.User;
import com.github.onozaty.sample.domain.UserCreateInput;
import com.github.onozaty.sample.domain.UserUpdateInput;
import com.github.onozaty.sample.mapper.UserCredentialMapper;
import com.github.onozaty.sample.mapper.UserMapper;
import java.util.List;
import java.util.Optional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class UserService {

  private final UserMapper userMapper;
  private final UserCredentialMapper credentialMapper;
  private final PasswordEncoder passwordEncoder;

  public UserService(
      UserMapper userMapper,
      UserCredentialMapper credentialMapper,
      PasswordEncoder passwordEncoder) {
    this.userMapper = userMapper;
    this.credentialMapper = credentialMapper;
    this.passwordEncoder = passwordEncoder;
  }

  @Transactional(readOnly = true)
  public List<User> findAll() {
    return userMapper.findAll();
  }

  @Transactional(readOnly = true)
  public Optional<User> findById(Long id) {
    return userMapper.findById(id);
  }

  @Transactional(readOnly = true)
  public Optional<User> findByEmail(String email) {
    return userMapper.findByEmail(email);
  }

  public User create(UserCreateInput input) {
    User created = userMapper.insert(input);

    credentialMapper.insert(created.getId(), passwordEncoder.encode(input.getPassword()));

    return created;
  }

  public Optional<User> update(Long id, UserUpdateInput input) {
    return Optional.ofNullable(userMapper.update(id, input));
  }

  public boolean delete(Long id) {
    return userMapper.delete(id) > 0;
  }
}
