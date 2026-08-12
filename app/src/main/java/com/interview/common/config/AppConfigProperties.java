package com.interview.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import lombok.Data;
import java.util.List;

/**
 * 应用配置属性
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.resume")
public class AppConfigProperties {

    private List<String> allowedTypes;
    
}
