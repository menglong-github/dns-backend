package com.dns.resolution.domain.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

@Data
public class DnsPlatformUserInfo {
    private Long id;
    private Long userId;
    private String userName;
    private String password;
    private String email;
    private String userLevel;
    private Long registerTime;
}
