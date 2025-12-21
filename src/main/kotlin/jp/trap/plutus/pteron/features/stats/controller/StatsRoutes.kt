package jp.trap.plutus.pteron.features.stats.controller

import io.ktor.server.resources.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import jp.trap.plutus.pteron.common.domain.model.ProjectId
import jp.trap.plutus.pteron.common.domain.model.UserId
import jp.trap.plutus.pteron.features.stats.domain.model.RankingType
import jp.trap.plutus.pteron.features.stats.domain.model.Term
import jp.trap.plutus.pteron.features.stats.service.StatsService
import jp.trap.plutus.pteron.openapi.internal.Paths
import jp.trap.plutus.pteron.openapi.internal.models.*
import org.koin.ktor.ext.inject
import kotlin.time.Clock
import kotlin.uuid.Uuid

fun Route.statsRoutes() {
    val statsService by inject<StatsService>()

    // GET /stats - システム全体統計
    get<Paths.getSystemStats> { params ->
        val term = Term.fromString(params.term)
        val stats = statsService.getSystemStats(term)

        call.respond(
            GetSystemStats200Response(
                balance = stats.balance,
                difference = stats.difference,
                count = stats.count,
                total = stats.total,
                ratio = stats.ratio,
            ),
        )
    }

    // GET /stats/users - ユーザー集計統計
    get<Paths.getUsersStats> { params ->
        val term = Term.fromString(params.term)
        val stats = statsService.getUsersAggregateStats(term)

        call.respond(
            GetUsersStats200Response(
                number = stats.number,
                balance = stats.balance,
                difference = stats.difference,
                count = stats.count,
                total = stats.total,
                ratio = stats.ratio,
            ),
        )
    }

    // GET /stats/projects - プロジェクト集計統計
    get<Paths.getProjectsStats> { params ->
        val term = Term.fromString(params.term)
        val stats = statsService.getProjectsAggregateStats(term)

        call.respond(
            GetUsersStats200Response(
                number = stats.number,
                balance = stats.balance,
                difference = stats.difference,
                count = stats.count,
                total = stats.total,
                ratio = stats.ratio,
            ),
        )
    }

    // GET /stats/users/{rankingName} - ユーザーランキング
    get<Paths.getUserRankings> { params ->
        val rankingType = RankingType.fromString(params.rankingName)
        val term = Term.fromString(params.term)
        val ascending = params.order == "asc"
        val limit = params.limit ?: 20

        val result = statsService.getUserRankings(rankingType, term, ascending, limit, params.cursor)

        val items =
            result.items.map { item ->
                GetUserRankings200ResponseItemsInner(
                    rank = item.rank,
                    value = item.value,
                    difference = item.difference,
                    user =
                        User(
                            id = item.user.id.value,
                            name = item.user.name.value,
                            balance = item.balance,
                        ),
                )
            }

        call.respond(
            GetUserRankings200Response(
                items = items,
                nextCursor = result.nextCursor,
            ),
        )
    }

    // GET /stats/projects/{projectName} - プロジェクトランキング
    get<Paths.getProjectRankings> { params ->
        val rankingType = RankingType.fromString(params.projectName)
        val term = Term.fromString(params.term)
        val ascending = params.order == "asc"
        val limit = params.limit ?: 20

        val result = statsService.getProjectRankings(rankingType, term, ascending, limit, params.cursor)

        val items =
            result.items.map { item ->
                GetProjectRankings200ResponseItemsInner(
                    rank = item.rank,
                    value = item.value,
                    difference = item.difference,
                    project =
                        Project(
                            id = item.project.id.value,
                            name = item.project.name.value,
                            owner =
                                User(
                                    id = item.owner.id.value,
                                    name = item.owner.name.value,
                                    balance = item.ownerBalance,
                                ),
                            admins =
                                item.admins.map { admin ->
                                    User(
                                        id = admin.user.id.value,
                                        name = admin.user.name.value,
                                        balance = admin.balance,
                                    )
                                },
                            balance = item.projectBalance,
                            url = item.project.url?.value,
                        ),
                )
            }

        call.respond(
            GetProjectRankings200Response(
                items = items,
                nextCursor = result.nextCursor,
            ),
        )
    }

    // GET /users/{userId}/stats - 個別ユーザー統計
    get<Paths.getUserStats> { params ->
        val userId = UserId(Uuid.parse(params.userId))
        val term = Term.fromString(params.term)

        val result = statsService.getUserStats(userId, term)
        val stats = result.stats

        val userObj =
            User(
                id = result.user.id.value,
                name = result.user.name.value,
                balance = result.balance,
            )

        call.respond(
            GetUserStats200Response(
                balance =
                    GetUserRankings200ResponseItemsInner(
                        rank = stats.balance.rank,
                        value = stats.balance.value,
                        difference = stats.balance.difference,
                        user = userObj,
                    ),
                difference =
                    GetUserRankings200ResponseItemsInner(
                        rank = stats.difference.rank,
                        value = stats.difference.value,
                        difference = stats.difference.difference,
                        user = userObj,
                    ),
                `in` =
                    GetUserRankings200ResponseItemsInner(
                        rank = stats.inAmount.rank,
                        value = stats.inAmount.value,
                        difference = stats.inAmount.difference,
                        user = userObj,
                    ),
                `out` =
                    GetUserRankings200ResponseItemsInner(
                        rank = stats.outAmount.rank,
                        value = stats.outAmount.value,
                        difference = stats.outAmount.difference,
                        user = userObj,
                    ),
                count =
                    GetUserRankings200ResponseItemsInner(
                        rank = stats.count.rank,
                        value = stats.count.value,
                        difference = stats.count.difference,
                        user = userObj,
                    ),
                total =
                    GetUserRankings200ResponseItemsInner(
                        rank = stats.total.rank,
                        value = stats.total.value,
                        difference = stats.total.difference,
                        user = userObj,
                    ),
                ratio =
                    GetUserRankings200ResponseItemsInner(
                        rank = stats.ratio.rank,
                        value = stats.ratio.value,
                        difference = stats.ratio.difference,
                        user = userObj,
                    ),
            ),
        )
    }

    // GET /users/{userId}/balance - 特定時点でのユーザー残高
    get<Paths.getUserBalance> { params ->
        val userId = UserId(Uuid.parse(params.userId))
        val at = params.date ?: Clock.System.now()

        val balance = statsService.getUserBalanceAt(userId, at)

        call.respond(
            GetUserBalance200Response(balance = balance),
        )
    }

    // GET /projects/{projectId}/stats - 個別プロジェクト統計
    get<Paths.getProjectStats> { params ->
        val projectId = ProjectId(Uuid.parse(params.projectId))
        val term = Term.fromString(params.term)

        val result = statsService.getProjectStats(projectId, term)
        val stats = result.stats

        val projectObj =
            Project(
                id = result.project.id.value,
                name = result.project.name.value,
                owner =
                    User(
                        id = result.owner.id.value,
                        name = result.owner.name.value,
                        balance = result.ownerBalance,
                    ),
                admins =
                    result.admins.map { admin ->
                        User(
                            id = admin.user.id.value,
                            name = admin.user.name.value,
                            balance = admin.balance,
                        )
                    },
                balance = result.projectBalance,
                url = result.project.url?.value,
            )

        call.respond(
            GetProjectStats200Response(
                balance =
                    GetProjectRankings200ResponseItemsInner(
                        rank = stats.balance.rank,
                        value = stats.balance.value,
                        difference = stats.balance.difference,
                        project = projectObj,
                    ),
                difference =
                    GetProjectRankings200ResponseItemsInner(
                        rank = stats.difference.rank,
                        value = stats.difference.value,
                        difference = stats.difference.difference,
                        project = projectObj,
                    ),
                `in` =
                    GetProjectRankings200ResponseItemsInner(
                        rank = stats.inAmount.rank,
                        value = stats.inAmount.value,
                        difference = stats.inAmount.difference,
                        project = projectObj,
                    ),
                `out` =
                    GetProjectRankings200ResponseItemsInner(
                        rank = stats.outAmount.rank,
                        value = stats.outAmount.value,
                        difference = stats.outAmount.difference,
                        project = projectObj,
                    ),
                count =
                    GetProjectRankings200ResponseItemsInner(
                        rank = stats.count.rank,
                        value = stats.count.value,
                        difference = stats.count.difference,
                        project = projectObj,
                    ),
                total =
                    GetProjectRankings200ResponseItemsInner(
                        rank = stats.total.rank,
                        value = stats.total.value,
                        difference = stats.total.difference,
                        project = projectObj,
                    ),
                ratio =
                    GetProjectRankings200ResponseItemsInner(
                        rank = stats.ratio.rank,
                        value = stats.ratio.value,
                        difference = stats.ratio.difference,
                        project = projectObj,
                    ),
            ),
        )
    }

    // GET /projects/{projectId}/balance - 特定時点でのプロジェクト残高
    get<Paths.getProjectBalance> { params ->
        val projectId = ProjectId(Uuid.parse(params.projectId))
        val at = params.date ?: Clock.System.now()

        val balance = statsService.getProjectBalanceAt(projectId, at)

        call.respond(
            GetUserBalance200Response(balance = balance),
        )
    }
}
