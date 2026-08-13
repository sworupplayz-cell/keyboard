package com.sworupplayz.keyboard

/**
 * Cross-system lifecycle rules. KeyboardService stays the orchestrator;
 * this is not a second typing or prediction engine.
 */
object ProductionIntegrationPolicy {
    fun shouldResetSession(restarting: Boolean): Boolean = !restarting

    fun shouldInvalidateSuggestionsOnHide(): Boolean = true

    fun shouldInvalidateSuggestionsOnAccept(): Boolean = true

    fun shouldResetRepeatLearningOnNewField(restarting: Boolean): Boolean = !restarting

    fun shouldResetGuardsOnHide(): Boolean = true

    fun singlePredictionFacade(): Boolean = true

    fun maxVisibleSuggestions(): Int = SuggestionEngine.MAX_VISIBLE

    fun floatingImplemented(): Boolean = false

    fun handwritingRecognitionImplemented(): Boolean = false

    fun allowsNetworkPrediction(): Boolean = false
}
