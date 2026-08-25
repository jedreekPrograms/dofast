package com.doFast.dofastapp.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class LoginRequest {

    @NotBlank(message = "Email nie może być pusty")
    @Email(message = "Niepoprawny format email")
    @Size(max = 320, message = "Email jest za długi")
    private String email;

    @NotBlank(message = "Hasło nie może być puste")
    private String password;

    public LoginRequest() {}

    public String getEmail() { return email; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public void setEmail(String email) { this.email = email; }
}
