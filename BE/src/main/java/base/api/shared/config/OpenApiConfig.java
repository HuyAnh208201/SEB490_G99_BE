package base.api.shared.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        final String securitySchemeName = "bearerAuth";

        return new OpenAPI()
                .info(new Info()
                        .title("ChainStore API")
                        .version("1.0")
                        .description("API đặt sân bóng. Mỗi endpoint có mô tả tác dụng và cách dùng. **Public** = không cần JWT. **Cần đăng nhập** = cần gửi header Authorization: Bearer <token>."))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:4313")
                                .description("Local"),
                        new Server()
                                .url("https://api.chainstore.site")
                                .description("Production")
                ))
                .components(new Components()
                        .addSecuritySchemes(securitySchemeName,
                                new SecurityScheme()
                                        .name(securitySchemeName)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("Nhập JWT token (không cần thêm 'Bearer ')")
                        ))
                .addSecurityItem(new SecurityRequirement().addList(securitySchemeName));
    }
}
