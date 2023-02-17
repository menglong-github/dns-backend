package com.dns.resolution.mapper;

import com.dns.resolution.domain.dto.DnsPlatformResolutionDomainName;
import com.dns.resolution.domain.req.DomainNameBody;
import com.dns.resolution.domain.res.DnsPlatformResolutionDomainNameView;

import java.util.List;

public interface DnsPlatformResolutionDomainNameMapper {
    public int insertDnsPlatformResolutionDomainName(DnsPlatformResolutionDomainName dnsPlatformResolutionDomainName);

    public List<DnsPlatformResolutionDomainName> selectDnsPlatformResolutionDomainNameAllList();

    public List<DnsPlatformResolutionDomainNameView> selectDnsPlatformResolutionDomainNameViewListByUserId(DomainNameBody domainNameBody);

    public DnsPlatformResolutionDomainName selectDnsPlatformResolutionDomainNameByDomainId(Long domainId);

    public int updateDnsPlatformResolutionDomainNameDnssec(DnsPlatformResolutionDomainName dnsPlatformResolutionDomainName);

    public int deleteDnsPlatformResolutionDomainName(DnsPlatformResolutionDomainName dnsPlatformResolutionDomainName);

}
