package com.dns.resolution.service.impl;

import com.dns.common.core.domain.entity.SysDictData;
import com.dns.common.utils.DictUtils;
import com.dns.common.utils.StringUtils;
import com.dns.resolution.constants.DomainNameConstants;
import com.dns.resolution.domain.dto.DnsPlatformResolutionDomainName;
import com.dns.resolution.domain.dto.DnsPlatformResolutionDomainNameZone;
import com.dns.resolution.domain.req.DomainNameBody;
import com.dns.resolution.domain.res.DnsPlatformResolutionDomainNameZoneSimpleGeo;
import com.dns.resolution.domain.res.DnsPlatformResolutionDomainNameZoneView;
import com.dns.resolution.mapper.DnsPlatformResolutionDomainNameMapper;
import com.dns.resolution.mapper.DnsPlatformResolutionDomainNameZoneMapper;
import com.dns.resolution.service.DnsPlatformResolutionDomainNameZoneService;
import com.dns.resolution.utils.AuthUtils;
import com.dns.resolution.utils.RabbitMqUtils;
import com.dns.resolution.utils.SnowflakeUtils;
import com.rabbitmq.client.Channel;
import dns.core.*;
import dns.core.utils.base16;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.*;
import java.net.IDN;
import java.security.*;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeoutException;

@Service
public class DnsPlatformResolutionDomainNameZoneServiceImpl implements DnsPlatformResolutionDomainNameZoneService {

    @Autowired
    private DnsPlatformResolutionDomainNameMapper dnsPlatformResolutionDomainNameMapper;

    @Autowired
    private DnsPlatformResolutionDomainNameZoneMapper dnsPlatformResolutionDomainNameZoneMapper;
    
    @Autowired
    private AuthUtils authUtils;

    @Autowired
    private SnowflakeUtils snowflakeUtils;

    @Autowired
    private RabbitMqUtils rabbitMqUtils;

    @Override
    public Map<String, Object> list(DomainNameBody domainNameBody) {
        Map<String, Object> resultMap = new HashMap<>();
        List<DnsPlatformResolutionDomainNameZoneView> dnsPlatformResolutionDomainNameZoneSimpleViewList = null;
        if (domainNameBody.getDomainId() != null) {
            DnsPlatformResolutionDomainName dnsPlatformResolutionDomainName = dnsPlatformResolutionDomainNameMapper.selectDnsPlatformResolutionDomainNameByDomainId(domainNameBody.getDomainId());
            if ((dnsPlatformResolutionDomainName != null)) {
                Long userId = authUtils.getLoginUser().getUserId();
                if (userId.longValue() == dnsPlatformResolutionDomainName.getUserId().longValue()) {
                    dnsPlatformResolutionDomainNameZoneSimpleViewList = dnsPlatformResolutionDomainNameZoneMapper.selectDnsPlatformResolutionDomainNameZoneSimpleViewByDomainId(domainNameBody.getDomainId());
                    resultMap.put("message", "Operation succeeded");
                    resultMap.put("code", 0);
                    resultMap.put("zone", dnsPlatformResolutionDomainNameZoneSimpleViewList);
                } else {
                    resultMap.put("message", "Please operate your own domain name");
                    resultMap.put("code", 100001);
                }
            } else {
                resultMap.put("message", "Domain name does not exist");
                resultMap.put("code", 100002);
            }
        } else {
            resultMap.put("message", "Please select the domain name to operate");
            resultMap.put("code", 100003);
        }
        return resultMap;
    }

