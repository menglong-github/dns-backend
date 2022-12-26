package com.dns.resolution.mapper;


import com.dns.resolution.domain.dto.DnsPlatformUserInfo;

public interface DnsPlatformUserInfoMapper {
    public Integer selectDnsPlatformUserInfoCountByEmail(String email);

    public Integer insertDnsPlatformUserInfo(DnsPlatformUserInfo dnsPlatformUserInfo);

    public Integer updateDnsPlatformUserInfoPassword(DnsPlatformUserInfo dnsPlatformUserInfo);

    public DnsPlatformUserInfo selectDnsPlatformUserInfoByEmail(String email);
}
