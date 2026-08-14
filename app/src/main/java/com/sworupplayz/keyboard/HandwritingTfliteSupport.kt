package com.sworupplayz.keyboard

/**
 * Shared lazy interpreter holder for English and Nepali TFLite runtimes.
 * This is not a second recognition system and does not invent text.
 */
class ReusableInkRuntime<I> {
    private var interpreter: I? = null
    private var loadedFrom: String? = null

    @Synchronized
    fun isLoaded(): Boolean = interpreter != null

    @Synchronized
    fun loadedAsset(): String? = loadedFrom

    @Synchronized
    fun get(): I? = interpreter

    @Synchronized
    fun load(assetName: String, factory: () -> I?): Boolean {
        if (interpreter != null && loadedFrom == assetName) return true
        unloadUnlocked()
        val created = factory() ?: return false
        interpreter = created
        loadedFrom = assetName
        return true
    }

    @Synchronized
    fun unload() {
        unloadUnlocked()
    }

    private fun unloadUnlocked() {
        interpreter = null
        loadedFrom = null
    }
}
