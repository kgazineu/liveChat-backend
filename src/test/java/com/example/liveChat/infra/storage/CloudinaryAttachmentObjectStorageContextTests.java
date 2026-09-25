package com.example.liveChat.infra.storage;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

class CloudinaryAttachmentObjectStorageContextTests {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withPropertyValues(
                    "livechat.attachments.cloudinary.cloud-name=livechat-test",
                    "livechat.attachments.cloudinary.api-key=test-api-key",
                    "livechat.attachments.cloudinary.api-secret=test-api-secret",
                    "livechat.attachments.cloudinary.upload-preset=test-upload-preset",
                    "livechat.attachments.cloudinary.download-url-ttl=5m")
            .withUserConfiguration(CloudinaryStorageConfiguration.class);

    @Test
    void createsTheProductionStorageBeanThroughConstructorInjection() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(CloudinaryAttachmentObjectStorage.class);
            assertThat(context).hasSingleBean(AttachmentObjectStorage.class);
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(CloudinaryAttachmentStorageProperties.class)
    @Import(CloudinaryAttachmentObjectStorage.class)
    static class CloudinaryStorageConfiguration {
    }
}
