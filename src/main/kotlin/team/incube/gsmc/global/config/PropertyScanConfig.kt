package team.incube.gsmc.global.config

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import team.incube.gsmc.domain.alert.adapter.sse.AlertSseProperties
import team.incube.gsmc.domain.auth.adapter.out.oauth.OAuthProperties
import team.incube.gsmc.domain.file.adapter.out.s3.S3Properties
import team.incube.gsmc.domain.project.adapter.out.openapi.DataGsmOpenApiProperties
import team.incube.gsmc.global.security.jwt.JwtProperties

@Configuration
@EnableConfigurationProperties(
    JwtProperties::class,
    OAuthProperties::class,
    DataGsmOpenApiProperties::class,
    S3Properties::class,
    AlertSseProperties::class,
)
class PropertyScanConfig
