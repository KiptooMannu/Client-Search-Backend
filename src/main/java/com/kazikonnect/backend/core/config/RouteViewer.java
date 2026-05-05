package com.kazikonnect.backend.core.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import java.util.Map;

@Component
@Slf4j
public class RouteViewer {

    @EventListener
    public void handleContextRefresh(ContextRefreshedEvent event) {
        ApplicationContext applicationContext = event.getApplicationContext();
        RequestMappingHandlerMapping mapping = applicationContext.getBean("requestMappingHandlerMapping", RequestMappingHandlerMapping.class);
        Map<RequestMappingInfo, HandlerMethod> methods = mapping.getHandlerMethods();

        log.info("");
        log.info("==========================================================================================");
        log.info("                                 KAZI KONNECT - ACTIVE API ROUTES                         ");
        log.info("==========================================================================================");
        log.info(String.format("%-10s | %-45s | %-20s", "METHOD", "PATH", "HANDLER"));
        log.info("-----------|-----------------------------------------------|------------------------------");

        methods.forEach((info, method) -> {
            String urlPattern = info.getDirectPaths().toString();
            String httpMethod = info.getMethodsCondition().getMethods().toString();
            String handlerName = method.getMethod().getName();
            
            log.info(String.format("%-10s | %-45s | %-20s", 
                httpMethod.replace("[", "").replace("]", ""), 
                urlPattern.replace("[", "").replace("]", ""), 
                handlerName));
        });
        log.info("==========================================================================================");
        log.info("");
    }
}
