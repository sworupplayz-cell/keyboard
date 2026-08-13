package com.sworupplayz.keyboard

/**
 * Per-field / per-session / persistent state rules for a real InputMethodService.
 * KeyboardService stays the orchestrator; this is not a second typing, prediction,
 * or touch engine.
 */
enum class ImeSessionScope {
    FIELD,
    INPUT_SESSION,
    PERSISTENT
}

object ImeSessionState {
    fun scopeForComposing(): ImeSessionScope = ImeSessionScope.FIELD

    fun scopeForSuggestions(): ImeSessionScope = ImeSessionScope.FIELD

    fun scopeForEditorInfo(): ImeSessionScope = ImeSessionScope.FIELD

    fun scopeForTouchPointers(): ImeSessionScope = ImeSessionScope.INPUT_SESSION

    fun scopeForBackspaceRepeat(): ImeSessionScope = ImeSessionScope.INPUT_SESSION

    fun scopeForPreview(): ImeSessionScope = ImeSessionScope.INPUT_SESSION

    fun scopeForLearnedWords(): ImeSessionScope = ImeSessionScope.PERSISTENT

    fun scopeForSettings(): ImeSessionScope = ImeSessionScope.PERSISTENT

    fun shouldReset(scope: ImeSessionScope, event: ImeLifecycleEvent): Boolean = when (event) {
        ImeLifecycleEvent.FIELD_CHANGE -> scope != ImeSessionScope.PERSISTENT
        ImeLifecycleEvent.HIDE -> scope == ImeSessionScope.INPUT_SESSION || scope == ImeSessionScope.FIELD
        ImeLifecycleEvent.SHOW -> false
        ImeLifecycleEvent.DESTROY -> true
        ImeLifecycleEvent.CONFIGURATION -> scope == ImeSessionScope.INPUT_SESSION
        ImeLifecycleEvent.RESTARTING_SAME_FIELD -> scope == ImeSessionScope.INPUT_SESSION
    }
}

enum class ImeLifecycleEvent {
    FIELD_CHANGE,
    HIDE,
    SHOW,
    DESTROY,
    CONFIGURATION,
    RESTARTING_SAME_FIELD
}

object ImeLifecyclePolicy {
    fun shouldApplyFieldPolicyOnStartInput(): Boolean = true

    fun shouldResetFieldState(restarting: Boolean): Boolean = !restarting

    fun shouldResetTransientStateOnHide(): Boolean = true

    fun shouldFinishComposingOnHide(): Boolean = true

    fun shouldConvertRomanOnHide(): Boolean = false

    fun shouldResetTouchOnHide(): Boolean = true

    fun shouldResetTouchOnDestroy(): Boolean = true

    fun shouldResetTouchOnConfiguration(): Boolean = true

    fun shouldStopBackspaceOnHide(): Boolean = true

    fun shouldStopBackspaceOnFieldChange(): Boolean = true

    fun shouldStopBackspaceOnDestroy(): Boolean = true

    fun shouldIgnoreInputWhenHidden(inputViewActive: Boolean): Boolean = !inputViewActive

    fun shouldInvalidateSuggestionsOnHide(): Boolean = true

    fun shouldInvalidateSuggestionsOnFieldChange(restarting: Boolean): Boolean = !restarting

    fun shouldInvalidateSuggestionsOnSelectionChange(internal: Boolean): Boolean = !internal

    fun shouldPreserveSettingsOnFieldChange(): Boolean = true

    fun shouldPreserveLearningOnFieldChange(): Boolean = true

    fun shouldPreferNumberPad(inputType: Int): Boolean =
        EditorFieldPolicy.prefersNumberPad(inputType)

    fun shouldOpenNumberPadOnNewField(inputType: Int, restarting: Boolean): Boolean =
        !restarting && shouldPreferNumberPad(inputType)
}

object ImeTouchLifecycle {
    fun shouldResetOnHide(): Boolean = true

    fun shouldResetOnDestroy(): Boolean = true

    fun shouldResetOnPanelOpen(): Boolean = true

    fun shouldDeliverEventAfterHide(inputViewActive: Boolean): Boolean = inputViewActive

    fun secondFingerSteals(): Boolean = false

    fun allowsRebuildOnMove(): Boolean = CoreTypingPolicy.allowsKeyboardRebuildOnMove()

    fun allowsPredictionOnMove(): Boolean = CoreTypingPolicy.allowsPredictionOnMove()
}

object ImeMemoryPolicy {
    fun learnedWordLimit(): Int = LearnedWordStore.DEFAULT_LIMIT

    fun recentWordLimit(): Int = RecentWordStore.DEFAULT_LIMIT

    fun adaptivePairLimit(): Int = TouchCalibration.MAX_ADAPTIVE_PAIRS

    fun suggestionCacheSlots(): Int = 1

    fun maxVisibleSuggestions(): Int = SuggestionEngine.MAX_VISIBLE
}
