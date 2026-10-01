package team.incube.gsmc.domain.score.adapter.web

import team.incube.gsmc.domain.score.academic.AcademicGradeEntryCommand

data class SaveAcademicGradeSheetInput(
    val entries: List<AcademicGradeEntryInput>,
) {
    fun toCommands(): List<AcademicGradeEntryCommand> =
        entries.map { AcademicGradeEntryCommand(it.semester, it.subjectName, it.value) }
}

data class AcademicGradeEntryInput(
    val semester: Int,
    val subjectName: String,
    val value: String,
)
