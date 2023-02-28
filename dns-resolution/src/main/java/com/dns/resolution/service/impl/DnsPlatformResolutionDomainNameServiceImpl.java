package com.dns.resolution.service.impl;

import com.dns.common.core.domain.entity.SysDictData;
import com.dns.common.core.page.TableDataInfo;
import com.dns.common.core.redis.RedisCache;
import com.dns.common.utils.DictUtils;
import com.dns.common.utils.StringUtils;
import com.dns.common.utils.uuid.IdUtils;
import com.dns.resolution.constants.DomainNameConstants;
import com.dns.resolution.domain.dto.DnsPlatformResolutionDomainName;
import com.dns.resolution.domain.dto.DnsPlatformResolutionDomainNameZone;
import com.dns.resolution.domain.req.DomainNameBody;
import com.dns.resolution.domain.res.DnsPlatformResolutionDomainNameView;
import com.dns.resolution.mapper.DnsPlatformResolutionDomainNameMapper;
import com.dns.resolution.mapper.DnsPlatformResolutionDomainNameZoneMapper;
import com.dns.resolution.service.DnsPlatformResolutionDomainNameService;
import com.dns.resolution.utils.*;
import com.rabbitmq.client.Channel;
import dns.core.*;
import dns.core.utils.base16;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.*;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static com.dns.common.utils.PageUtils.startPage;

@Service
public class DnsPlatformResolutionDomainNameServiceImpl implements DnsPlatformResolutionDomainNameService {

    @Autowired
    private RedisCache redisCache;

    @Autowired
    private AuthUtils authUtils;

    @Autowired
    private SnowflakeUtils snowflakeUtils;

    @Autowired
    private PageUtils pageUtils;

    @Autowired
    private RabbitMqUtils rabbitMqUtils;

    @Autowired
    private IDNUtils idnUtils;

    @Autowired
    private DnsPlatformResolutionDomainNameMapper dnsPlatformResolutionDomainNameMapper;

    @Autowired
    private DnsPlatformResolutionDomainNameZoneMapper dnsPlatformResolutionDomainNameZoneMapper;

