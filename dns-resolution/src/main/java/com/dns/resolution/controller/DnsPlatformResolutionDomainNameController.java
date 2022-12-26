package com.dns.resolution.controller;

import com.dns.common.core.domain.AjaxResult;
import com.dns.resolution.annotation.Auth;
import com.dns.resolution.domain.req.DomainNameBody;
import com.dns.resolution.service.DnsPlatformResolutionDomainNameService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/web/domain/name")
public class DnsPlatformResolutionDomainNameController {

    @Autowired
    private DnsPlatformResolutionDomainNameService dnsPlatformResolutionDomainNameService;

    @Auth
    @PostMapping("/add")
    public AjaxResult add(@RequestBody DomainNameBody domainNameBody) {
        return AjaxResult.success(dnsPlatformResolutionDomainNameService.add(domainNameBody));
    }

}
