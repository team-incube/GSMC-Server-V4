package team.incube.gsmc.global.config

import org.hibernate.boot.model.FunctionContributions
import org.hibernate.boot.model.FunctionContributor
import org.hibernate.type.StandardBasicTypes

/** MySQL FULLTEXT(ngram) 검색을 위한 `match_against` JPQL 함수를 등록합니다. */
class MatchAgainstFunctionContributor : FunctionContributor {
    override fun contributeFunctions(functionContributions: FunctionContributions) {
        val booleanType = functionContributions.typeConfiguration.basicTypeRegistry.resolve(StandardBasicTypes.BOOLEAN)
        functionContributions.functionRegistry.registerPattern(
            "match_against",
            "match(?1) against (?2 in boolean mode)",
            booleanType,
        )
    }
}
