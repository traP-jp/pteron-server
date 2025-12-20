package jp.trap.plutus.pteron.features.stats.infrastructure

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestamp

/**
 * システム統計キャッシュテーブル
 */
object StatsCacheSystemTable : Table("stats_cache_system") {
    val term = varchar("term", 16)
    val balance = long("balance")
    val difference = long("difference")
    val count = long("count")
    val total = long("total")
    val ratio = long("ratio")
    val updatedAt = timestamp("updated_at")

    override val primaryKey = PrimaryKey(term)
}

/**
 * ユーザー集計統計キャッシュテーブル
 */
object StatsCacheUsersTable : Table("stats_cache_users") {
    val term = varchar("term", 16)
    val number = long("number")
    val balance = long("balance")
    val difference = long("difference")
    val count = long("count")
    val total = long("total")
    val ratio = long("ratio")
    val updatedAt = timestamp("updated_at")

    override val primaryKey = PrimaryKey(term)
}

/**
 * プロジェクト集計統計キャッシュテーブル
 */
object StatsCacheProjectsTable : Table("stats_cache_projects") {
    val term = varchar("term", 16)
    val number = long("number")
    val balance = long("balance")
    val difference = long("difference")
    val count = long("count")
    val total = long("total")
    val ratio = long("ratio")
    val updatedAt = timestamp("updated_at")

    override val primaryKey = PrimaryKey(term)
}

/**
 * ユーザーランキングキャッシュテーブル
 */
object StatsCacheUserRankingsTable : Table("stats_cache_user_rankings") {
    val term = varchar("term", 16)
    val rankingType = varchar("ranking_type", 16)
    val userId = uuid("user_id")
    val rankValue = long("rank_value")
    val difference = long("difference")
    val updatedAt = timestamp("updated_at")

    override val primaryKey = PrimaryKey(term, rankingType, userId)
}

/**
 * プロジェクトランキングキャッシュテーブル
 */
object StatsCacheProjectRankingsTable : Table("stats_cache_project_rankings") {
    val term = varchar("term", 16)
    val rankingType = varchar("ranking_type", 16)
    val projectId = uuid("project_id")
    val rankValue = long("rank_value")
    val difference = long("difference")
    val updatedAt = timestamp("updated_at")

    override val primaryKey = PrimaryKey(term, rankingType, projectId)
}
