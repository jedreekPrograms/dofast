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

    public UserRequest() {}

    public String getEmail() {
        return email;
    }

    public String getNickname() {
        return nickname;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }
}
