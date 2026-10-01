package team.incube.gsmc.domain.score.service

import org.springframework.transaction.annotation.Transactional
import team.incube.gsmc.domain.score.academic.AcademicCurriculum
import team.incube.gsmc.domain.score.academic.AcademicGradeEntry
import team.incube.gsmc.domain.score.academic.AcademicGradeEntryCommand
import team.incube.gsmc.domain.score.academic.AcademicGradeSheet
import team.incube.gsmc.domain.score.academic.AcademicGradeValue
import team.incube.gsmc.domain.score.port.`in`.ModifyMyAcademicGradeSheetUseCase
import team.incube.gsmc.domain.score.port.out.AcademicGradeEntryPersistencePort
import team.incube.gsmc.domain.score.port.out.ScorePersistencePort
import team.incube.gsmc.global.annotation.PortDirection
import team.incube.gsmc.global.annotation.port.Port
import team.incube.gsmc.global.exception.ErrorCode
import team.incube.gsmc.global.exception.GsmcException
import team.incube.gsmc.global.util.MemberUtil

/**
 * 본인 교과성적 입력표 저장 유스케이스 구현 클래스입니다.
 * [ModifyMyAcademicGradeSheetUseCase]를 구현하며, 현재 학년 입력값을 통째로 교체한다.
 *
 * 과목은 학생의 학년·학기·학과 목록에 있어야 하고, 택1 선택 그룹은 학기마다 1개까지만 입력할 수 있다.
 * 이번 학년도에 승인된 교과성적이 있으면 잠겨 있어 저장할 수 없다([AcademicGradeSheetSupport.ensureEditable]).
 * 심사 중인 교과성적이 있으면 그 점수를 새 평균으로 다시 계산하므로, 이때는 입력표가 완성된 상태여야 한다.
 */
@Port(direction = PortDirection.INBOUND)
class ModifyMyAcademicGradeSheetService(
    private val academicGradeSheetSupport: AcademicGradeSheetSupport,
    private val academicGradeEntryPersistencePort: AcademicGradeEntryPersistencePort,
    private val scorePersistencePort: ScorePersistencePort,
    private val scoreTotalCacheInvalidator: ScoreTotalCacheInvalidator,
    private val memberUtil: MemberUtil,
) : ModifyMyAcademicGradeSheetUseCase {
    @Transactional
    override fun execute(entries: List<AcademicGradeEntryCommand>): AcademicGradeSheet {
        val userId = memberUtil.getCurrentUserId()
        val student = academicGradeSheetSupport.loadStudent(userId)
        academicGradeSheetSupport.ensureEditable(userId)

        val newEntries = toEntries(userId, student, entries)
        academicGradeEntryPersistencePort.replaceAll(userId, student.grade, newEntries)

        val sheet = AcademicGradeSheet.of(student.grade, student.department, newEntries)
        academicGradeSheetSupport.findPending(userId)?.let { pending ->
            val scoreValue = academicGradeSheetSupport.toScoreValue(sheet, pending.category)
            if (pending.scoreValue != scoreValue) {
                scorePersistencePort.save(pending.copy(scoreValue = scoreValue))
                scoreTotalCacheInvalidator.invalidate(userId)
            }
        }
        return sheet
    }

    private fun toEntries(
        userId: Long,
        student: AcademicGradeSheetSupport.Student,
        commands: List<AcademicGradeEntryCommand>,
    ): List<AcademicGradeEntry> {
        if (commands.distinctBy { it.semester to it.subjectName }.size != commands.size) {
            throw GsmcException(ErrorCode.INVALID_ACADEMIC_SUBJECT)
        }
        val entries =
            commands.map { command ->
                if (command.semester !in AcademicCurriculum.SEMESTERS) {
                    throw GsmcException(ErrorCode.INVALID_ACADEMIC_SUBJECT)
                }
                val subject =
                    AcademicCurriculum
                        .subjectsOf(student.grade, command.semester, student.department)
                        .find { it.name == command.subjectName }
                        ?: throw GsmcException(ErrorCode.INVALID_ACADEMIC_SUBJECT)
                subject.electiveGroup to
                    AcademicGradeEntry(
                        userId = userId,
                        grade = student.grade,
                        semester = command.semester,
                        subjectName = subject.name,
                        subjectGrade = AcademicGradeValue.parse(student.grade, command.value),
                    )
            }
        val duplicatedElective =
            entries
                .filter { (group, _) -> group != null }
                .groupBy { (group, entry) -> entry.semester to group }
                .any { (_, inGroup) -> inGroup.size > 1 }
        if (duplicatedElective) throw GsmcException(ErrorCode.INVALID_ACADEMIC_SUBJECT)
        return entries.map { it.second }
    }
}
