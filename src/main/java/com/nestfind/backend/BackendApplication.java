package com.nestfind.backend;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

@SpringBootApplication
public class BackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(BackendApplication.class, args);
	}

	@Bean
	public CommandLineRunner listEndpoints(RequestMappingHandlerMapping handlerMapping) {
		return args -> {
			System.out.println("\n" + "=".repeat(80));
			System.out.println("🚀  NESTFIND API REGISTRY - ACTIVE ENDPOINTS");
			System.out.println("=".repeat(80));
			
			handlerMapping.getHandlerMethods().forEach((info, method) -> {
				String params = info.getMethodsCondition().getMethods().toString();
				String patterns = "N/A";
				
				var patternsCondition = info.getPatternsCondition();
				var pathPatternsCondition = info.getPathPatternsCondition();

				if (patternsCondition != null) {
					patterns = patternsCondition.getPatterns().toString();
				} else if (pathPatternsCondition != null) {
					patterns = pathPatternsCondition.getPatterns().toString();
				}
				
				// Clean up formatting
				String methodDisplay = String.format("%-10s", params.replaceAll("[\\[\\]]", ""));
				String pathDisplay = patterns.replaceAll("[\\[\\]]", "");
				
				System.out.printf("  %s | %-45s | %s#%s\n", 
					methodDisplay, 
					pathDisplay,
					method.getBeanType().getSimpleName(),
					method.getMethod().getName());
			});
			
			System.out.println("=".repeat(80) + "\n");
		};
	}

}
