package org.tasks.constructor

class FeatureGate(initial: Constructor = Constructor()) {
    @Volatile
    var current: Constructor = initial

    /** Google Tasks account present and not paused. */
    @Volatile
    var sync: Boolean = false
}
