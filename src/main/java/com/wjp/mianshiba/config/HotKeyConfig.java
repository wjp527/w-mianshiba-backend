package com.wjp.mianshiba.config;

import com.jd.platform.hotkey.client.ClientStarter;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * HotKey 热key 发现配置
 *
 * @author <a href="https://github.com/liyupi">程序员鱼皮</a>
 * @from <a href="https://wjp.icu">编程导航知识星球</a>
 */
@Configuration
@ConfigurationProperties(prefix = "hotkey")
@Data
public class HotKeyConfig {

    /**
     * Etcd 服务器完整路径
     */
    private String etcdServer = "http://127.0.0.1:2379";

    /**
     * 应用名称
     */
    private String appName = "app";

    /**
     * 本地缓存最大数量
     */
    private int caffeineSize = 10000;

    /**
     * 批量推送 key 的间隔时间
     */
    private long pushPeriod = 1000L;


    /**
     * 初始化 hotkey
     */
    @Bean
    public void initHotKey() {
        ClientStarter.Builder builder = new ClientStarter.Builder();
        ClientStarter starter = builder.setAppName(appName)
                .setCaffeineSize(caffeineSize)
                .setPushPeriod(pushPeriod)
                .setEtcdServer(etcdServer)
                .build();
        starter.startPipeline();
    }
}
