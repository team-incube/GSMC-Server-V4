package team.incube.gsmc.domain.project.service

import org.springframework.stereotype.Service
import team.incube.gsmc.domain.project.port.`in`.ModifyDataGsmProjectCacheUseCase
import team.incube.gsmc.domain.project.port.out.DataGsmProjectCachePort

@Service
class ModifyDataGsmProjectCacheService(
    private val cachePort: DataGsmProjectCachePort,
) : ModifyDataGsmProjectCacheUseCase {
    override fun execute() = cachePort.invalidateAll()
}
