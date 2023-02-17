package com.dns.resolution.domain.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

@Data
public class DnsPlatformResolutionDomainName {
    private Long id;
    private Long userId;
    private Long domainId;
    private String domainName;
    private Boolean dnssecEnable;
    private String dnssecKskPrivateKey;
    private String dnssecKskPublicKey;
    private String dnssecZskPrivateKey;
    private String dnssecZskPublicKey;
    private Integer dnssecDsKeyTag;
    private String dnssecDsDigestValue;
    private Long createTime;
    private Long updateTime;
}
