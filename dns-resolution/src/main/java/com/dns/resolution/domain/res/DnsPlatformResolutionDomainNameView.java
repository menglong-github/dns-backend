package com.dns.resolution.domain.res;

import lombok.Data;

@Data
public class DnsPlatformResolutionDomainNameView {
    private Long domainId;
    private String domainName;
    private Boolean dnssecEnable;
    private Integer dnssecDsKeyTag;
    private String dnssecDsDigestValue;
    private Long createTime;
    private Long updateTime;
}
