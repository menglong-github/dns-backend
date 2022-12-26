package com.dns.resolution.service.impl;

import com.dns.common.core.redis.RedisCache;
import com.dns.common.utils.SecurityUtils;
import com.dns.common.utils.ServletUtils;
import com.dns.common.utils.StringUtils;
import com.dns.common.utils.ip.IpUtils;
import com.dns.common.utils.uuid.IdUtils;
import com.dns.resolution.constants.LoginConstants;
import com.dns.resolution.constants.RegexConstants;
import com.dns.resolution.constants.RegisterConstants;
import com.dns.resolution.constants.ResetConstants;
import com.dns.resolution.domain.dto.DnsPlatformUserInfo;
import com.dns.resolution.domain.req.UserBody;
import com.dns.resolution.mapper.DnsPlatformUserInfoMapper;
import com.dns.resolution.service.DnsPlatformUserService;
import com.dns.resolution.utils.MailUtils;
import com.dns.resolution.utils.SnowflakeUtils;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@Service
public class DnsPlatformUserServiceImpl implements DnsPlatformUserService {

    @Autowired
    private RedisCache redisCache;

    @Autowired
    private DnsPlatformUserInfoMapper dnsPlatformUserInfoMapper;

    @Autowired
    private MailUtils mailUtils;

    @Autowired
    private SnowflakeUtils snowflakeUtils;

    @Override
    public Map<String, Object> registerCheckService(UserBody userBody) {
        Map<String, Object> resultMap = new HashMap<>();
        if (StringUtils.isEmpty(userBody.getEmail())) {
            resultMap.put("message", "Please input the email address");
            resultMap.put("code", 100001);
        } else if (!Pattern.matches(RegexConstants.REGEX_EMAIL, userBody.getEmail())) {
            resultMap.put("message", "Email format error");
            resultMap.put("code", 100002);
        } else {
            String sendRegisterEmailIpCacheKey = RegisterConstants.SEND_REGISTER_EMAIL_IP_CACHE_KEY + IpUtils.getIpAddr(ServletUtils.getRequest());
            long sendRegisterEmailIpLimitCount = redisCache.redisTemplate.boundValueOps(sendRegisterEmailIpCacheKey).increment();
            if (sendRegisterEmailIpLimitCount <= RegisterConstants.SEND_REGISTER_EMAIL_IP_LIMIT_COUNT) {
                String sendRegisterEmailCode = redisCache.getCacheObject(RegisterConstants.SEND_REGISTER_EMAIL_CODE_CACHE_KEY + userBody.getEmail());
                if (StringUtils.isEmpty(sendRegisterEmailCode)) {
                    if (dnsPlatformUserInfoMapper.selectDnsPlatformUserInfoCountByEmail(userBody.getEmail()) == 0) {
                        String code = IdUtils.fastSimpleUUID();
                        boolean sendResult = mailUtils.sendEmail(userBody.getEmail(), "Welcome to register Root Servers World", "Register Code: " + code);
                        if (sendResult) {
                            redisCache.setCacheObject(RegisterConstants.SEND_REGISTER_EMAIL_CODE_CACHE_KEY + userBody.getEmail(), code, 5, TimeUnit.MINUTES);
                            resultMap.put("message", "The verification code has been sent, please pay attention to check");
                            resultMap.put("code", 0);
                        } else {
                            resultMap.put("message", "Failed to send verification code, please try again");
                            resultMap.put("code", 100005);
                            redisCache.redisTemplate.boundValueOps(sendRegisterEmailIpCacheKey).decrement();
                        }
                    } else {
                        resultMap.put("message", "Email address has been registered");
                        resultMap.put("code", 100004);
                        redisCache.redisTemplate.boundValueOps(sendRegisterEmailIpCacheKey).decrement();
                    }
                } else {
                    resultMap.put("message", "The verification code has been sent, please pay attention to check");
                    resultMap.put("code", 0);
                    redisCache.redisTemplate.boundValueOps(sendRegisterEmailIpCacheKey).decrement();
                }
            } else {
                if (sendRegisterEmailIpLimitCount == (RegisterConstants.SEND_REGISTER_EMAIL_IP_LIMIT_COUNT + 1)) {
                    redisCache.expire(sendRegisterEmailIpCacheKey, 1, TimeUnit.HOURS);
                }
                resultMap.put("message", "Verification codes are sent frequently, please try again in an hour");
                resultMap.put("code", 100003);
            }
        }
        return resultMap;
    }

