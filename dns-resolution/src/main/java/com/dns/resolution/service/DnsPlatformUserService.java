package com.dns.resolution.service;

import com.dns.resolution.domain.req.UserBody;

import java.util.Map;

public interface DnsPlatformUserService {
    public Map<String, Object> registerCheckService(UserBody userBody);

    public Map<String, Object> registerService(UserBody userBody);

    public Map<String, Object> resetCheckService(UserBody userBody);

    public Map<String, Object> resetService(UserBody userBody);

    public Map<String, Object> login(UserBody userBody);

}
