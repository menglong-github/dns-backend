package com.dns.resolution.utils;

import com.dns.common.core.redis.RedisCache;
import com.dns.common.utils.ServletUtils;
import com.dns.resolution.constants.LoginConstants;
import com.dns.resolution.domain.dto.DnsPlatformUserInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class AuthUtils {

    @Autowired
    private RedisCache redisCache;

    public DnsPlatformUserInfo getLoginUser() {
        return (DnsPlatformUserInfo) ServletUtils.getRequest().getAttribute(LoginConstants.SERVLET_LOGIN_USER_KEY);
    }

    public void logout() {
        Map<String, Object> claims = (Map<String, Object>) ServletUtils.getRequest().getAttribute(LoginConstants.SERVLET_LOGIN_JWT_CLAIMS_KEY);
        DnsPlatformUserInfo dnsPlatformUserInfo = getLoginUser();
        redisCache.deleteObject(LoginConstants.LOGIN_USER_TOKEN_CACHE_KEY + claims.get(LoginConstants.JWT_CLAIMS_TOKEN_KEY));
        redisCache.redisTemplate.opsForList().remove(LoginConstants.LOGIN_USER_ID_MAP_TOKEN_LIST_CACHE_KEY + dnsPlatformUserInfo.getUserId(), 1, claims.get(LoginConstants.JWT_CLAIMS_TOKEN_KEY));
    }

}
