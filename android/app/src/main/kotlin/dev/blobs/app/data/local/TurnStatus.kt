package dev.blobs.app.data.local

/** Stable persisted states for a locally generated research turn. */
internal enum class TurnStatus(val storedValue: String) {
    RUNNING("RUNNING"),
    COMPLETE("COMPLETE"),
    STOPPED("STOPPED"),
    ERROR("ERROR"),

    // Values written by earlier builds. Keep them readable; new turns use the
    // lifecycle vocabulary above and record detail in TurnFinishReason.
    INTERRUPTED("INTERRUPTED"),
    LIMITED("LIMITED"),
    CONTEXT_FULL("CONTEXT_FULL"),
    NO_SOURCES("NO_SOURCES"),
    UNVERIFIED("UNVERIFIED");

    companion object {
        fun fromStored(value: String): TurnStatus? = values().firstOrNull {
            it.storedValue == value
        }
    }
}

internal val TurnEntity.statusKind: TurnStatus?
    get() = TurnStatus.fromStored(status)

/** Source-grounding disclosure, deliberately separate from terminal lifecycle. */
internal enum class TurnGrounding(val storedValue: String) {
    PENDING("PENDING"),
    GROUNDED("GROUNDED"),
    UNVERIFIED("UNVERIFIED"),
    NOT_REQUESTED("NOT_REQUESTED"),
    NONE("NONE"),
    LEGACY("LEGACY");

    companion object {
        fun fromStored(value: String): TurnGrounding? = values().firstOrNull {
            it.storedValue == value
        }
    }
}

/** Native completion outcome, separate from whether the body is source-grounded. */
internal enum class TurnFinishReason(val storedValue: String) {
    PENDING("PENDING"),
    STOP("STOP"),
    TOKEN_LIMIT("TOKEN_LIMIT"),
    CANCELLED("CANCELLED"),
    CONTEXT_FULL("CONTEXT_FULL"),
    ERROR("ERROR"),
    LEGACY("LEGACY");

    companion object {
        fun fromStored(value: String): TurnFinishReason? = values().firstOrNull {
            it.storedValue == value
        }
    }
}

internal val TurnEntity.groundingKind: TurnGrounding?
    get() = TurnGrounding.fromStored(grounding)

internal val TurnEntity.finishReasonKind: TurnFinishReason?
    get() = TurnFinishReason.fromStored(finishReason)
