package com.dns.resolution.annotation.aspect;

import com.alibaba.fastjson2.JSON;
import com.dns.common.constant.HttpStatus;
import com.dns.common.core.domain.AjaxResult;
import com.dns.common.core.redis.RedisCache;
import com.dns.common.utils.ServletUtils;
import com.dns.common.utils.StringUtils;
import com.dns.resolution.annotation.Auth;
import com.dns.resolution.constants.LoginConstants;
import com.dns.resolution.domain.dto.DnsPlatformUserInfo;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.servlet.Servlet;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Aspect
@Component
public class AuthAspect {

    @Autowired
    private RedisCache redisCache;

    @Pointcut("@annotation(auth)")
    public void pointCut(Auth auth) {}

    private Object authException() {
        int code = HttpStatus.UNAUTHORIZED;
        String msg = StringUtils.format("Request access：{}，authentication failed", ServletUtils.getRequest().getRequestURI());
        ServletUtils.renderString(ServletUtils.getResponse(), JSON.toJSONString(AjaxResult.error(code, msg)));
        return null;
    }

    @Around(value = "pointCut(auth)", argNames = "proceedingJoinPoint,auth")
    public Object authAroundAspect(ProceedingJoinPoint proceedingJoinPoint, Auth auth) throws Throwable {
        String jwtToken = ServletUtils.getRequest().getHeader(LoginConstants.JWT_HEADER);
        Claims claims;
        long tokenExpire;
        try {
            claims = Jwts.parser().setSigningKey(LoginConstants.JWT_SECRET).parseClaimsJws(jwtToken).getBody();
            tokenExpire = (long) claims.get(LoginConstants.JWT_CLAIMS_EXPIRE_KEY);
        } catch (Exception exception) {
            return authException();
        }
        if (tokenExpire > System.currentTimeMillis()) {
            DnsPlatformUserInfo dnsPlatformUserInfo = redisCache.getCacheObject(LoginConstants.LOGIN_USER_TOKEN_CACHE_KEY + claims.get(LoginConstants.JWT_CLAIMS_TOKEN_KEY));
            if (dnsPlatformUserInfo == null) {
                return authException();
            } else {
                ServletUtils.getRequest().setAttribute(LoginConstants.SERVLET_LOGIN_JWT_CLAIMS_KEY, claims);
                ServletUtils.getRequest().setAttribute(LoginConstants.SERVLET_LOGIN_USER_KEY, dnsPlatformUserInfo);
                return proceedingJoinPoint.proceed();
            }
        } else {
            return authException();
        }
    }

    @AfterReturning(value = "pointCut(auth)", argNames = "auth,result", returning = "result")
    public void wechatAppletAuthAfterReturningAspect(Auth auth, AjaxResult result) {
        Claims claims = (Claims) ServletUtils.getRequest().getAttribute(LoginConstants.SERVLET_LOGIN_JWT_CLAIMS_KEY);
        long tokenExpire = (long) claims.get(LoginConstants.JWT_CLAIMS_EXPIRE_KEY);
        if (TimeUnit.MILLISECONDS.toMinutes(tokenExpire - System.currentTimeMillis()) < LoginConstants.JWT_EXPIRE_REFRESH) {
            DnsPlatformUserInfo dnsPlatformUserInfo = (DnsPlatformUserInfo) ServletUtils.getRequest().getAttribute(LoginConstants.SERVLET_LOGIN_USER_KEY);
            claims.put(LoginConstants.JWT_CLAIMS_EXPIRE_KEY, System.currentTimeMillis() + TimeUnit.MINUTES.toMicros(LoginConstants.JWT_EXPIRE));
            String jwtToken = Jwts.builder().setClaims(claims).signWith(SignatureAlgorithm.HS512, LoginConstants.JWT_SECRET).compact();
            redisCache.setCacheObject(LoginConstants.LOGIN_USER_TOKEN_CACHE_KEY + claims.get(LoginConstants.JWT_CLAIMS_TOKEN_KEY), dnsPlatformUserInfo, LoginConstants.JWT_EXPIRE, TimeUnit.MINUTES);
            result.put(LoginConstants.JWT_REFRESH_TOKEN_KEY, jwtToken);
        }
    }
}
