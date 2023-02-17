package com.dns.resolution.mapper;

import com.dns.resolution.domain.dto.DnsPlatformResolutionDomainName;
import com.dns.resolution.domain.dto.DnsPlatformResolutionDomainNameZone;
import com.dns.resolution.domain.res.DnsPlatformResolutionDomainNameZoneView;

import java.util.List;

public interface DnsPlatformResolutionDomainNameZoneMapper {

    public int insertDnsPlatformResolutionDomainNameZone(DnsPlatformResolutionDomainNameZone dnsPlatformResolutionDomainNameZone);

    public List<DnsPlatformResolutionDomainNameZone> selectDnsPlatformResolutionDomainNameZoneByDomainId(Long domainId);

    public List<DnsPlatformResolutionDomainNameZoneView> selectDnsPlatformResolutionDomainNameZoneViewByDomainId(Long domainId);

    public int updateDnsPlatformResolutionDomainNameZone(DnsPlatformResolutionDomainNameZone dnsPlatformResolutionDomainNameZone);

    public int deleteDnsPlatformResolutionDomainNameZoneByDomainId(Long domainId);


    public DnsPlatformResolutionDomainNameZone selectDnsPlatformResolutionDomainNameZoneByZoneId(Long zoneId);

    public int deleteDnsPlatformResolutionDomainNameZoneById(Long id);


}
