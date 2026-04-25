package com.github.onozaty.sample.domain;

/** UserDetailsService 内部でのみ使用する認証情報付きユーザー DTO */
public class UserWithCredential {

  private long id;
  private String name;
  private String email;
  private String passwordHash;

  public long getId() {
    return id;
  }

  public void setId(long id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public void setPasswordHash(String passwordHash) {
    this.passwordHash = passwordHash;
  }
}
