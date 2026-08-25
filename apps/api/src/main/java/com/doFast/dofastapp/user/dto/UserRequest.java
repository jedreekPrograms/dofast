package com.doFast.dofastapp.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class UserRequest {

    @NotBlank(message = "Email nie może być pusty")
    @Email(message = "Niepoprawny format email")
    @Size(max = 320, message = "Email jest za długi")
    private String email;

    @NotBlank(message = "Nickname nie może być pusty")
    @Size(min = 3, max = 80, message = "Nickname musi mieć od 3 do 80 znaków")
    private String nickname;

    @NotBlank(message = "Hasło nie może być puste")
    @Size(min = 8, max = 72, message = "Hasło musi mieć od 8 do 72 znaków")
    private String password;

    public UserRequest() {}

    public String getEmail() { return email; }
    public String getNickname() { return nickname; }
    public String getPassword() { return password; }
    public void setEmail(String email) { this.email = email; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public void setPassword(String password) { this.password = password; }
}
