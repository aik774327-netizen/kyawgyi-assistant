package com.example.data.local

import com.example.data.AppDatabase

/**
 * Unified KyawGyi Database.
 * Consolidated into AppDatabase to provide a single source of truth,
 * eliminating multi-database locks, reducing RAM footprint, and ensuring
 * seamless local persistence across all voice, shortcut, and AI modules.
 */
typealias KyawGyiDatabase = AppDatabase

