package com.dns.resolution.service;

import com.dns.common.core.page.TableDataInfo;
import com.dns.resolution.domain.req.DomainNameBody;

import java.util.Map;

public interface DnsPlatformResolutionDomainNameService {

    public Map<String, Object> addDomainName(DomainNameBody domainNameBody);

    public TableDataInfo list(DomainNameBody domainNameBody);

    public Map<String, Object> dnssec(DomainNameBody domainNameBody);

    public Map<String, Object> delete(DomainNameBody domainNameBody);

}
