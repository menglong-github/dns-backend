package com.dns.resolution.service;

import com.dns.resolution.domain.req.DomainNameBody;

import java.util.Map;

public interface DnsPlatformResolutionDomainNameService {

    public Map<String, Object> add(DomainNameBody domainNameBody);

}
