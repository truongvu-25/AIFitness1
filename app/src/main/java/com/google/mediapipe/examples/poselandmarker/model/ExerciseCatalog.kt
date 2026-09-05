package com.google.mediapipe.examples.poselandmarker.model

import android.content.Context
import androidx.annotation.StringRes
import com.google.mediapipe.examples.poselandmarker.R

enum class ExerciseCategory {
    UPPER_CORE,
    LOWER_CARDIO
}

data class CatalogExercise(
    val id: String,
    val name: String,
    val description: String,
    val libraryDescription: String,
    val summary: String,
    val videoUrl: String,
    val isTimed: Boolean,
    val unit: String,
    val defaultTarget: Int,
    val category: ExerciseCategory,
    val categoryLabel: String,
    val equipment: String,
    val targetText: String,
    val targetLabel: String
) {
    fun toExerciseDetails() = ExerciseDetails(
        id = id,
        name = name,
        description = description,
        videoUrl = videoUrl,
        isTimed = isTimed,
        unit = unit
    )
}

/**
 * Single source of truth for the seven supported exercises.
 *
 * Canonical IDs, bundled videos, timed/repetition behavior and default targets are intentionally
 * kept identical to the existing product. Only user-facing text is resolved from the active locale.
 */
object ExerciseCatalog {
    private data class Definition(
        val id: String,
        @StringRes val nameRes: Int,
        @StringRes val descriptionRes: Int,
        @StringRes val libraryDescriptionRes: Int,
        @StringRes val summaryRes: Int,
        val videoUrl: String,
        val isTimed: Boolean,
        val defaultTarget: Int,
        val category: ExerciseCategory,
        val eachSide: Boolean = false
    )

    private val definitions = listOf(
        Definition(
            "pushup",
            R.string.exercise_name_pushup,
            R.string.exercise_desc_pushup,
            R.string.exercise_library_desc_pushup,
            R.string.exercise_summary_pushup,
            "asset:///videos/push_up.mp4",
            false,
            15,
            ExerciseCategory.UPPER_CORE
        ),
        Definition(
            "situp",
            R.string.exercise_name_situp,
            R.string.exercise_desc_situp,
            R.string.exercise_library_desc_situp,
            R.string.exercise_summary_situp,
            "asset:///videos/sit_up.mp4",
            false,
            20,
            ExerciseCategory.UPPER_CORE
        ),
        Definition(
            "squat",
            R.string.exercise_name_squat,
            R.string.exercise_desc_squat,
            R.string.exercise_library_desc_squat,
            R.string.exercise_summary_squat,
            "asset:///videos/squat.mp4",
            false,
            20,
            ExerciseCategory.LOWER_CARDIO
        ),
        Definition(
            "plank",
            R.string.exercise_name_plank,
            R.string.exercise_desc_plank,
            R.string.exercise_library_desc_plank,
            R.string.exercise_summary_plank,
            "asset:///videos/plank.mp4",
            true,
            45,
            ExerciseCategory.UPPER_CORE
        ),
        Definition(
            "sideplank",
            R.string.exercise_name_side_plank,
            R.string.exercise_desc_side_plank,
            R.string.exercise_library_desc_side_plank,
            R.string.exercise_summary_side_plank,
            "asset:///videos/side_plank.mp4",
            true,
            30,
            ExerciseCategory.UPPER_CORE
        ),
        Definition(
            "jumpingjack",
            R.string.exercise_name_jumping_jack,
            R.string.exercise_desc_jumping_jack,
            R.string.exercise_library_desc_jumping_jack,
            R.string.exercise_summary_jumping_jack,
            "asset:///videos/jumping_jack.mp4",
            false,
            30,
            ExerciseCategory.LOWER_CARDIO
        ),
        Definition(
            "splitsquat",
            R.string.exercise_name_split_squat,
            R.string.exercise_desc_split_squat,
            R.string.exercise_library_desc_split_squat,
            R.string.exercise_summary_split_squat,
            "asset:///videos/split_squat.mp4",
            false,
            15,
            ExerciseCategory.LOWER_CARDIO,
            eachSide = true
        )
    )

    val supportedIds: List<String>
        get() = definitions.map { it.id }

    fun canonicalId(rawId: String): String = when (rawId.trim().lowercase()) {
        "push_up" -> "pushup"
        "sit_up" -> "situp"
        "jumping_jack", "jumping_jacks" -> "jumpingjack"
        "side_plank" -> "sideplank"
        "split_squat", "lunges" -> "splitsquat"
        else -> rawId.trim().lowercase()
    }

