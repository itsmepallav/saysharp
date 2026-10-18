package com.saysharp.shared.session

/** Offline, immutable content lookup shared by sessions, Today, Programme and saved-history labels. */
object BundledSessionRepository : SessionRepository {
    override fun definition(day: Int): SessionDefinition {
        require(day in 1..14)
        return SessionCatalogue.definitions[day - 1]
    }
}