    @Override
    public Map<String, Object> registerService(UserBody userBody) {
        Map<String, Object> resultMap = new HashMap<>();
        if (StringUtils.isEmpty(userBody.getEmail())) {
            resultMap.put("message", "Please input the email address");
            resultMap.put("code", 100001);
        } else if (StringUtils.isEmpty(userBody.getPassword())) {
            resultMap.put("message", "Please input password");
            resultMap.put("code", 100002);
        } else if (userBody.getPassword().length() <= RegisterConstants.REGISTER_PASSWORD_LENGTH_MIN) {
            resultMap.put("message", "Password length should be greater than  " + RegisterConstants.REGISTER_PASSWORD_LENGTH_MIN + " characters");
            resultMap.put("code", 100003);
        } else if (StringUtils.isEmpty(userBody.getCode())) {
            resultMap.put("message", "Please input verification code");
            resultMap.put("code", 100004);
        } else {
            String verifyRegisterEmailIpCacheKey = RegisterConstants.VERIFY_REGISTER_EMAIL_IP_CACHE_KEY + IpUtils.getIpAddr(ServletUtils.getRequest());
            long verifyRegisterEmailIpLimitCount = redisCache.redisTemplate.boundValueOps(verifyRegisterEmailIpCacheKey).increment();
            if (verifyRegisterEmailIpLimitCount <= RegisterConstants.VERIFY_REGISTER_EMAIL_IP_LIMIT_COUNT) {
                String code = redisCache.getCacheObject(RegisterConstants.SEND_REGISTER_EMAIL_CODE_CACHE_KEY + userBody.getEmail());
                if (StringUtils.isEmpty(code)) {
                    resultMap.put("message", "Verification code expired");
                    resultMap.put("code", 100006);
                } else {
                    if (code.contentEquals(userBody.getCode())) {
                        DnsPlatformUserInfo dnsPlatformUserInfo = new DnsPlatformUserInfo();
                        dnsPlatformUserInfo.setUserId(snowflakeUtils.nextId());
                        dnsPlatformUserInfo.setUserLevel(RegisterConstants.REGISTER_DEFAULT_USER_LEVEL);
                        dnsPlatformUserInfo.setUserName(IdUtils.fastSimpleUUID());
                        dnsPlatformUserInfo.setRegisterTime(System.nanoTime());
                        dnsPlatformUserInfo.setEmail(userBody.getEmail());
                        dnsPlatformUserInfo.setPassword(SecurityUtils.encryptPassword(userBody.getPassword()));
                        int result = dnsPlatformUserInfoMapper.insertDnsPlatformUserInfo(dnsPlatformUserInfo);
                        if (result == 0) {
                            resultMap.put("message", "Email address has been registered");
                            resultMap.put("code", 100008);
                        } else {
                            resultMap.put("message", "Registration success");
                            resultMap.put("code", 0);
                            redisCache.redisTemplate.boundValueOps(verifyRegisterEmailIpCacheKey).decrement();
                        }
                        redisCache.deleteObject(RegisterConstants.SEND_REGISTER_EMAIL_CODE_CACHE_KEY + userBody.getEmail());
                    } else {
                        resultMap.put("message", "Verification code error");
                        resultMap.put("code", 100007);
                    }
                }
            } else {
                if (verifyRegisterEmailIpLimitCount == (RegisterConstants.VERIFY_REGISTER_EMAIL_IP_LIMIT_COUNT + 1)) {
                    redisCache.expire(verifyRegisterEmailIpCacheKey, 10, TimeUnit.MINUTES);
                }
                resultMap.put("message", "Frequent verification, please try again in ten minutes");
                resultMap.put("code", 100005);
            }

        }
        return resultMap;
    }

