package com.dns.resolution.service;

import com.dns.common.core.domain.AjaxResult;
import com.dns.common.core.page.TableDataInfo;
import com.dns.resolution.domain.dto.DnsPlatformResolutionDomainName;
import com.dns.resolution.domain.req.DomainNameBody;
import com.dns.resolution.domain.res.DnsPlatformResolutionDomainNameView;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

public interface DnsPlatformResolutionDomainNameService {

    public Map<String, Object> addDomainName(DomainNameBody domainNameBody);

    public TableDataInfo list(DomainNameBody domainNameBody);

    public Map<String, Object> dnssec(DomainNameBody domainNameBody);

    public Map<String, Object> delete(DomainNameBody domainNameBody);

}
