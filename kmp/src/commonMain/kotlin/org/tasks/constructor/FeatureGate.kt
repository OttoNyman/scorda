package org.tasks.constructor

class FeatureGate(initial: Constructor = Constructor()) {
    @Volatile
    var current: Constructor = initial
}
