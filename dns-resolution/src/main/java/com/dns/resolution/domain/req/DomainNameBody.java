package com.dns.resolution.domain.req;

import lombok.Data;

@Data
public class DomainNameBody {
    private Long userId;
    private Long domainId;
    private String domainName;
    private Integer pageNum;
    private Integer pageSize;
}
