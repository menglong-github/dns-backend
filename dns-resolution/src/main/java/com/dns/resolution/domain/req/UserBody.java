package com.dns.resolution.domain.req;

import lombok.Data;

@Data
public class UserBody {
    private String email;
    private String password;
    private String code;
}
