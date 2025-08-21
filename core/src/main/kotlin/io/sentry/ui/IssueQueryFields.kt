package io.sentry.ui

/**
 * Strongly-typed representation of issue query fields converted from issue_query_fields.json
 */
data class FieldValueEntry(
    val value: String,
    val description: String? = null,
)

data class QueryField(
    val key: String,
    val description: String,
    val values: List<FieldValueEntry> = emptyList(),
)

object IssueQueryFields {
    val fields: List<QueryField> = listOf(
        QueryField(
            key = "is",
            description = "The properties of an issue (i.e. Resolved, unresolved)",
            values = listOf(
                FieldValueEntry(value = "resolved", description = "Issues marked as fixed"),
                FieldValueEntry(value = "unresolved", description = "Issues still active and needing attention"),
                FieldValueEntry(value = "archived", description = "Issues that have been archived"),
                FieldValueEntry(
                    value = "escalating",
                    description = "Issues occurring significantly more often than they used to"
                ),
                FieldValueEntry(value = "new", description = "Issues that first occurred in the last 7 days"),
                FieldValueEntry(
                    value = "ongoing",
                    description = "Issues created more than 7 days ago or manually been marked as reviewed"
                ),
                FieldValueEntry(value = "regressed", description = "Issues resolved then occurred again"),
                FieldValueEntry(value = "assigned", description = "Issues assigned to a team member"),
                FieldValueEntry(value = "unassigned", description = "Issues not assigned to anyone"),
                FieldValueEntry(value = "for_review", description = "Issues pending review"),
                FieldValueEntry(value = "linked", description = "Issues linked to other issues"),
                FieldValueEntry(value = "unlinked", description = "Issues not linked to other issues"),
            ),
        ),
        QueryField(
            key = "release",
            description = "The version of your code deployed to an environment",
        ),
        QueryField(
            key = "firstRelease",
            description = "Issues first seen in a given release",
        ),
        QueryField(
            key = "user.email",
            description = "Email address of the user",
        ),
        QueryField(
            key = "user.id",
            description = "Application specific internal identifier of the user",
        ),
        QueryField(
            key = "user.username",
            description = "Username of the user",
        ),
        QueryField(
            key = "error.type",
            description = "The type of exception",
        ),
        QueryField(
            key = "project",
            description = "Project slug",
        ),
        QueryField(
            key = "environment",
            description = "The environment the event was seen in",
            values = listOf(FieldValueEntry("production", "debug"))
        ),
        QueryField(
            key = "level",
            description = "Severity of the event (i.e., fatal, error, warning)",
        ),
        QueryField(
            key = "event.type",
            description = "Type of event (Errors, transactions, csp and default)",
        ),
        QueryField(
            key = "issue",
            description = "The issue identification short code",
        ),
        QueryField(
            key = "status",
            description = "Status of the issue",
        ),
        QueryField(
            key = "lastSeen",
            description = "Issues last seen at a given time",
        ),
        QueryField(
            key = "firstSeen",
            description = "Issues first seen at a given time",
        ),
        QueryField(
            key = "timesSeen",
            description = "Total number of events",
        ),
        QueryField(
            key = "title",
            description = "Error or transaction name identifier",
        ),
        QueryField(
            key = "message",
            description = "Error message or transaction name",
        ),
        QueryField(
            key = "user",
            description = "User identification value",
        ),
        QueryField(
            key = "user.display",
            description = "The first user field available of email, username, ID, and IP",
        ),
        QueryField(
            key = "user.ip",
            description = "IP Address of the user",
        ),
        QueryField(
            key = "assigned",
            description = "Assignee of the issue as a user ID",
        ),
        QueryField(
            key = "assigned_or_suggested",
            description = "Assignee or suggestee of the issue as a user ID",
        ),
        QueryField(
            key = "issue.category",
            description = "Category of issue (error or performance)",
        ),
        QueryField(
            key = "issue.type",
            description = "Type of problem the issue represents (i.e. N+1 Query)",
        ),
        QueryField(
            key = "release.stage",
            description = "Stage of usage (i.e., adopted, replaced, low)",
        ),
        QueryField(
            key = "release.version",
            description = "An abbreviated version number of the build",
        ),
        QueryField(
            key = "release.build",
            description = "The full version number that identifies the iteration",
        ),
        QueryField(
            key = "release.package",
            description = "The identifier unique to the project or application",
        ),
        QueryField(
            key = "error.handled",
            description = "Determines handling status of the error",
        ),
        QueryField(
            key = "error.unhandled",
            description = "Determines unhandling status of the error",
        ),
        QueryField(
            key = "error.value",
            description = "Original value that exhibits error",
        ),
        QueryField(
            key = "error.main_thread",
            description = "Indicates if the error occurred on the main thread",
            values = listOf(
                FieldValueEntry(value = "true"),
                FieldValueEntry(value = "false"),
            )
        ),
        QueryField(
            key = "device.class",
            description = "The estimated performance level of the device, graded low, medium, or high",
            values = listOf(
                FieldValueEntry(value = "high"),
                FieldValueEntry(value = "medium"),
                FieldValueEntry(value = "low"),
            ),
        ),
    )

    fun findFieldByKey(key: String): QueryField? = fields.find { it.key == key }
}
