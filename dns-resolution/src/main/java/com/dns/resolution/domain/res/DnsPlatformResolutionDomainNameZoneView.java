package com.dns.resolution.domain.res;

import lombok.Data;

@Data
public class DnsPlatformResolutionDomainNameZoneView {
    private Long zoneId;
    private Long domainId;
    private String geoCode;
    private String zoneContent;
    private Long createTime;
    private Long updateTime;
}
