package com.dns.resolution.domain.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

@Data
public class DnsPlatformResolutionDomainNameZone {
    private Long id;

    private Long zoneId;
    private Long domainId;

    private String geoCode;
    private String zoneContent;
    private Long createTime;
    private Long updateTime;

    public DnsPlatformResolutionDomainNameZone() {}

    public DnsPlatformResolutionDomainNameZone(Long zoneId, Long domainId, String geoCode, String zoneContent, Long createTime, Long updateTime) {
        this.zoneId = zoneId;
        this.domainId = domainId;
        this.geoCode = geoCode;
        this.zoneContent = zoneContent;
        this.createTime = createTime;
        this.updateTime = updateTime;
    }
}
