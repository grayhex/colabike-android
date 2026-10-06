package ru.colabike.core.model

/** What a report is about (cola docs/modules/api-v1.md, "Безопасность: блокировки и жалобы"). */
enum class ReportKind {
    Profile,
    Bike,
    BikeComment,
    Ride,
    RideComment,
    Journal,
    JournalComment,
    ComponentComment,
    ComponentPhoto,
}

/** One thing that can be reported: its kind and its UUID. */
data class ReportTarget(val kind: ReportKind, val id: String)

enum class ReportReason {
    Spam,
    Abuse,
    Inappropriate,
    Copyright,
    Other,
}

/** A person was blocked or unblocked by the viewer, announced to every screen that shows them. */
data class BlockChange(val id: UserId, val blocked: Boolean)
