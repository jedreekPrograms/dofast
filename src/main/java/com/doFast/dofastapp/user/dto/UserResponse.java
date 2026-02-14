package com.doFast.dofastapp.user.dto;

public class UserResponse {

    private Long id;
    private String email;
    private String nickname;


    public UserResponse(Long id, String email, String nickname) {
        this.id = id;
        this.email = email;
        this.nickname = nickname;
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getNickname() {
        return nickname;
    }


}