    @Transactional
    @Override
    public Map<String, Object> add(DnsPlatformResolutionDomainNameZone dnsPlatformResolutionDomainNameZone) {
        Map<String, Object> resultMap = new HashMap<>();
        if (dnsPlatformResolutionDomainNameZone.getDomainId() == null) {
            resultMap.put("message", "Please select the domain name to operate");
            resultMap.put("code", 100001);
        } else if (StringUtils.isEmpty(dnsPlatformResolutionDomainNameZone.getGeoCode())) {
            resultMap.put("message", "Please select a geo code");
            resultMap.put("code", 100002);
        } else if (StringUtils.isEmpty(dnsPlatformResolutionDomainNameZone.getZoneContent())) {
            resultMap.put("message", "Please enter the content of the zone file");
            resultMap.put("code", 100003);
        } else {
            if (dnsPlatformResolutionDomainNameZone.getZoneContent().length() > DomainNameConstants.SUPPORT_RESOLUTION_DOMAIN_NAME_ZONE_FILE_LENGTH) {
                resultMap.put("message", "The zone file is too large");
                resultMap.put("code", 100004);
            } else {
                DnsPlatformResolutionDomainName dnsPlatformResolutionDomainName = dnsPlatformResolutionDomainNameMapper.selectDnsPlatformResolutionDomainNameByDomainId(dnsPlatformResolutionDomainNameZone.getDomainId());
                if (dnsPlatformResolutionDomainName != null) {
                    Long userId = authUtils.getLoginUser().getUserId();
                    if (userId.longValue() == dnsPlatformResolutionDomainName.getUserId().longValue()) {
                        List<SysDictData> geoCodeDictList = DictUtils.getDictCache(DomainNameConstants.SUPPORT_RESOLUTION_DOMAIN_NAME_GEO_DICT_KEY);
                        int geoCodeDictListLength = geoCodeDictList.size();
                        boolean isSupportGeoCode = false;
                        for (int index = 0; index < geoCodeDictListLength; index++) {
                            if (geoCodeDictList.get(index).getDictValue().contentEquals(dnsPlatformResolutionDomainNameZone.getGeoCode())) {
                                isSupportGeoCode = true;
                                break;
                            }
                        }
                        if (isSupportGeoCode) {
                            Zone zone = null;
                            try {
                                StringBuilder idnZoneFileContent = new StringBuilder();
                                String originZoneFileContent = dnsPlatformResolutionDomainNameZone.getZoneContent();
                                int zoneFileContentLength = dnsPlatformResolutionDomainNameZone.getZoneContent().length();
                                for (int index = 0; index < zoneFileContentLength; index++) {
                                    idnZoneFileContent.append(IDN.toASCII(String.valueOf(originZoneFileContent.charAt(index))));
                                }
                                dnsPlatformResolutionDomainNameZone.setZoneContent(idnZoneFileContent.toString());
                                InputStream inputStream = new ByteArrayInputStream(dnsPlatformResolutionDomainNameZone.getZoneContent().getBytes());
                                zone = new Zone(new Name(dnsPlatformResolutionDomainName.getDomainName()), inputStream);
                                List<Record> deleteRecordList = new ArrayList<>();
                                Iterator<RRset> rRsetIterator = zone.iterator();
                                while (rRsetIterator.hasNext()) {
                                    RRset rRset = rRsetIterator.next();
                                    deleteRecordList.addAll(rRset.sigs());
                                    if ((rRset.getType() == Type.DNSKEY) || (rRset.getType() == Type.NSEC)) {
                                        deleteRecordList.addAll(rRset.rrs());
                                    }
                                }
                                for (Record record : deleteRecordList) {
                                    zone.removeRecord(record);
                                }
                            } catch (Exception e) {
                                resultMap.put("message", "Zone file format error");
                                resultMap.put("code", 100005);
                            }
                            if (zone != null) {
                                if (dnsPlatformResolutionDomainName.getDnssecEnable()) {
                                    try {
                                        ECPrivateKey kskPrivateKey = (ECPrivateKey) KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(dnsPlatformResolutionDomainName.getDnssecKskPrivateKey())));
                                        ECPublicKey kskPublicKey = (ECPublicKey) KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(dnsPlatformResolutionDomainName.getDnssecKskPublicKey())));
                                        ECPrivateKey zskPrivateKey = (ECPrivateKey) KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(dnsPlatformResolutionDomainName.getDnssecZskPrivateKey())));
                                        ECPublicKey zskPublicKey = (ECPublicKey) KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(dnsPlatformResolutionDomainName.getDnssecZskPublicKey())));
                                        DNSKEYRecord kskRecord = new DNSKEYRecord(Name.fromString(dnsPlatformResolutionDomainName.getDomainName()), DClass.IN, 3600, 0x101, DNSKEYRecord.Protocol.DNSSEC, DNSSEC.Algorithm.ECDSAP256SHA256, kskPublicKey);
                                        DNSKEYRecord zskRecord = new DNSKEYRecord(Name.fromString(dnsPlatformResolutionDomainName.getDomainName()), DClass.IN, 3600, DNSKEYRecord.Flags.ZONE_KEY, DNSKEYRecord.Protocol.DNSSEC, DNSSEC.Algorithm.ECDSAP256SHA256, zskPublicKey);
                                        zone.addRecord(kskRecord);
                                        zone.addRecord(zskRecord);
                                        zone.addRecord(new NSECRecord(new Name(dnsPlatformResolutionDomainName.getDomainName()), DClass.IN, 3600, new Name(dnsPlatformResolutionDomainName.getDomainName()), new int[]{Type.NSEC}));
                                        Calendar calendar = Calendar.getInstance();
                                        calendar.add(Calendar.YEAR, 1);
                                        Instant inception = Instant.now();
                                        Instant expiration = Instant.ofEpochMilli(calendar.getTimeInMillis());
                                        Iterator<RRset> rRsetIterator = zone.iterator();
                                        while (rRsetIterator.hasNext()) {
                                            RRset rRset = rRsetIterator.next();
                                            if (rRset.getType() == Type.DNSKEY) {
                                                zone.addRecord(DNSSEC.sign(rRset, kskRecord, kskPrivateKey, inception, expiration));
                                            } else {
                                                zone.addRecord(DNSSEC.sign(rRset, zskRecord, zskPrivateKey, inception, expiration));
                                            }
                                        }
                                    } catch (Exception e) {
                                        resultMap.put("message", "Dnssec error");
                                        resultMap.put("code", 100006);
                                    }
                                }
                                long nowTime = System.currentTimeMillis();
                                dnsPlatformResolutionDomainNameZone.setZoneId(snowflakeUtils.nextId());
                                dnsPlatformResolutionDomainNameZone.setCreateTime(nowTime);
                                dnsPlatformResolutionDomainNameZone.setUpdateTime(nowTime);
                                dnsPlatformResolutionDomainNameZone.setZoneContent(zone.toString());
                                dnsPlatformResolutionDomainNameZoneMapper.insertDnsPlatformResolutionDomainNameZone(dnsPlatformResolutionDomainNameZone);
                                TransferZone transferZone = new TransferZone();
                                transferZone.setMaster(dnsPlatformResolutionDomainName.getDomainName());
                                transferZone.setOperationType(1);
                                Map<String, Zone> zoneMap = new HashMap<>();
                                List<DnsPlatformResolutionDomainNameZone> dnsPlatformResolutionDomainNameZoneList = dnsPlatformResolutionDomainNameZoneMapper.selectDnsPlatformResolutionDomainNameZoneByDomainId(dnsPlatformResolutionDomainName.getDomainId());
                                dnsPlatformResolutionDomainNameZoneList.forEach(newDnsPlatformResolutionDomainNameZone -> {
                                    InputStream inputStream = new ByteArrayInputStream(newDnsPlatformResolutionDomainNameZone.getZoneContent().getBytes());
                                    try {
                                        Zone newZone = new Zone(new Name(dnsPlatformResolutionDomainName.getDomainName()), inputStream);
                                        newZone.dnssec = dnsPlatformResolutionDomainName.getDnssecEnable();
                                        zoneMap.put(newDnsPlatformResolutionDomainNameZone.getGeoCode(), newZone);
                                    } catch (IOException e) {
                                        throw new RuntimeException(e);
                                    }
                                });
                                transferZone.setZoneMap(zoneMap);
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
                                resultMap.put("message", "Operation succeeded");
                                resultMap.put("code", 0);
                            }
                        } else {
                            resultMap.put("message", "Unsupported geo code");
                            resultMap.put("code", 100007);
                        }
                    } else {
                        resultMap.put("message", "Please operate your own domain name");
                        resultMap.put("code", 100008);
                    }
                } else {
                    resultMap.put("message", "Domain name does not exist");
                    resultMap.put("code", 100009);
                }
            }
        }
        return resultMap;
    }

    @Override
    public Map<String, Object> update(DnsPlatformResolutionDomainNameZone dnsPlatformResolutionDomainNameZone) {
        Map<String, Object> resultMap = new HashMap<>();
        if (dnsPlatformResolutionDomainNameZone.getZoneId() == null) {
            resultMap.put("message", "Please select the zone to operate");
            resultMap.put("code", 100001);
        } else if (StringUtils.isEmpty(dnsPlatformResolutionDomainNameZone.getZoneContent())) {
            resultMap.put("message", "Please enter the content of the zone file");
            resultMap.put("code", 100002);
        } else {
            if (dnsPlatformResolutionDomainNameZone.getZoneContent().length() > DomainNameConstants.SUPPORT_RESOLUTION_DOMAIN_NAME_ZONE_FILE_LENGTH) {
                resultMap.put("message", "The zone file is too large");
                resultMap.put("code", 100003);
            } else {
                DnsPlatformResolutionDomainNameZone dnsPlatformResolutionDomainNameZoneTrust = dnsPlatformResolutionDomainNameZoneMapper.selectDnsPlatformResolutionDomainNameZoneByZoneId(dnsPlatformResolutionDomainNameZone.getZoneId());
                if (dnsPlatformResolutionDomainNameZoneTrust != null) {
                    DnsPlatformResolutionDomainName dnsPlatformResolutionDomainName = dnsPlatformResolutionDomainNameMapper.selectDnsPlatformResolutionDomainNameByDomainId(dnsPlatformResolutionDomainNameZoneTrust.getDomainId());
                    if (dnsPlatformResolutionDomainName != null) {
                        Long userId = authUtils.getLoginUser().getUserId();
                        if (userId.longValue() == dnsPlatformResolutionDomainName.getUserId().longValue()) {
                            Zone zone = null;
                            try {
                                StringBuilder idnZoneFileContent = new StringBuilder();
                                String originZoneFileContent = dnsPlatformResolutionDomainNameZone.getZoneContent();
                                int zoneFileContentLength = dnsPlatformResolutionDomainNameZone.getZoneContent().length();
                                for (int index = 0; index < zoneFileContentLength; index++) {
                                    idnZoneFileContent.append(IDN.toASCII(String.valueOf(originZoneFileContent.charAt(index))));
                                }
                                dnsPlatformResolutionDomainNameZone.setZoneContent(idnZoneFileContent.toString());
                                InputStream inputStream = new ByteArrayInputStream(dnsPlatformResolutionDomainNameZone.getZoneContent().getBytes());
                                zone = new Zone(new Name(dnsPlatformResolutionDomainName.getDomainName()), inputStream);
                                List<Record> deleteRecordList = new ArrayList<>();
                                Iterator<RRset> rRsetIterator = zone.iterator();
                                while (rRsetIterator.hasNext()) {
                                    RRset rRset = rRsetIterator.next();
                                    deleteRecordList.addAll(rRset.sigs());
                                    if ((rRset.getType() == Type.DNSKEY) || (rRset.getType() == Type.NSEC)) {
                                        deleteRecordList.addAll(rRset.rrs());
                                    }
                                }
                                for (Record record : deleteRecordList) {
                                    zone.removeRecord(record);
                                }
                            } catch (Exception e) {
                                resultMap.put("message", "Zone file format error");
                                resultMap.put("code", 100004);
                            }
                            if (zone != null) {
                                if (dnsPlatformResolutionDomainName.getDnssecEnable()) {
                                    try {
                                        ECPrivateKey kskPrivateKey = (ECPrivateKey) KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(dnsPlatformResolutionDomainName.getDnssecKskPrivateKey())));
                                        ECPublicKey kskPublicKey = (ECPublicKey) KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(dnsPlatformResolutionDomainName.getDnssecKskPublicKey())));
                                        ECPrivateKey zskPrivateKey = (ECPrivateKey) KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(dnsPlatformResolutionDomainName.getDnssecZskPrivateKey())));
                                        ECPublicKey zskPublicKey = (ECPublicKey) KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(dnsPlatformResolutionDomainName.getDnssecZskPublicKey())));
                                        DNSKEYRecord kskRecord = new DNSKEYRecord(Name.fromString(dnsPlatformResolutionDomainName.getDomainName()), DClass.IN, 3600, 0x101, DNSKEYRecord.Protocol.DNSSEC, DNSSEC.Algorithm.ECDSAP256SHA256, kskPublicKey);
                                        DNSKEYRecord zskRecord = new DNSKEYRecord(Name.fromString(dnsPlatformResolutionDomainName.getDomainName()), DClass.IN, 3600, DNSKEYRecord.Flags.ZONE_KEY, DNSKEYRecord.Protocol.DNSSEC, DNSSEC.Algorithm.ECDSAP256SHA256, zskPublicKey);
                                        zone.addRecord(kskRecord);
                                        zone.addRecord(zskRecord);
                                        zone.addRecord(new NSECRecord(new Name(dnsPlatformResolutionDomainName.getDomainName()), DClass.IN, 3600, new Name(dnsPlatformResolutionDomainName.getDomainName()), new int[]{Type.NSEC}));
                                        Calendar calendar = Calendar.getInstance();
                                        calendar.add(Calendar.YEAR, 1);
                                        Instant inception = Instant.now();
                                        Instant expiration = Instant.ofEpochMilli(calendar.getTimeInMillis());
                                        Iterator<RRset> rRsetIterator = zone.iterator();
                                        while (rRsetIterator.hasNext()) {
                                            RRset rRset = rRsetIterator.next();
                                            if (rRset.getType() == Type.DNSKEY) {
                                                zone.addRecord(DNSSEC.sign(rRset, kskRecord, kskPrivateKey, inception, expiration));
                                            } else {
                                                zone.addRecord(DNSSEC.sign(rRset, zskRecord, zskPrivateKey, inception, expiration));
                                            }
                                        }
                                    } catch (Exception e) {
                                        resultMap.put("message", "Dnssec error");
                                        resultMap.put("code", 100005);
                                    }
                                }
                                long nowTime = System.currentTimeMillis();
                                dnsPlatformResolutionDomainNameZoneTrust.setUpdateTime(nowTime);
                                dnsPlatformResolutionDomainNameZoneTrust.setZoneContent(zone.toString());
                                dnsPlatformResolutionDomainNameZoneMapper.updateDnsPlatformResolutionDomainNameZone(dnsPlatformResolutionDomainNameZoneTrust);
                                TransferZone transferZone = new TransferZone();
                                transferZone.setMaster(dnsPlatformResolutionDomainName.getDomainName());
                                transferZone.setOperationType(1);
                                Map<String, Zone> zoneMap = new HashMap<>();
                                List<DnsPlatformResolutionDomainNameZone> dnsPlatformResolutionDomainNameZoneList = dnsPlatformResolutionDomainNameZoneMapper.selectDnsPlatformResolutionDomainNameZoneByDomainId(dnsPlatformResolutionDomainName.getDomainId());
                                dnsPlatformResolutionDomainNameZoneList.forEach(newDnsPlatformResolutionDomainNameZone -> {
                                    InputStream inputStream = new ByteArrayInputStream(newDnsPlatformResolutionDomainNameZone.getZoneContent().getBytes());
                                    try {
                                        Zone newZone = new Zone(new Name(dnsPlatformResolutionDomainName.getDomainName()), inputStream);
                                        newZone.dnssec = dnsPlatformResolutionDomainName.getDnssecEnable();
                                        zoneMap.put(newDnsPlatformResolutionDomainNameZone.getGeoCode(), newZone);
                                    } catch (IOException e) {
                                        throw new RuntimeException(e);
                                    }
                                });
                                transferZone.setZoneMap(zoneMap);
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
                                resultMap.put("message", "Operation succeeded");
                                resultMap.put("code", 0);
                            }
                        } else {
                            resultMap.put("message", "Please operate your own domain name");
                            resultMap.put("code", 100006);
                        }
                    } else {
                        resultMap.put("message", "Domain name does not exist");
                        resultMap.put("code", 100007);
                    }
                } else {
                    resultMap.put("message", "Zone does not exist");
                    resultMap.put("code", 100008);
                }
            }
        }
        return resultMap;
    }

    @Override
    public Map<String, Object> delete(DnsPlatformResolutionDomainNameZone dnsPlatformResolutionDomainNameZone) {
        Map<String, Object> resultMap = new HashMap<>();
        if (dnsPlatformResolutionDomainNameZone.getZoneId() == null) {
            resultMap.put("message", "Please select the zone to operate");
            resultMap.put("code", 100001);
        } else {
            DnsPlatformResolutionDomainNameZone dnsPlatformResolutionDomainNameZoneTrust = dnsPlatformResolutionDomainNameZoneMapper.selectDnsPlatformResolutionDomainNameZoneByZoneId(dnsPlatformResolutionDomainNameZone.getZoneId());
            if (dnsPlatformResolutionDomainNameZoneTrust != null) {
                DnsPlatformResolutionDomainName dnsPlatformResolutionDomainName = dnsPlatformResolutionDomainNameMapper.selectDnsPlatformResolutionDomainNameByDomainId(dnsPlatformResolutionDomainNameZoneTrust.getDomainId());
                if (dnsPlatformResolutionDomainName != null) {
                    Long userId = authUtils.getLoginUser().getUserId();
                    if (userId.longValue() == dnsPlatformResolutionDomainName.getUserId().longValue()) {
                        dnsPlatformResolutionDomainNameZoneMapper.deleteDnsPlatformResolutionDomainNameZoneById(dnsPlatformResolutionDomainNameZoneTrust.getId());
                        TransferZone transferZone = new TransferZone();
                        transferZone.setMaster(dnsPlatformResolutionDomainName.getDomainName());
                        transferZone.setOperationType(1);
                        Map<String, Zone> zoneMap = new HashMap<>();
                        List<DnsPlatformResolutionDomainNameZone> dnsPlatformResolutionDomainNameZoneList = dnsPlatformResolutionDomainNameZoneMapper.selectDnsPlatformResolutionDomainNameZoneByDomainId(dnsPlatformResolutionDomainName.getDomainId());
                        dnsPlatformResolutionDomainNameZoneList.forEach(newDnsPlatformResolutionDomainNameZone -> {
                            InputStream inputStream = new ByteArrayInputStream(newDnsPlatformResolutionDomainNameZone.getZoneContent().getBytes());
                            try {
                                Zone newZone = new Zone(new Name(dnsPlatformResolutionDomainName.getDomainName()), inputStream);
                                newZone.dnssec = dnsPlatformResolutionDomainName.getDnssecEnable();
                                zoneMap.put(newDnsPlatformResolutionDomainNameZone.getGeoCode(), newZone);
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        });
                        transferZone.setZoneMap(zoneMap);
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
                        resultMap.put("message", "Operation succeeded");
                        resultMap.put("code", 0);
                    } else {
                        resultMap.put("message", "Please operate your own domain name");
                        resultMap.put("code", 100002);
                    }
                } else {
                    resultMap.put("message", "Domain name does not exist");
                    resultMap.put("code", 100003);
                }
            } else {
                resultMap.put("message", "Zone does not exist");
                resultMap.put("code", 100004);
            }
        }
        return resultMap;
    }

    @Override
    public Map<String, Object> info(DnsPlatformResolutionDomainNameZone dnsPlatformResolutionDomainNameZone) {
        Map<String, Object> resultMap = new HashMap<>();
        if (dnsPlatformResolutionDomainNameZone.getZoneId() == null) {
            resultMap.put("message", "Please select the zone to operate");
            resultMap.put("code", 100001);
        } else {
            DnsPlatformResolutionDomainNameZone dnsPlatformResolutionDomainNameZoneTrust = dnsPlatformResolutionDomainNameZoneMapper.selectDnsPlatformResolutionDomainNameZoneByZoneId(dnsPlatformResolutionDomainNameZone.getZoneId());
            if (dnsPlatformResolutionDomainNameZoneTrust == null) {
                resultMap.put("message", "Zone does not exist");
                resultMap.put("code", 100002);
            } else {
                DnsPlatformResolutionDomainName dnsPlatformResolutionDomainName = dnsPlatformResolutionDomainNameMapper.selectDnsPlatformResolutionDomainNameByDomainId(dnsPlatformResolutionDomainNameZoneTrust.getDomainId());
                if (dnsPlatformResolutionDomainName == null) {
                    resultMap.put("message", "Domain name does not exist");
                    resultMap.put("code", 100003);
                } else {
                    Long userId = authUtils.getLoginUser().getUserId();
                    if (userId.longValue() == dnsPlatformResolutionDomainName.getUserId().longValue()) {
                        if (dnsPlatformResolutionDomainName.getDnssecEnable()) {
                            InputStream inputStream = new ByteArrayInputStream(dnsPlatformResolutionDomainNameZoneTrust.getZoneContent().getBytes());
                            try {
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
                                String originZoneContent = zone.toString();
                                int originZoneContentLength = originZoneContent.length();
                                StringBuilder idnZoneContent = new StringBuilder();
                                for (int index = 0; index < originZoneContentLength; index++) {
                                    idnZoneContent.append(IDN.toUnicode(String.valueOf(originZoneContent.charAt(index))));
                                }
                                resultMap.put("message", "Operation succeeded");
                                resultMap.put("code", 0);
                                resultMap.put("zone", idnZoneContent.toString());
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        } else {
                            String originZoneContent = dnsPlatformResolutionDomainNameZoneTrust.getZoneContent();
                            int originZoneContentLength = originZoneContent.length();
                            StringBuilder idnZoneContent = new StringBuilder();
                            for (int index = 0; index < originZoneContentLength; index++) {
                                idnZoneContent.append(IDN.toUnicode(String.valueOf(originZoneContent.charAt(index))));
                            }
                            resultMap.put("message", "Operation succeeded");
                            resultMap.put("code", 0);
                            resultMap.put("zone", idnZoneContent.toString());
                        }
                    } else {
                        resultMap.put("message", "Please operate your own domain name");
                        resultMap.put("code", 100004);
                    }
                }
            }
        }
        return resultMap;
    }

    @Override
    public List<DnsPlatformResolutionDomainNameZoneSimpleGeo> geo() {
        List<SysDictData> geoDictList = DictUtils.getDictCache(DomainNameConstants.SUPPORT_RESOLUTION_DOMAIN_NAME_GEO_DICT_KEY);
        List<DnsPlatformResolutionDomainNameZoneSimpleGeo> simpleGeoList = new ArrayList<>();
        for (SysDictData geoData : geoDictList) {
            DnsPlatformResolutionDomainNameZoneSimpleGeo dnsPlatformResolutionDomainNameZoneSimpleGeo = new DnsPlatformResolutionDomainNameZoneSimpleGeo();
            dnsPlatformResolutionDomainNameZoneSimpleGeo.setLabel(geoData.getDictLabel());
            dnsPlatformResolutionDomainNameZoneSimpleGeo.setValue(geoData.getDictValue());
            simpleGeoList.add(dnsPlatformResolutionDomainNameZoneSimpleGeo);
        }
        return simpleGeoList;
    }
}
