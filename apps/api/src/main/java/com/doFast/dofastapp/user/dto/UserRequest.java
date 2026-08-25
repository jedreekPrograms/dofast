package com.doFast.dofastapp.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class UserRequest {

    @NotBlank(message = "Email nie może być pusty")
    @Email(message = "Niepoprawny format email")
    private String email;

    @NotBlank(message = "Nickname nie może być pusty")
    @Size(min = 5, message = "Nickname musi mieć min 5 znaków")
    private String nickname;

    @NotBlank(message = "Hasło nie może być puste")
    @Size(min = 6, message = "Hasło musi mieć min 6 znaków")
    private String password;

    public UserRequest() {}

    public String getEmail() {
        return email;
    }

    public String getNickname() {
        return nickname;
    }

    public String getPassword() {
        return password;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
