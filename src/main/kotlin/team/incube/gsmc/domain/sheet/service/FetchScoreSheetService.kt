package team.incube.gsmc.domain.sheet.service

import team.incube.gsmc.domain.score.ScoreAggregator
import team.incube.gsmc.domain.sheet.ScoreSheetRow
import team.incube.gsmc.domain.sheet.SheetStudent
import team.incube.gsmc.domain.sheet.port.`in`.FetchClassScoreSheetUseCase
import team.incube.gsmc.domain.sheet.port.`in`.FetchGradeScoreSheetUseCase
import team.incube.gsmc.domain.sheet.port.out.SheetGeneratorPort
import team.incube.gsmc.domain.sheet.port.out.SheetMemberPersistencePort
import team.incube.gsmc.domain.sheet.port.out.SheetScorePersistencePort
import team.incube.gsmc.domain.sheet.port.out.SheetStoragePort
import team.incube.gsmc.domain.user.UserRole
import team.incube.gsmc.domain.user.isTeacherOrAbove
import team.incube.gsmc.global.annotation.PortDirection
import team.incube.gsmc.global.annotation.port.Port
import team.incube.gsmc.global.exception.ErrorCode
import team.incube.gsmc.global.exception.GsmcException
import team.incube.gsmc.global.util.MemberUtil
import java.util.UUID

private const val XLSX_CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

@Port(direction = PortDirection.INBOUND)
class FetchScoreSheetService(
    private val sheetMemberPersistencePort: SheetMemberPersistencePort,
    private val sheetScorePersistencePort: SheetScorePersistencePort,
    private val sheetGeneratorPort: SheetGeneratorPort,
    private val sheetStoragePort: SheetStoragePort,
    private val memberUtil: MemberUtil,
) : FetchClassScoreSheetUseCase,
    FetchGradeScoreSheetUseCase {
    override fun execute(
        grade: Int,
        classNumber: Int,
    ): String {
        requireTeacherOrAbove()
        validateGrade(grade)
        validateClassNumber(classNumber)

        return generateSheet(
            students = sheetMemberPersistencePort.findAllStudentsByGradeAndClass(grade, classNumber),
            sheetName = "${grade}학년 ${classNumber}반",
            keyPrefix = "sheets/class/$grade/$classNumber",
        )
    }

    override fun execute(grade: Int): String {
        requireTeacherOrAbove()
        validateGrade(grade)

        return generateSheet(
            students = sheetMemberPersistencePort.findAllStudentsByGrade(grade),
            sheetName = "${grade}학년",
            keyPrefix = "sheets/grade/$grade",
        )
    }

    private fun generateSheet(
        students: List<SheetStudent>,
        sheetName: String,
        keyPrefix: String,
    ): String {
        val eligibleStudents =
            students
                .filter { it.role == UserRole.STUDENT }
                .sortedWith(compareBy(SheetStudent::grade, SheetStudent::classNumber, SheetStudent::number))
        val totalScoreByUserId =
            sheetScorePersistencePort.findApprovedScoresByUserIds(eligibleStudents.map { it.userId })
        val rows =
            eligibleStudents.map { student ->
                ScoreSheetRow(
                    grade = student.grade,
                    classNumber = student.classNumber,
                    number = student.number,
                    name = student.name,
                    totalScore =
                        ScoreAggregator.totalScoreOf(
                            totalScoreByUserId[student.userId].orEmpty(),
                            includeApprovedOnly = true,
                            userGrade = student.grade,
                        ),
                )
            }
        val content = sheetGeneratorPort.generate(rows, sheetName)
        val key = "$keyPrefix/${UUID.randomUUID()}.xlsx"

        sheetStoragePort.upload(key, content, XLSX_CONTENT_TYPE)
        return sheetStoragePort.createPresignedDownloadUrl(key)
    }

    private fun requireTeacherOrAbove() {
        if (!memberUtil.getCurrentUserRole().isTeacherOrAbove()) {
            throw GsmcException(ErrorCode.FORBIDDEN)
        }
    }

    private fun validateGrade(grade: Int) {
        if (grade !in 1..3) {
            throw GsmcException(ErrorCode.INVALID_GRADE)
        }
    }

    private fun validateClassNumber(classNumber: Int) {
        if (classNumber !in 1..4) {
            throw GsmcException(ErrorCode.INVALID_CLASS_NUMBER)
        }
    }
}
