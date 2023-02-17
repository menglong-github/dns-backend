package com.dns.resolution.controller;

import com.dns.common.core.domain.AjaxResult;
import com.dns.resolution.annotation.Auth;
import com.dns.resolution.domain.dto.DnsPlatformResolutionDomainNameZone;
import com.dns.resolution.domain.req.DomainNameBody;
import com.dns.resolution.service.DnsPlatformResolutionDomainNameZoneService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/web/domain/name/zone")
public class DnsPlatformResolutionDomainNameZoneController {

    @Autowired
    private DnsPlatformResolutionDomainNameZoneService dnsPlatformResolutionDomainNameZoneService;

    @Auth
    @GetMapping
    public AjaxResult list(DomainNameBody domainNameBody) {
        return AjaxResult.success(dnsPlatformResolutionDomainNameZoneService.list(domainNameBody));
    }

    @Auth
    @PostMapping
    public AjaxResult add(@RequestBody DnsPlatformResolutionDomainNameZone dnsPlatformResolutionDomainNameZone) {
        return AjaxResult.success(dnsPlatformResolutionDomainNameZoneService.add(dnsPlatformResolutionDomainNameZone));
    }

    @Auth
    @PutMapping
    public AjaxResult update(@RequestBody DnsPlatformResolutionDomainNameZone dnsPlatformResolutionDomainNameZone) {
        return AjaxResult.success(dnsPlatformResolutionDomainNameZoneService.update(dnsPlatformResolutionDomainNameZone));
    }

    @Auth
    @DeleteMapping
    public AjaxResult delete(@RequestBody DnsPlatformResolutionDomainNameZone dnsPlatformResolutionDomainNameZone) {
        return AjaxResult.success(dnsPlatformResolutionDomainNameZoneService.delete(dnsPlatformResolutionDomainNameZone));
    }

}