    @Transactional
    @Override
    public Map<String, Object> addDomainName(DomainNameBody domainNameBody) {
        Map<String, Object> resultMap = new HashMap<>();//结果
        if (StringUtils.isEmpty(domainNameBody.getDomainName())) {
            resultMap.put("message", "Please input a domain name");
            resultMap.put("code", 100001);
        } else {
            Name master = null;
            String domainName = null;
            try {
                domainName = idnUtils.toASCII(domainNameBody.getDomainName()) + ".";
                master = new Name(domainName);
            } catch (Exception exception) {
                resultMap.put("message", "Domain name format error");
                resultMap.put("code", 100002);
            }
            if ((domainName != null) && (master != null)) {
                String domainNameExtension = null;
                List<SysDictData> supportResolutionDomainNameExtensionList = DictUtils.getDictCache(DomainNameConstants.SUPPORT_RESOLUTION_DOMAIN_NAME_EXTENSION_DICT_KEY);
                int supportResolutionDomainNameExtensionListLength = supportResolutionDomainNameExtensionList.size();
                boolean isSupportResolutionDomainNameExtension = false;
                for (int supportResolutionDomainNameExtensionIndex = 0; supportResolutionDomainNameExtensionIndex < supportResolutionDomainNameExtensionListLength; supportResolutionDomainNameExtensionIndex++) {
                    String supportExtension = supportResolutionDomainNameExtensionList.get(supportResolutionDomainNameExtensionIndex).getDictValue();
                    if (domainName.endsWith(supportExtension)) {
                        isSupportResolutionDomainNameExtension = true;
                        domainNameExtension = (domainNameExtension == null) ? supportExtension : ((supportExtension.length() > domainNameExtension.length()) ? supportExtension : domainNameExtension);
                    }
                }
                if (isSupportResolutionDomainNameExtension) {
                    boolean isOperation = false;
                    Long userId = authUtils.getLoginUser().getUserId();
                    DnsPlatformResolutionDomainName dnsPlatformResolutionDomainName = redisCache.getCacheObject(DomainNameConstants.EXIST_RESOLUTION_DOMAIN_NAME_CACHE_KEY + domainName);
                    if (dnsPlatformResolutionDomainName == null) {
                        boolean isSubdomain = domainName.substring(0, domainName.length() - domainNameExtension.length()).contains(".");
                        if (isSubdomain) {
                            String addSubdomainNameVerifyTxtRecordCacheKey = DomainNameConstants.ADD_RESOLUTION_DOMAIN_NAME_VERIFY_TXT_RECORD_CACHE_KEY + userId + ":" + domainName;
                            String verifyTxtRecordContent = redisCache.getCacheObject(addSubdomainNameVerifyTxtRecordCacheKey);
                            if (StringUtils.isNotEmpty(verifyTxtRecordContent)) {
                                try {
                                    Lookup lookup = new Lookup(new Name(DomainNameConstants.ADD_RESOLUTION_DOMAIN_NAME_VERIFY_TXT_RECORD_PREFIX + domainName), Type.TXT);
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
                                            isOperation = true;
                                        } else {
                                            resultMap.put("message", "TXT record text validation error, please check carefully");
                                            resultMap.put("domain", DomainNameConstants.ADD_RESOLUTION_DOMAIN_NAME_VERIFY_TXT_RECORD_PREFIX + domainName);
                                            resultMap.put("content", verifyTxtRecordContent);
                                            resultMap.put("code", 100003);
                                        }
                                    } else {
                                        resultMap.put("message", "TXT record not found, please confirm whether to add");
                                        resultMap.put("domain", DomainNameConstants.ADD_RESOLUTION_DOMAIN_NAME_VERIFY_TXT_RECORD_PREFIX + domainName);
                                        resultMap.put("content", verifyTxtRecordContent);
                                        resultMap.put("code", 100004);
                                    }
                                } catch (Exception exception) {
                                    resultMap.put("message", "TXT record validation exception, please try again later");
                                    resultMap.put("code", 100005);
                                }
                            } else {
                                verifyTxtRecordContent = IdUtils.fastSimpleUUID();
                                redisCache.setCacheObject(addSubdomainNameVerifyTxtRecordCacheKey, verifyTxtRecordContent, DomainNameConstants.ADD_RESOLUTION_DOMAIN_NAME_VERIFY_TXT_RECORD_LIMIT_TIME, TimeUnit.MINUTES);
                                resultMap.put("message", "To add a subdomain name, please verify the TXT record");
                                resultMap.put("domain", DomainNameConstants.ADD_RESOLUTION_DOMAIN_NAME_VERIFY_TXT_RECORD_PREFIX + domainName);
                                resultMap.put("content", verifyTxtRecordContent);
                                resultMap.put("code", 100006);
                            }
                        } else {
                            isOperation = true;
                        }
                    } else {
                        if (dnsPlatformResolutionDomainName.getUserId().longValue() == userId.longValue()) {
                            resultMap.put("message", "Domain name has been added");
                            resultMap.put("code", 100007);
                        } else {
                            String addSubdomainNameVerifyTxtRecordCacheKey = DomainNameConstants.ADD_RESOLUTION_DOMAIN_NAME_VERIFY_TXT_RECORD_CACHE_KEY + userId + ":" + domainName;
                            String verifyTxtRecordContent = redisCache.getCacheObject(addSubdomainNameVerifyTxtRecordCacheKey);
                            if (StringUtils.isNotEmpty(verifyTxtRecordContent)) {
                                try {
                                    Lookup lookup = new Lookup(new Name(DomainNameConstants.ADD_RESOLUTION_DOMAIN_NAME_VERIFY_TXT_RECORD_PREFIX + domainName), Type.TXT);
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
                                            isOperation = true;
                                        } else {
                                            resultMap.put("message", "TXT record text validation error, please check carefully");
                                            resultMap.put("domain", DomainNameConstants.ADD_RESOLUTION_DOMAIN_NAME_VERIFY_TXT_RECORD_PREFIX + domainName);
                                            resultMap.put("content", verifyTxtRecordContent);
                                            resultMap.put("code", 100003);
                                        }
                                    } else {
                                        resultMap.put("message", "TXT record not found, please confirm whether to add");
                                        resultMap.put("domain", DomainNameConstants.ADD_RESOLUTION_DOMAIN_NAME_VERIFY_TXT_RECORD_PREFIX + domainName);
                                        resultMap.put("content", verifyTxtRecordContent);
                                        resultMap.put("code", 100004);
                                    }
                                } catch (Exception exception) {
                                    resultMap.put("message", "TXT record validation exception, please try again later");
                                    resultMap.put("domain", DomainNameConstants.ADD_RESOLUTION_DOMAIN_NAME_VERIFY_TXT_RECORD_PREFIX + domainName);
                                    resultMap.put("content", verifyTxtRecordContent);
                                    resultMap.put("code", 100005);
                                }
                            } else {
                                verifyTxtRecordContent = IdUtils.fastSimpleUUID();
                                redisCache.setCacheObject(addSubdomainNameVerifyTxtRecordCacheKey, verifyTxtRecordContent, DomainNameConstants.ADD_RESOLUTION_DOMAIN_NAME_VERIFY_TXT_RECORD_LIMIT_TIME, TimeUnit.MINUTES);
                                resultMap.put("message", "The domain name has been added by others. Please verify the TXT record authorization");
                                resultMap.put("domain", DomainNameConstants.ADD_RESOLUTION_DOMAIN_NAME_VERIFY_TXT_RECORD_PREFIX + domainName);
                                resultMap.put("content", verifyTxtRecordContent);
                                resultMap.put("code", 100008);
                            }
                        }
                    }
                    if (isOperation) {
                        String resolutionDomainNameLockCacheKey = DomainNameConstants.RESOLUTION_DOMAIN_NAME_LOCK_CACHE_KEY + domainName;
                        long addResolutionDomainNameLockCount =  redisCache.redisTemplate.opsForValue().increment(resolutionDomainNameLockCacheKey);
                        if (addResolutionDomainNameLockCount == 1) {
                            redisCache.expire(resolutionDomainNameLockCacheKey, DomainNameConstants.RESOLUTION_DOMAIN_NAME_LOCK_EXPIRE_TIME, TimeUnit.MINUTES);
                            long nowTime = System.currentTimeMillis();
                            boolean isUpdate = false;
                            if (dnsPlatformResolutionDomainName == null) {
                                dnsPlatformResolutionDomainName = new DnsPlatformResolutionDomainName();
                                dnsPlatformResolutionDomainName.setUserId(userId);
                                dnsPlatformResolutionDomainName.setDomainId(snowflakeUtils.nextId());
                                dnsPlatformResolutionDomainName.setDomainName(domainName);
                                dnsPlatformResolutionDomainName.setDnssecEnable(false);
                                dnsPlatformResolutionDomainName.setCreateTime(nowTime);
                                dnsPlatformResolutionDomainName.setUpdateTime(nowTime);
                            } else {
                                dnsPlatformResolutionDomainName.setUserId(userId);
                                dnsPlatformResolutionDomainName.setUpdateTime(nowTime);
                                isUpdate = true;
                            }
                            dnsPlatformResolutionDomainNameMapper.insertDnsPlatformResolutionDomainName(dnsPlatformResolutionDomainName);
                            if (!isUpdate) {
                                List<Record> defaultRecordList = new ArrayList<>();
                                try {
                                    defaultRecordList.add(new SOARecord(new Name(domainName), DClass.IN, 0, new Name(DomainNameConstants.ADD_RESOLUTION_DOMAIN_NAME_DEFAULT_SOA_MASTER_NAME), new Name(DomainNameConstants.ADD_RESOLUTION_DOMAIN_NAME_DEFAULT_SOA_ADMIN_NAME), 0, 0, 0, 0, 0));
                                    defaultRecordList.add(new NSRecord(new Name(domainName), DClass.IN, 0, new Name(DomainNameConstants.ADD_RESOLUTION_DOMAIN_NAME_DEFAULT_SOA_MASTER_NAME)));
                                    defaultRecordList.add(new NSRecord(new Name(domainName), DClass.IN, 0, new Name(DomainNameConstants.ADD_RESOLUTION_DOMAIN_NAME_DEFAULT_NS_SLAVE_NAME)));
                                    Zone zone = new Zone(new Name(domainName), defaultRecordList.toArray(new Record[0]));
                                    Map<String, Zone> zoneMap = new HashMap<>();
                                    zoneMap.put("*", zone);
                                    DnsPlatformResolutionDomainNameZone dnsPlatformResolutionDomainNameZone = new DnsPlatformResolutionDomainNameZone(snowflakeUtils.nextId(), dnsPlatformResolutionDomainName.getDomainId(), "*", zone.toString(), nowTime, nowTime);
                                    dnsPlatformResolutionDomainNameZoneMapper.insertDnsPlatformResolutionDomainNameZone(dnsPlatformResolutionDomainNameZone);
                                    TransferZone transferZone = new TransferZone();
                                    transferZone.setMaster(domainName);
                                    transferZone.setZoneMap(zoneMap);
                                    transferZone.setOperationType(1);
                                    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                                    ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteArrayOutputStream);
                                    objectOutputStream.writeObject(transferZone);
                                    Channel channel = rabbitMqUtils.getChannel();
                                    channel.basicPublish(DomainNameConstants.RESOLUTION_DOMAIN_NAME_MQ_EXCHANGE_NAME, "", null, byteArrayOutputStream.toByteArray());
                                    channel.close();
                                } catch (IOException | TimeoutException e) {
                                    throw new RuntimeException(e);
                                }
                            }
                            redisCache.setCacheObject(DomainNameConstants.EXIST_RESOLUTION_DOMAIN_NAME_CACHE_KEY + domainName, dnsPlatformResolutionDomainName);
                            resultMap.put("message", "Operation succeeded");
                            resultMap.put("code", 0);
                        } else {
                            resultMap.put("message", "The domain name is in operation, please try again later");
                            resultMap.put("code", 100009);
                        }
                    }
                } else {
                    resultMap.put("message", "Unsupported domain name extension");
                    resultMap.put("code", 100010);
                }
            }
        }
        return resultMap;
    }

    @Override
    public TableDataInfo list(DomainNameBody domainNameBody) {
        domainNameBody.setDomainName(idnUtils.toASCII(domainNameBody.getDomainName()));
        domainNameBody.setUserId(authUtils.getLoginUser().getUserId());
        domainNameBody.setPageSize(10);
        startPage();
        return pageUtils.getDataTable(dnsPlatformResolutionDomainNameMapper.selectDnsPlatformResolutionDomainNameViewListByUserId(domainNameBody));
    }


    @Transactional
    @Override
    public Map<String, Object> dnssec(DomainNameBody domainNameBody) {
        Map<String, Object> resultMap = new HashMap<>();
        if (domainNameBody.getDomainId() != null) {
            DnsPlatformResolutionDomainName dnsPlatformResolutionDomainName = dnsPlatformResolutionDomainNameMapper.selectDnsPlatformResolutionDomainNameByDomainId(domainNameBody.getDomainId());
            if (dnsPlatformResolutionDomainName != null) {
                Long userId = authUtils.getLoginUser().getUserId();
                if (userId.longValue() == dnsPlatformResolutionDomainName.getUserId().longValue()) {
                    Long nowTime = System.currentTimeMillis();
                    dnsPlatformResolutionDomainName.setUpdateTime(nowTime);
                    TransferZone transferZone = new TransferZone();
                    transferZone.setMaster(dnsPlatformResolutionDomainName.getDomainName());
                    transferZone.setOperationType(1);
                    Map<String, Zone> zoneMap = new HashMap<>();
                    try {
                        if (dnsPlatformResolutionDomainName.getDnssecEnable()) {
                            dnsPlatformResolutionDomainName.setDnssecEnable(false);
                            dnsPlatformResolutionDomainName.setDnssecKskPrivateKey(null);
                            dnsPlatformResolutionDomainName.setDnssecKskPublicKey(null);
                            dnsPlatformResolutionDomainName.setDnssecZskPrivateKey(null);
                            dnsPlatformResolutionDomainName.setDnssecZskPublicKey(null);
                            dnsPlatformResolutionDomainName.setDnssecDsKeyTag(null);
                            dnsPlatformResolutionDomainName.setDnssecDsDigestValue(null);
                            dnsPlatformResolutionDomainNameMapper.updateDnsPlatformResolutionDomainNameDnssec(dnsPlatformResolutionDomainName);
                            List<DnsPlatformResolutionDomainNameZone> dnsPlatformResolutionDomainNameZoneList = dnsPlatformResolutionDomainNameZoneMapper.selectDnsPlatformResolutionDomainNameZoneByDomainId(dnsPlatformResolutionDomainName.getDomainId());
                            for (DnsPlatformResolutionDomainNameZone dnsPlatformResolutionDomainNameZone : dnsPlatformResolutionDomainNameZoneList) {
                                InputStream inputStream = new ByteArrayInputStream(dnsPlatformResolutionDomainNameZone.getZoneContent().getBytes());
                                Zone zone = new Zone(new Name(dnsPlatformResolutionDomainName.getDomainName()), inputStream);
                                List<Record> deleteRecordList = new ArrayList<>();
                                Iterator<RRset> rRsetIterator = zone.iterator();
                                while (rRsetIterator.hasNext()) {
                                    RRset rRset = rRsetIterator.next();
                                    deleteRecordList.addAll(rRset.sigs());
                                    if ((rRset.getType() == Type.DNSKEY) || (rRset.getType() == Type.NSEC)) {
                                        deleteRecordList.addAll(rRset.rrs());
                                    }
                                }
                                deleteRecordList.forEach(record -> {
                                    zone.removeRecord(record);
                                });
                                zone.dnssec = false;
                                zoneMap.put(dnsPlatformResolutionDomainNameZone.getGeoCode(), zone);
                                dnsPlatformResolutionDomainNameZone.setZoneContent(zone.toString());
                                dnsPlatformResolutionDomainNameZone.setUpdateTime(nowTime);
                                dnsPlatformResolutionDomainNameZoneMapper.updateDnsPlatformResolutionDomainNameZone(dnsPlatformResolutionDomainNameZone);
                            }
                            transferZone.setZoneMap(zoneMap);
                            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                            ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteArrayOutputStream);
                            objectOutputStream.writeObject(transferZone);
                            Channel channel = rabbitMqUtils.getChannel();
                            channel.basicPublish(DomainNameConstants.RESOLUTION_DOMAIN_NAME_MQ_EXCHANGE_NAME, "", null, byteArrayOutputStream.toByteArray());
                            channel.close();
                        } else {
                            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("EC");
                            keyPairGenerator.initialize(new ECGenParameterSpec("secp256r1"), new SecureRandom());
                            KeyPair kskKeyPair = keyPairGenerator.generateKeyPair();
                            ECPublicKey kskEcPublicKey = (ECPublicKey) kskKeyPair.getPublic();
                            ECPrivateKey kskEcPrivateKey = (ECPrivateKey) kskKeyPair.getPrivate();
                            KeyPair zskKeyPair = keyPairGenerator.generateKeyPair();
                            ECPublicKey zskEcPublicKey = (ECPublicKey) zskKeyPair.getPublic();
                            ECPrivateKey zskEcPrivateKey = (ECPrivateKey) zskKeyPair.getPrivate();
                            DNSKEYRecord kskRecord = new DNSKEYRecord(Name.fromString(dnsPlatformResolutionDomainName.getDomainName()), DClass.IN, 3600, 0x101, DNSKEYRecord.Protocol.DNSSEC, DNSSEC.Algorithm.ECDSAP256SHA256, kskEcPublicKey);
                            DNSKEYRecord zskRecord = new DNSKEYRecord(Name.fromString(dnsPlatformResolutionDomainName.getDomainName()), DClass.IN, 3600, DNSKEYRecord.Flags.ZONE_KEY, DNSKEYRecord.Protocol.DNSSEC, DNSSEC.Algorithm.ECDSAP256SHA256, zskEcPublicKey);
                            DSRecord dsRecord = new DSRecord(new Name(dnsPlatformResolutionDomainName.getDomainName()), DClass.IN, 3600, DNSSEC.Digest.SHA256, kskRecord);
                            dnsPlatformResolutionDomainName.setDnssecEnable(true);
                            dnsPlatformResolutionDomainName.setDnssecKskPrivateKey(Base64.getEncoder().encodeToString(kskEcPrivateKey.getEncoded()));
                            dnsPlatformResolutionDomainName.setDnssecKskPublicKey(Base64.getEncoder().encodeToString(kskEcPublicKey.getEncoded()));
                            dnsPlatformResolutionDomainName.setDnssecZskPrivateKey(Base64.getEncoder().encodeToString(zskEcPrivateKey.getEncoded()));
                            dnsPlatformResolutionDomainName.setDnssecZskPublicKey(Base64.getEncoder().encodeToString(zskEcPublicKey.getEncoded()));
                            dnsPlatformResolutionDomainName.setDnssecDsKeyTag(dsRecord.getFootprint());
                            dnsPlatformResolutionDomainName.setDnssecDsDigestValue(base16.toString(dsRecord.getDigest()));
                            dnsPlatformResolutionDomainNameMapper.updateDnsPlatformResolutionDomainNameDnssec(dnsPlatformResolutionDomainName);
                            List<DnsPlatformResolutionDomainNameZone> dnsPlatformResolutionDomainNameZoneList = dnsPlatformResolutionDomainNameZoneMapper.selectDnsPlatformResolutionDomainNameZoneByDomainId(dnsPlatformResolutionDomainName.getDomainId());
                            Calendar calendar = Calendar.getInstance();
                            calendar.add(Calendar.YEAR, 1);
                            Instant inception = Instant.now();
                            Instant expiration = Instant.ofEpochMilli(calendar.getTimeInMillis());
                            for (DnsPlatformResolutionDomainNameZone dnsPlatformResolutionDomainNameZone : dnsPlatformResolutionDomainNameZoneList) {
                                InputStream inputStream = new ByteArrayInputStream(dnsPlatformResolutionDomainNameZone.getZoneContent().getBytes());
                                try {
                                    Zone zone = new Zone(new Name(dnsPlatformResolutionDomainName.getDomainName()), inputStream);
                                    zone.addRecord(kskRecord);
                                    zone.addRecord(zskRecord);
                                    zone.addRecord(new NSECRecord(new Name(dnsPlatformResolutionDomainName.getDomainName()), DClass.IN, 3600, new Name(dnsPlatformResolutionDomainName.getDomainName()), new int[]{Type.NSEC}));
                                    Iterator<RRset> rRsetIterator = zone.iterator();
                                    while (rRsetIterator.hasNext()) {
                                        RRset rRset = rRsetIterator.next();
                                        if (rRset.getType() == Type.DNSKEY) {
                                            zone.addRecord(DNSSEC.sign(rRset, kskRecord, kskEcPrivateKey, inception, expiration));
                                        } else {
                                            zone.addRecord(DNSSEC.sign(rRset, zskRecord, zskEcPrivateKey, inception, expiration));
                                        }
                                    }
                                    zone.dnssec = true;
                                    zoneMap.put(dnsPlatformResolutionDomainNameZone.getGeoCode(), zone);
                                    dnsPlatformResolutionDomainNameZone.setZoneContent(zone.toString());
                                    dnsPlatformResolutionDomainNameZone.setUpdateTime(nowTime);
                                    dnsPlatformResolutionDomainNameZoneMapper.updateDnsPlatformResolutionDomainNameZone(dnsPlatformResolutionDomainNameZone);
                                } catch (IOException | DNSSEC.DNSSECException e) {
                                    throw new RuntimeException(e);
                                }
                            }
                            transferZone.setZoneMap(zoneMap);
                            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                            ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteArrayOutputStream);
                            objectOutputStream.writeObject(transferZone);
                            Channel channel = rabbitMqUtils.getChannel();
                            channel.basicPublish(DomainNameConstants.RESOLUTION_DOMAIN_NAME_MQ_EXCHANGE_NAME, "", null, byteArrayOutputStream.toByteArray());
                            channel.close();
                        }
                        DnsPlatformResolutionDomainNameView dnsPlatformResolutionDomainNameView = new DnsPlatformResolutionDomainNameView();
                        dnsPlatformResolutionDomainNameView.setDomainId(dnsPlatformResolutionDomainName.getDomainId());
                        dnsPlatformResolutionDomainNameView.setDomainName(dnsPlatformResolutionDomainName.getDomainName());
                        dnsPlatformResolutionDomainNameView.setDnssecEnable(dnsPlatformResolutionDomainName.getDnssecEnable());
                        dnsPlatformResolutionDomainNameView.setDnssecDsKeyTag(dnsPlatformResolutionDomainName.getDnssecDsKeyTag());
                        dnsPlatformResolutionDomainNameView.setDnssecDsDigestValue(dnsPlatformResolutionDomainName.getDnssecDsDigestValue());
                        dnsPlatformResolutionDomainNameView.setCreateTime(dnsPlatformResolutionDomainName.getCreateTime());
                        dnsPlatformResolutionDomainNameView.setUpdateTime(dnsPlatformResolutionDomainName.getUpdateTime());
                        resultMap.put("message", "Operation succeeded");
                        resultMap.put("code", 0);
                        resultMap.put("dnssec", dnsPlatformResolutionDomainNameView);
                    } catch (Exception e) {
                        resultMap.put("message", "Dnssec error");
                        resultMap.put("code", 100006);
                    }
                } else {
                    resultMap.put("message", "Please operate your own domain name");
                    resultMap.put("code", 100008);
                }
            } else {
                resultMap.put("message", "Domain name does not exist");
                resultMap.put("code", 100009);
            }
        } else {
            resultMap.put("message", "Please select the domain name to operate");
            resultMap.put("code", 100001);
        }
        return resultMap;
    }

    @Transactional
    @Override
    public Map<String, Object> delete(DomainNameBody domainNameBody) {
        Map<String, Object> resultMap = new HashMap<>();
        if (domainNameBody.getDomainId() != null) {
            DnsPlatformResolutionDomainName dnsPlatformResolutionDomainName = dnsPlatformResolutionDomainNameMapper.selectDnsPlatformResolutionDomainNameByDomainId(domainNameBody.getDomainId());
            if (dnsPlatformResolutionDomainName != null) {
                Long userId = authUtils.getLoginUser().getUserId();
                if (userId.longValue() == dnsPlatformResolutionDomainName.getUserId().longValue()) {
                    dnsPlatformResolutionDomainNameMapper.deleteDnsPlatformResolutionDomainName(dnsPlatformResolutionDomainName);
                    dnsPlatformResolutionDomainNameZoneMapper.deleteDnsPlatformResolutionDomainNameZoneByDomainId(dnsPlatformResolutionDomainName.getDomainId());
                    TransferZone transferZone = new TransferZone();
                    transferZone.setMaster(dnsPlatformResolutionDomainName.getDomainName());
                    transferZone.setOperationType(0);
                    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                    ObjectOutputStream objectOutputStream;
                    try {
                        objectOutputStream = new ObjectOutputStream(byteArrayOutputStream);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    try {
                        objectOutputStream.writeObject(transferZone);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    try {
                        Channel channel = rabbitMqUtils.getChannel();
                        channel.basicPublish(DomainNameConstants.RESOLUTION_DOMAIN_NAME_MQ_EXCHANGE_NAME, "", null, byteArrayOutputStream.toByteArray());
                        channel.close();
                    } catch (IOException | TimeoutException e) {
                        throw new RuntimeException(e);
                    }
                    redisCache.deleteObject(DomainNameConstants.EXIST_RESOLUTION_DOMAIN_NAME_CACHE_KEY + dnsPlatformResolutionDomainName.getDomainName());
                    resultMap.put("message", "Operation succeeded");
                    resultMap.put("code", 0);
                } else {
                    resultMap.put("message", "Please operate your own domain name");
                    resultMap.put("code", 100009);
                }
            } else {
                resultMap.put("message", "Domain name does not exist");
                resultMap.put("code", 100003);
            }
        } else {
            resultMap.put("message", "Please select the domain name to operate");
            resultMap.put("code", 100001);
        }
        return resultMap;
    }
}
