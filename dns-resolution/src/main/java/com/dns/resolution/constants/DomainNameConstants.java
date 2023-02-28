package com.dns.resolution.constants;

public class DomainNameConstants {
    public static final String RESOLUTION_DOMAIN_NAME_LOCK_CACHE_KEY = "RESOLUTION_DOMAIN_NAME_LOCK:";

    public static final int RESOLUTION_DOMAIN_NAME_LOCK_EXPIRE_TIME = 5;

    public static final String EXIST_RESOLUTION_DOMAIN_NAME_CACHE_KEY = "EXIST_RESOLUTION_DOMAIN_NAME:";

    public static final String SUPPORT_RESOLUTION_DOMAIN_NAME_EXTENSION_DICT_KEY = "resolution_domain_name_extension";

    public static final String SUPPORT_RESOLUTION_DOMAIN_NAME_GEO_DICT_KEY = "resolution_domain_name_geo";

    public static final String ADD_RESOLUTION_DOMAIN_NAME_VERIFY_TXT_RECORD_CACHE_KEY = "ADD_RESOLUTION_DOMAIN_NAME_VERIFY_TXT_RECORD:";

    public static final int ADD_RESOLUTION_DOMAIN_NAME_VERIFY_TXT_RECORD_LIMIT_TIME = 30;

    public static final String ADD_RESOLUTION_DOMAIN_NAME_VERIFY_TXT_RECORD_PREFIX = "_auth.";

    public static final String ADD_RESOLUTION_DOMAIN_NAME_DEFAULT_SOA_MASTER_NAME = "a.root-servers.world.";

    public static final String ADD_RESOLUTION_DOMAIN_NAME_DEFAULT_NS_SLAVE_NAME = "b.root-servers.world.";

    public static final String ADD_RESOLUTION_DOMAIN_NAME_DEFAULT_SOA_ADMIN_NAME = "info@root-servers.world.";

    public static final String RESOLUTION_DOMAIN_NAME_MQ_EXCHANGE_NAME = "DNS";

    public static final int SUPPORT_RESOLUTION_DOMAIN_NAME_ZONE_FILE_LENGTH = 1048576;
}
