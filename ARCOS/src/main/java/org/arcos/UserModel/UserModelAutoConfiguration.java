package org.arcos.UserModel;

import org.arcos.UserModel.GdeltThemeIndex.GdeltThemeIndexProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "arcos.user-model.enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties({UserModelProperties.class, GdeltThemeIndexProperties.class})
@ComponentScan(basePackages = "org.arcos.UserModel")
public class UserModelAutoConfiguration {
}
