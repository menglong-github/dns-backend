package com.dns.resolution.constants;

public class LoginConstants {
    public static final String LOGIN_ERROR_IP_CACHE_KEY = "LOGIN_ERROR_IP:";

    public static final long LOGIN_ERROR_IP_LIMIT_COUNT = 10;

    public static final String LOGIN_USER_TOKEN_CACHE_KEY = "LOGIN_USER_TOKEN:";

    public static final String LOGIN_USER_ID_MAP_TOKEN_LIST_CACHE_KEY = "LOGIN_USER_ID_MAP_TOKEN_LIST:";

    public static final int LOGIN_USER_ONLINE_LIMIT_COUNT = 1;

    public static final String JWT_CLAIMS_TOKEN_KEY = "token";

    public static final String JWT_CLAIMS_EXPIRE_KEY = "expire";

    public static final String JWT_HEADER = "Authorization";

    public static final String JWT_SECRET = "$10$ZUWZPRj61SI705Qd66XQBe7ZMPUY5Gg2QKVan/7.TPDvNJKKMavVm";

    public static final int JWT_EXPIRE = 60;

    public static final int JWT_EXPIRE_REFRESH = 5;

    public static final String SERVLET_LOGIN_JWT_CLAIMS_KEY = "SERVLET_LOGIN_JWT_CLAIMS";

    public static final String SERVLET_LOGIN_USER_KEY = "SERVLET_LOGIN_USER";

    public static final String JWT_REFRESH_TOKEN_KEY = "refresh_token";
}
