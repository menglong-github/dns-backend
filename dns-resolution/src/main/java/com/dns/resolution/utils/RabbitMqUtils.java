package com.dns.resolution.utils;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

@Component
public class RabbitMqUtils {
    Connection connection;

    public RabbitMqUtils(@Value("${mq.rabbit.host}") String host, @Value("${mq.rabbit.port}") int port, @Value("${mq.rabbit.user}") String user, @Value("${mq.rabbit.password}") String password) {
        ConnectionFactory connectionFactory = new ConnectionFactory();
        connectionFactory.setHost(host);
        connectionFactory.setPort(port);
        connectionFactory.setVirtualHost("/");
        connectionFactory.setUsername(user);
        connectionFactory.setPassword(password);
        try {
            connection = connectionFactory.newConnection();
        } catch (IOException | TimeoutException e) {
            throw new RuntimeException(e);
        }
    }

    public Channel getChannel() throws IOException {
        return connection.createChannel();
    }

}
