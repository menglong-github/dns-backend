package com.dns.resolution.service;

import com.dns.resolution.domain.dto.DnsPlatformResolutionDomainName;
import com.dns.resolution.domain.dto.DnsPlatformResolutionDomainNameZone;
import com.dns.resolution.domain.req.DomainNameBody;
import com.dns.resolution.domain.res.DnsPlatformResolutionDomainNameZoneView;

import java.util.List;
import java.util.Map;

public interface DnsPlatformResolutionDomainNameZoneService {
    public List<DnsPlatformResolutionDomainNameZoneView> list(DomainNameBody domainNameBody);

    public Map<String, Object> add(DnsPlatformResolutionDomainNameZone dnsPlatformResolutionDomainNameZone);

    public Map<String, Object> update(DnsPlatformResolutionDomainNameZone dnsPlatformResolutionDomainNameZone);

    public Map<String, Object> delete(DnsPlatformResolutionDomainNameZone dnsPlatformResolutionDomainNameZone);
}
