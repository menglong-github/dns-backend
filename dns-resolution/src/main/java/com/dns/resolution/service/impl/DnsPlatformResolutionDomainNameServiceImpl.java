package com.dns.resolution.service.impl;

import com.dns.common.core.domain.entity.SysDictData;
import com.dns.common.core.redis.RedisCache;
import com.dns.common.utils.DictUtils;
import com.dns.common.utils.StringUtils;
import com.dns.common.utils.uuid.IdUtils;
import com.dns.resolution.constants.DomainNameConstants;
import com.dns.resolution.domain.dto.DnsPlatformResolutionDomainName;
import com.dns.resolution.domain.req.DomainNameBody;
import com.dns.resolution.service.DnsPlatformResolutionDomainNameService;
import com.dns.resolution.utils.AuthUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.xbill.DNS.*;

import java.net.IDN;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class DnsPlatformResolutionDomainNameServiceImpl implements DnsPlatformResolutionDomainNameService {

    @Autowired
    private RedisCache redisCache;

    @Autowired
    private AuthUtils authUtils;

    private Integer insertResolutionDomainName() {



        return 0;
    }

    @Override
    public Map<String, Object> add(DomainNameBody domainNameBody) {
        Map<String, Object> resultMap = new HashMap<>();
        if (StringUtils.isNotEmpty(domainNameBody.getDomainName())) {
            Name name = null;
            try {
                domainNameBody.setDomainName(IDN.toASCII(domainNameBody.getDomainName().toLowerCase()));
                name = new Name(domainNameBody.getDomainName());
            } catch (Exception exception) {
                resultMap.put("message", "Domain name format error");
                resultMap.put("code", 100002);
            }
            if (name != null) {
                String domainNameString = name.toString();
                String domainNameExtension = null;
                List<SysDictData> supportResolutionDomainNameExtensionList = DictUtils.getDictCache(DomainNameConstants.SUPPORT_RESOLUTION_DOMAIN_NAME_EXTENSION_DICT_KEY);
                int supportResolutionDomainNameExtensionListLength = supportResolutionDomainNameExtensionList.size();
                boolean isSupportResolutionDomainNameExtension = false;
                for (int supportResolutionDomainNameExtensionIndex = 0; supportResolutionDomainNameExtensionIndex < supportResolutionDomainNameExtensionListLength; supportResolutionDomainNameExtensionIndex++) {
                    String supportExtension = supportResolutionDomainNameExtensionList.get(supportResolutionDomainNameExtensionIndex).getDictValue();
                    if (domainNameString.endsWith(supportExtension)) {
                        isSupportResolutionDomainNameExtension = true;
                        domainNameExtension = (domainNameExtension == null) ? supportExtension : ((supportExtension.length() > domainNameExtension.length()) ? supportExtension : domainNameExtension);
                    }
                }
                if (isSupportResolutionDomainNameExtension) {
                    DnsPlatformResolutionDomainName dnsPlatformResolutionDomainName = redisCache.getCacheObject(DomainNameConstants.EXIST_RESOLUTION_DOMAIN_NAME_CACHE_KEY + domainNameString);
                    if (dnsPlatformResolutionDomainName == null) {
                        boolean isSubdomain = domainNameString.substring(0, domainNameString.length() - domainNameExtension.length()).contains(".");
                        if (isSubdomain) {
                            String addSubdomainNameVerifyTxtRecordCacheKey = DomainNameConstants.ADD_RESOLUTION_SUBDOMAIN_NAME_VERIFY_TXT_RECORD_CACHE_KEY + authUtils.getLoginUser().getUserId() + domainNameString;
                            String verifyTxtRecordContent = redisCache.getCacheObject(addSubdomainNameVerifyTxtRecordCacheKey);
                            if (StringUtils.isNotEmpty(verifyTxtRecordContent)) {
                                try {
                                    Lookup lookup = new Lookup(new Name(DomainNameConstants.ADD_RESOLUTION_SUBDOMAIN_NAME_VERIFY_TXT_RECORD_PREFIX + domainNameString), Type.TXT);
                                    lookup.run();
                                    if (lookup.getResult() == Lookup.SUCCESSFUL) {
                                        Record[] txtRecords = lookup.getAnswers();
                                        boolean verifyTxtRecordResult = false;
                                        for (int txtRecordIndex = 0; txtRecordIndex < txtRecords.length; txtRecordIndex++) {
                                            String txtRecordContent = txtRecords[txtRecordIndex].rdataToString();
                                            if (verifyTxtRecordContent.contentEquals(txtRecordContent.substring(1, txtRecordContent.length() - 1))) {
                                                verifyTxtRecordResult = true;
                                                break;
                                            }
                                        }
                                        if (verifyTxtRecordResult) {
                                            //添加
                                            redisCache.deleteObject(addSubdomainNameVerifyTxtRecordCacheKey);
                                            resultMap.put("message", "Successfully added");
                                            resultMap.put("code", 0);
                                        } else {
                                            resultMap.put("message", "TXT record text validation error, please check carefully");
                                            resultMap.put("content", verifyTxtRecordContent);
                                            resultMap.put("code", 100012);
                                        }
                                    } else {
                                        resultMap.put("message", "TXT record not found, please confirm whether to add");
                                        resultMap.put("content", verifyTxtRecordContent);
                                        resultMap.put("code", 100011);
                                    }
                                } catch (Exception exception) {
                                    resultMap.put("message", "TXT record validation exception, please try again later");
                                    resultMap.put("code", 100010);
                                }
                            } else {
                                verifyTxtRecordContent = IdUtils.fastSimpleUUID();
                                redisCache.setCacheObject(addSubdomainNameVerifyTxtRecordCacheKey, verifyTxtRecordContent, DomainNameConstants.ADD_RESOLUTION_SUBDOMAIN_NAME_VERIFY_TXT_RECORD_LIMIT_TIME, TimeUnit.MINUTES);
                                resultMap.put("message", "To add a subdomain name, please verify the TXT record");
                                resultMap.put("content", verifyTxtRecordContent);
                                resultMap.put("code", 100009);
                            }
                        } else {
                            //执行添加
                            resultMap.put("message", "Successfully added");
                            resultMap.put("code", 0);
                        }
                    } else {
                        if (dnsPlatformResolutionDomainName.getUserId().longValue() == authUtils.getLoginUser().getUserId()) {
                            resultMap.put("message", "The domain name already exists");
                            resultMap.put("code", 100004);
                        } else {
                            String addSubdomainNameVerifyTxtRecordCacheKey = DomainNameConstants.ADD_RESOLUTION_SUBDOMAIN_NAME_VERIFY_TXT_RECORD_CACHE_KEY + authUtils.getLoginUser().getUserId() + domainNameString;
                            String verifyTxtRecordContent = redisCache.getCacheObject(addSubdomainNameVerifyTxtRecordCacheKey);
                            if (StringUtils.isNotEmpty(verifyTxtRecordContent)) {
                                try {
                                    Lookup lookup = new Lookup(new Name(DomainNameConstants.ADD_RESOLUTION_SUBDOMAIN_NAME_VERIFY_TXT_RECORD_PREFIX + domainNameString), Type.TXT);
                                    lookup.run();
                                    if (lookup.getResult() == Lookup.SUCCESSFUL) {
                                        Record[] txtRecords = lookup.getAnswers();
                                        boolean verifyTxtRecordResult = false;
                                        for (int txtRecordIndex = 0; txtRecordIndex < txtRecords.length; txtRecordIndex++) {
                                            String txtRecordContent = txtRecords[txtRecordIndex].rdataToString();
                                            if (verifyTxtRecordContent.contentEquals(txtRecordContent.substring(1, txtRecordContent.length() - 1))) {
                                                verifyTxtRecordResult = true;
                                                break;
                                            }
                                        }
                                        if (verifyTxtRecordResult) {
                                            //添加
                                            redisCache.deleteObject(addSubdomainNameVerifyTxtRecordCacheKey);
                                            resultMap.put("message", "Successfully added");
                                            resultMap.put("code", 0);
                                        } else {
                                            resultMap.put("message", "TXT record text validation error, please check carefully");
                                            resultMap.put("content", verifyTxtRecordContent);
                                            resultMap.put("code", 100008);
                                        }
                                    } else {
                                        resultMap.put("message", "TXT record not found, please confirm whether to add");
                                        resultMap.put("content", verifyTxtRecordContent);
                                        resultMap.put("code", 100007);
                                    }
                                } catch (Exception exception) {
                                    resultMap.put("message", "TXT record validation exception, please try again later");
                                    resultMap.put("content", verifyTxtRecordContent);
                                    resultMap.put("code", 100006);
                                }
                            } else {
                                verifyTxtRecordContent = IdUtils.fastSimpleUUID();
                                redisCache.setCacheObject(addSubdomainNameVerifyTxtRecordCacheKey, verifyTxtRecordContent, DomainNameConstants.ADD_RESOLUTION_SUBDOMAIN_NAME_VERIFY_TXT_RECORD_LIMIT_TIME, TimeUnit.MINUTES);
                                resultMap.put("message", "The domain name has been added by others. Please verify the TXT record authorization");
                                resultMap.put("content", verifyTxtRecordContent);
                                resultMap.put("code", 100005);
                            }
                        }
                    }
                } else {
                    resultMap.put("message", "Unsupported domain name extension");
                    resultMap.put("code", 100003);
                }
            }
        } else {
            resultMap.put("message", "Please input the domain name");
            resultMap.put("code", 100001);
        }

        return resultMap;
    }
}