    @Override
    public Map<String, Object> resetCheckService(UserBody userBody) {
        Map<String, Object> resultMap = new HashMap<>();
        if (StringUtils.isEmpty(userBody.getEmail())) {
            resultMap.put("message", "Please input the email address");
            resultMap.put("code", 100001);
        } else if (!Pattern.matches(RegexConstants.REGEX_EMAIL, userBody.getEmail())) {
            resultMap.put("message", "Email format error");
            resultMap.put("code", 100002);
        } else {
            String sendResetEmailCode = redisCache.getCacheObject(ResetConstants.SEND_RESET_EMAIL_CODE_CACHE_KEY + userBody.getEmail());
            String sendResetEmailIpCacheKey = ResetConstants.SEND_RESET_EMAIL_IP_CACHE_KEY + IpUtils.getIpAddr(ServletUtils.getRequest());
            long sendResetEmailIpLimitCount = redisCache.redisTemplate.boundValueOps(sendResetEmailIpCacheKey).increment();
            if (sendResetEmailIpLimitCount <= ResetConstants.SEND_RESET_EMAIL_IP_LIMIT_COUNT) {
                if (StringUtils.isEmpty(sendResetEmailCode)) {
                    if (dnsPlatformUserInfoMapper.selectDnsPlatformUserInfoCountByEmail(userBody.getEmail()) == 0) {
                        resultMap.put("message", "Email address is not registered");
                        resultMap.put("code", 100004);
                        redisCache.redisTemplate.boundValueOps(sendResetEmailIpCacheKey).decrement();
                    } else {
                        String code = IdUtils.fastSimpleUUID();
                        boolean sendResult = mailUtils.sendEmail(userBody.getEmail(), "Welcome to reset Root Servers World", "Reset Code: " + code);
                        if (sendResult) {
                            redisCache.setCacheObject(ResetConstants.SEND_RESET_EMAIL_CODE_CACHE_KEY + userBody.getEmail(), code, 5, TimeUnit.MINUTES);
                            resultMap.put("message", "The verification code has been sent, please pay attention to check");
                            resultMap.put("code", 0);
                        } else {
                            resultMap.put("message", "Failed to send verification code, please try again");
                            resultMap.put("code", 100005);
                            redisCache.redisTemplate.boundValueOps(sendResetEmailIpCacheKey).decrement();
                        }
                    }
                } else {
                    resultMap.put("message", "The verification code has been sent, please pay attention to check");
                    resultMap.put("code", 0);
                    redisCache.redisTemplate.boundValueOps(sendResetEmailIpCacheKey).decrement();
                }
            } else {
                if (sendResetEmailIpLimitCount == (ResetConstants.SEND_RESET_EMAIL_IP_LIMIT_COUNT + 1)) {
                    redisCache.expire(sendResetEmailIpCacheKey, 1, TimeUnit.HOURS);
                }
                resultMap.put("message", "Verification codes are sent frequently, please try again in an hour");
                resultMap.put("code", 100003);
            }
        }
        return resultMap;
    }

    @Override
    public Map<String, Object> resetService(UserBody userBody) {
        Map<String, Object> resultMap = new HashMap<>();
        if (StringUtils.isEmpty(userBody.getEmail())) {
            resultMap.put("message", "Please input the email address");
            resultMap.put("code", 100001);
        } else if (StringUtils.isEmpty(userBody.getPassword())) {
            resultMap.put("message", "Please input password");
            resultMap.put("code", 100002);
        } else if (userBody.getPassword().length() <= ResetConstants.RESET_PASSWORD_LENGTH_MIN) {
            resultMap.put("message", "Password length should be greater than  " + ResetConstants.RESET_PASSWORD_LENGTH_MIN + " characters");
            resultMap.put("code", 100003);
        } else if (StringUtils.isEmpty(userBody.getCode())) {
            resultMap.put("message", "Please input verification code");
            resultMap.put("code", 100004);
        } else {
            String code = redisCache.getCacheObject(ResetConstants.SEND_RESET_EMAIL_CODE_CACHE_KEY + userBody.getEmail());
            String verifyResetEmailIpCacheKey = ResetConstants.VERIFY_RESET_EMAIL_IP_CACHE_KEY + IpUtils.getIpAddr(ServletUtils.getRequest());
            long verifyResetEmailIpLimitCount = redisCache.redisTemplate.boundValueOps(verifyResetEmailIpCacheKey).increment();
            if (verifyResetEmailIpLimitCount <= ResetConstants.VERIFY_RESET_EMAIL_IP_LIMIT_COUNT) {
                if (StringUtils.isEmpty(code)) {
                    resultMap.put("message", "Verification code expired");
                    resultMap.put("code", 100006);
                } else {
                    if (code.contentEquals(userBody.getCode())) {
                        DnsPlatformUserInfo dnsPlatformUserInfo = new DnsPlatformUserInfo();
                        dnsPlatformUserInfo.setEmail(userBody.getEmail());
                        dnsPlatformUserInfo.setPassword(SecurityUtils.encryptPassword(userBody.getPassword()));
                        int result = dnsPlatformUserInfoMapper.updateDnsPlatformUserInfoPassword(dnsPlatformUserInfo);
                        if (result == 0) {
                            resultMap.put("message", "Email address is not registered");
                            resultMap.put("code", 100008);
                        } else {
                            resultMap.put("message", "Reset success");
                            resultMap.put("code", 0);
                            redisCache.redisTemplate.boundValueOps(verifyResetEmailIpCacheKey).decrement();
                        }
                        redisCache.deleteObject(ResetConstants.SEND_RESET_EMAIL_CODE_CACHE_KEY + userBody.getEmail());
                    } else {
                        resultMap.put("message", "Verification code error");
                        resultMap.put("code", 100007);
                    }
                }
            } else {
                if (verifyResetEmailIpLimitCount == (ResetConstants.VERIFY_RESET_EMAIL_IP_LIMIT_COUNT + 1)) {
                    redisCache.expire(verifyResetEmailIpCacheKey, 10, TimeUnit.MINUTES);
                }
                resultMap.put("message", "Frequent verification, please try again in ten minutes");
                resultMap.put("code", 100005);
            }
        }
        return resultMap;
    }

