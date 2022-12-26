package com.dns.resolution.utils;

import com.dns.common.utils.ServletUtils;
import com.dns.resolution.constants.LoginConstants;
import com.dns.resolution.domain.dto.DnsPlatformUserInfo;
import org.springframework.stereotype.Component;

@Component
public class AuthUtils {
    public DnsPlatformUserInfo getLoginUser() {
        return (DnsPlatformUserInfo) ServletUtils.getRequest().getAttribute(LoginConstants.SERVLET_LOGIN_USER_KEY);
    }
}
