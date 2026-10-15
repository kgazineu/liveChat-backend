package com.example.liveChat.infra;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import org.springframework.boot.autoconfigure.data.redis.LettuceClientConfigurationBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.time.Duration;

/** Configura a conexão com o Redis e a assinatura das notificações de expiração de chaves. */
@Configuration
@Profile("!test")
public class RedisConfig {

    /**
     * Redis gerenciado e redes intermediárias costumam descartar conexões ociosas sem avisar. Com keepalive TCP, o
     * Lettuce percebe a conexão morta e reconecta antes do próximo comando, em vez de falhar a primeira requisição
     * depois de um período sem uso (por exemplo, o primeiro login do dia).
     */
    @Bean
    LettuceClientConfigurationBuilderCustomizer redisConnectionResilience() {
        return builder -> builder.clientOptions(ClientOptions.builder()
                .autoReconnect(true)
                .socketOptions(SocketOptions.builder()
                        .connectTimeout(Duration.ofSeconds(5))
                        .keepAlive(SocketOptions.KeepAliveOptions.builder()
                                .enable()
                                .idle(Duration.ofSeconds(30))
                                .interval(Duration.ofSeconds(10))
                                .count(3)
                                .build())
                        .build())
                .build());
    }

    @Bean
    RedisMessageListenerContainer redisMessageListenerContainer(RedisConnectionFactory connectionFactory) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        return container;
    }
}
