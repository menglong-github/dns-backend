package com.dns.resolution.init;

import com.dns.resolution.domain.dto.DnsPlatformResolutionDomainName;
import com.dns.resolution.domain.dto.DnsPlatformResolutionDomainNameZone;
import com.dns.resolution.mapper.DnsPlatformResolutionDomainNameMapper;
import com.dns.resolution.mapper.DnsPlatformResolutionDomainNameZoneMapper;
import com.dns.resolution.utils.RabbitMqUtils;
import com.rabbitmq.client.*;
import dns.core.Name;
import dns.core.TransferZone;
import dns.core.Zone;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class Init {

    @Value("${mq.rabbit.init}")
    private String initQueueName;

    @Autowired
    private DnsPlatformResolutionDomainNameMapper dnsPlatformResolutionDomainNameMapper;

    @Autowired
    private DnsPlatformResolutionDomainNameZoneMapper dnsPlatformResolutionDomainNameZoneMapper;

    @Autowired
    private RabbitMqUtils rabbitMqUtils;

    @PostConstruct
    public void init() {

        try {
            Channel channel = rabbitMqUtils.getChannel();
            channel.queueDeclare(initQueueName, true, false, false, null);
            channel.basicConsume(initQueueName, true, new Consumer() {
                @Override
                public void handleConsumeOk(String consumerTag) {

                }

                @Override
                public void handleCancelOk(String consumerTag) {

                }

                @Override
                public void handleCancel(String consumerTag) {

                }

                @Override
                public void handleShutdownSignal(String consumerTag, ShutdownSignalException sig) {

                }

                @Override
                public void handleRecoverOk(String consumerTag) {

                }

                @Override
                public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
                    String initQueue = new String(body);
                    List<DnsPlatformResolutionDomainName> dnsPlatformResolutionDomainNameList = dnsPlatformResolutionDomainNameMapper.selectDnsPlatformResolutionDomainNameAllList();
                    dnsPlatformResolutionDomainNameList.forEach(dnsPlatformResolutionDomainName -> {
                        List<DnsPlatformResolutionDomainNameZone> dnsPlatformResolutionDomainNameZoneList = dnsPlatformResolutionDomainNameZoneMapper.selectDnsPlatformResolutionDomainNameZoneByDomainId(dnsPlatformResolutionDomainName.getDomainId());
                        TransferZone transferZone = new TransferZone();
                        transferZone.setMaster(dnsPlatformResolutionDomainName.getDomainName());
                        Map<String, Zone> zoneMap = new HashMap<>();
                        dnsPlatformResolutionDomainNameZoneList.forEach(dnsPlatformResolutionDomainNameZone -> {
                            InputStream inputStream = new ByteArrayInputStream(dnsPlatformResolutionDomainNameZone.getZoneContent().getBytes());
                            try {
                                Zone zone = new Zone(new Name(dnsPlatformResolutionDomainName.getDomainName()), inputStream);
                                zone.dnssec = dnsPlatformResolutionDomainName.getDnssecEnable();
                                zoneMap.put(dnsPlatformResolutionDomainNameZone.getGeoCode(), zone);
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        });
                        transferZone.setZoneMap(zoneMap);
                        transferZone.setOperationType(1);
                        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                        ObjectOutputStream objectOutputStream = null;
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
                            channel.basicPublish("", initQueue, null, byteArrayOutputStream.toByteArray());
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
                }
            });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