    fun all(context: Context): List<CatalogExercise> = definitions.map { definition ->
        val unitRes = when {
            definition.eachSide -> R.string.unit_reps_each_side
            definition.isTimed -> R.string.unit_seconds
            else -> R.string.unit_reps
        }
        val unit = context.getString(unitRes)
        val targetText = context.getString(
            if (definition.isTimed) R.string.exercise_secs_format else R.string.exercise_reps_format,
            definition.defaultTarget
        ).let { value ->
            if (definition.eachSide) {
                context.getString(R.string.exercise_reps_format, definition.defaultTarget)
                    .substringBeforeLast(' ') + " " + unit
            } else {
                value
            }
        }
        val categoryLabel = context.getString(
            if (definition.category == ExerciseCategory.UPPER_CORE) {
                R.string.exercise_category_upper_core
            } else {
                R.string.exercise_category_lower_cardio
            }
        )

        CatalogExercise(
            id = definition.id,
            name = context.getString(definition.nameRes),
            description = context.getString(definition.descriptionRes),
            libraryDescription = context.getString(definition.libraryDescriptionRes),
            summary = context.getString(definition.summaryRes),
            videoUrl = definition.videoUrl,
            isTimed = definition.isTimed,
            unit = if (definition.eachSide) context.getString(R.string.unit_reps) else unit,
            defaultTarget = definition.defaultTarget,
            category = definition.category,
            categoryLabel = categoryLabel,
            equipment = context.getString(R.string.bodyweight),
            targetText = targetText,
            targetLabel = context.getString(R.string.exercise_target_format, targetText)
        )
    }

    fun find(context: Context, rawId: String): CatalogExercise? {
        val canonicalId = canonicalId(rawId)
        return all(context).firstOrNull { it.id == canonicalId }
    }

    fun details(context: Context, rawId: String, fallback: ExerciseDetails? = null): ExerciseDetails? {
        val local = find(context, rawId) ?: return fallback
        return local.toExerciseDetails()
    }

    /** Maps existing analyzer output to locale resources without touching analyzer state machines. */
    fun localizeFeedback(context: Context, feedback: String): String {
        val resource = when (feedback) {
            "Bài tập chưa hỗ trợ đếm" -> R.string.feedback_unsupported
            "Hãy đứng lùi lại để camera quét được toàn thân" -> R.string.feedback_full_body
            "Hãy quay ngang người để đếm hít đất chính xác hơn" -> R.string.feedback_pushup_side
            "Hãy bắt đầu ở tư thế chống tay thẳng" -> R.string.feedback_pushup_ready
            "Tốt! Bây giờ hãy đẩy lên." -> R.string.feedback_pushup_down_ok
            "Đã xong 1 lần! Tiếp tục nào." -> R.string.feedback_rep_done
            "Hạ thấp người xuống nữa." -> R.string.feedback_pushup_lower
            "Hãy quay ngang người để đếm Squat chính xác hơn" -> R.string.feedback_squat_side
            "Hãy đứng thẳng để bắt đầu" -> R.string.feedback_stand_ready
            "Đã xuống đủ sâu! Đứng dậy nào." -> R.string.feedback_squat_depth_ok
            "Tuyệt vời! Tiếp tục squat." -> R.string.feedback_squat_done
            "Hạ thấp mông xuống chút nữa." -> R.string.feedback_squat_lower
            "Hãy đứng hướng về phía camera để tập Jumping Jack" -> R.string.feedback_jumping_jack_front
            "Hãy đứng thẳng, khép tay và khép chân để bắt đầu" -> R.string.feedback_jumping_jack_ready
            "Tốt! Khép tay và chân lại." -> R.string.feedback_jumping_jack_close
            "Tuyệt vời! Tiếp tục nào." -> R.string.feedback_keep_going
            "Giơ tay qua đầu và bật mở hai chân." -> R.string.feedback_jumping_jack_open
            "Khép tay và chân về vị trí ban đầu." -> R.string.feedback_jumping_jack_reset
            "Hãy nằm lùi lại để camera quét được toàn thân" -> R.string.feedback_lie_full_body
            "Hãy nằm ngang so với camera để đếm gập bụng" -> R.string.feedback_situp_side
            "Hãy nằm phẳng để bắt đầu" -> R.string.feedback_lie_ready
            "Tốt! Nằm xuống từ từ." -> R.string.feedback_situp_lower
            "Gập bụng mạnh lên!" -> R.string.feedback_situp_up
            "Kéo người ngồi dậy cao hơn." -> R.string.feedback_situp_higher
            "Hãy nằm ngang so với camera để tập Plank" -> R.string.feedback_plank_side
            "Hãy giữ thẳng người để bắt đầu tính giờ" -> R.string.feedback_plank_ready
            "Đang giữ chuẩn tư thế!" -> R.string.feedback_plank_good
            "Hãy giữ thẳng thân và hai chân!" -> R.string.feedback_plank_straight
            "Hãy nằm ngang so với camera để tập Side Plank" -> R.string.feedback_side_plank_side
            "Hãy giữ người thẳng để bắt đầu" -> R.string.feedback_side_plank_ready
            "Tuyệt vời, giữ vững nhé!" -> R.string.feedback_hold_good
            "Đẩy hông cao lên một chút!" -> R.string.feedback_hips_up
            "Hãy quay ngang người để tập Split Squat" -> R.string.feedback_split_squat_side
            "Tốt! Đẩy người lên." -> R.string.feedback_split_squat_up
            "Giữ thăng bằng tốt!" -> R.string.feedback_balance_good
            "Hạ gối chân sau sâu xuống." -> R.string.feedback_split_squat_lower
            else -> return feedback
        }
        return context.getString(resource)
    }
}
