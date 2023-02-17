package com.dns.resolution.controller;

import com.dns.common.core.domain.AjaxResult;
import com.dns.common.core.page.TableDataInfo;
import com.dns.resolution.annotation.Auth;
import com.dns.resolution.domain.req.DomainNameBody;
import com.dns.resolution.service.DnsPlatformResolutionDomainNameService;
import dns.core.DNSKEYRecord;
import dns.core.DSRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.io.*;

@RestController
@RequestMapping("/web/domain/name")
public class DnsPlatformResolutionDomainNameController {

    @Autowired
    private DnsPlatformResolutionDomainNameService dnsPlatformResolutionDomainNameService;

    @Auth
    @PostMapping
    public AjaxResult add(@RequestBody DomainNameBody domainNameBody) {
        return AjaxResult.success(dnsPlatformResolutionDomainNameService.addDomainName(domainNameBody));
    }

    @Auth
    @GetMapping
    public TableDataInfo list(DomainNameBody domainNameBody) {
        return dnsPlatformResolutionDomainNameService.list(domainNameBody);
    }

    @Auth
    @PutMapping("/dnssec")
    public AjaxResult dnssec(@RequestBody DomainNameBody domainNameBody) {
        return AjaxResult.success(dnsPlatformResolutionDomainNameService.dnssec(domainNameBody));
    }

    @Auth
    @DeleteMapping
    public AjaxResult delete(@RequestBody DomainNameBody domainNameBody) {
        return AjaxResult.success(dnsPlatformResolutionDomainNameService.delete(domainNameBody));
    }

}