    @Override
    public Map<String, Object> login(UserBody userBody) {
        Map<String, Object> resultMap = new HashMap<>();
        if (StringUtils.isEmpty(userBody.getEmail())) {
            resultMap.put("message", "Please input the email address");
            resultMap.put("code", 100001);
        } else if (StringUtils.isEmpty(userBody.getPassword())) {
            resultMap.put("message", "Please input password");
            resultMap.put("code", 100002);
        } else {
            String loginErrorIpCacheKey = LoginConstants.LOGIN_ERROR_IP_CACHE_KEY + IpUtils.getIpAddr(ServletUtils.getRequest());
            long loginErrorIpLimitCount = redisCache.redisTemplate.boundValueOps(loginErrorIpCacheKey).increment();
            if (loginErrorIpLimitCount <= LoginConstants.LOGIN_ERROR_IP_LIMIT_COUNT) {
                DnsPlatformUserInfo dnsPlatformUserInfo = dnsPlatformUserInfoMapper.selectDnsPlatformUserInfoByEmail(userBody.getEmail());
                if ((dnsPlatformUserInfo != null) && SecurityUtils.matchesPassword(userBody.getPassword(), dnsPlatformUserInfo.getPassword())) {
                    String token = IdUtils.fastSimpleUUID();
                    Map<String, Object> claims = new HashMap<>();
                    claims.put(LoginConstants.JWT_CLAIMS_TOKEN_KEY, token);
                    claims.put(LoginConstants.JWT_CLAIMS_EXPIRE_KEY, System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(LoginConstants.JWT_EXPIRE));
                    String jwtToken = Jwts.builder().setClaims(claims).signWith(SignatureAlgorithm.HS512, LoginConstants.JWT_SECRET).compact();
                    redisCache.setCacheObject(LoginConstants.LOGIN_USER_TOKEN_CACHE_KEY + token, dnsPlatformUserInfo, LoginConstants.JWT_EXPIRE, TimeUnit.MINUTES);
                    long onlineSize = redisCache.redisTemplate.opsForList().rightPush(LoginConstants.LOGIN_USER_ID_MAP_TOKEN_LIST_CACHE_KEY + dnsPlatformUserInfo.getUserId(), token);
                    if (onlineSize > LoginConstants.LOGIN_USER_ONLINE_LIMIT_COUNT) {
                        String removeToken = (String) redisCache.redisTemplate.opsForList().leftPop(LoginConstants.LOGIN_USER_ID_MAP_TOKEN_LIST_CACHE_KEY + dnsPlatformUserInfo.getUserId());
                        redisCache.deleteObject(LoginConstants.LOGIN_USER_TOKEN_CACHE_KEY + removeToken);
                    }
                    redisCache.redisTemplate.boundValueOps(loginErrorIpCacheKey).decrement()                                                                                                                                                                                                                                                                                                                                                                                          ;
                    resultMap.put("message", "Login success");
                    resultMap.put("code", 0);
                    resultMap.put("token", jwtToken);
                } else {
                    resultMap.put("message", "Wrong email address or password");
                    resultMap.put("code", 100004);
                }
            } else {
                if (loginErrorIpLimitCount == (LoginConstants.LOGIN_ERROR_IP_LIMIT_COUNT + 1)) {
                    redisCache.expire(loginErrorIpCacheKey, 1, TimeUnit.HOURS);
                }
                resultMap.put("message", "Frequent login errors, please try again in an hour");
                resultMap.put("code", 100003);
            }
        }
        return resultMap;
    }
}
