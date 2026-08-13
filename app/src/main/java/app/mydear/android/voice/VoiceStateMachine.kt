package app.mydear.android.voice

import app.mydear.android.domain.TurnId

sealed interface VoiceState {
    data object Idle : VoiceState
    data class Listening(val turnId: TurnId) : VoiceState
    data class PreparingAnswer(val turnId: TurnId) : VoiceState
    data class Speaking(val turnId: TurnId, val generation: Long) : VoiceState
    data class Failed(val message: String) : VoiceState
}

sealed interface VoiceEvent {
    data class StartListening(val turnId: TurnId) : VoiceEvent
    data class SpeechAccepted(val turnId: TurnId) : VoiceEvent
    data class PlaybackStarted(val turnId: TurnId, val generation: Long) : VoiceEvent
    data class PlaybackCompleted(val generation: Long) : VoiceEvent
    data class Failure(val message: String) : VoiceEvent
    data object Cancel : VoiceEvent
}

data class VoiceTransition(val state: VoiceState, val invalidatePlayback: Boolean = false)

object VoiceReducer {
    fun reduce(state: VoiceState, event: VoiceEvent): VoiceTransition = when (event) {
        is VoiceEvent.StartListening -> VoiceTransition(
            state = VoiceState.Listening(event.turnId),
            invalidatePlayback = state is VoiceState.Speaking,
        )
        is VoiceEvent.SpeechAccepted -> if (state is VoiceState.Listening && state.turnId == event.turnId) {
            VoiceTransition(VoiceState.PreparingAnswer(event.turnId))
        } else VoiceTransition(state)
        is VoiceEvent.PlaybackStarted -> if (state is VoiceState.PreparingAnswer && state.turnId == event.turnId) {
            VoiceTransition(VoiceState.Speaking(event.turnId, event.generation))
        } else VoiceTransition(state)
        is VoiceEvent.PlaybackCompleted -> if (state is VoiceState.Speaking && state.generation == event.generation) {
            VoiceTransition(VoiceState.Idle)
        } else VoiceTransition(state)
        is VoiceEvent.Failure -> VoiceTransition(VoiceState.Failed(event.message), invalidatePlayback = state is VoiceState.Speaking)
        VoiceEvent.Cancel -> VoiceTransition(VoiceState.Idle, invalidatePlayback = state is VoiceState.Speaking)
    }

    fun accessibleLabel(state: VoiceState): String = when (state) {
        VoiceState.Idle -> "말하기"
        is VoiceState.Listening -> "듣고 있어요 · 다 말했어요"
        is VoiceState.PreparingAnswer -> "답변을 준비하고 있어요 · 취소"
        is VoiceState.Speaking -> "읽어드리고 있어요 · 다시 말하기"
        is VoiceState.Failed -> "문제가 생겼어요 · 다시 시도"
    }
}
