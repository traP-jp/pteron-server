package jp.trap.plutus.pteron.features.user.controller

import io.ktor.server.auth.*
import io.ktor.server.resources.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import jp.trap.plutus.pteron.features.account.domain.model.Account
import jp.trap.plutus.pteron.features.account.service.AccountService
import jp.trap.plutus.pteron.features.user.domain.model.User
import jp.trap.plutus.pteron.features.user.service.UserService
import jp.trap.plutus.pteron.openapi.internal.Paths
import jp.trap.plutus.pteron.utils.trapId
import org.koin.ktor.ext.inject
import jp.trap.plutus.pteron.openapi.internal.models.User as UserDto

fun Route.userRoutes() {
    val userService by inject<UserService>()
    val accountService by inject<AccountService>()

    authenticate("ForwardAuth") {
        get<Paths.getCurrentUser> {
            val user = userService.getUserByName(call.trapId)
            val account = accountService.getAccountById(user.accountId)

            call.respond(createUserDto(user, account))
        }

        get<Paths.getUser> {
            val user = userService.getUser(it.userId)
            val account = accountService.getAccountById(user.accountId)

            call.respond(createUserDto(user, account))
        }
    }
}

private fun createUserDto(
    user: User,
    account: Account,
): UserDto =
    UserDto(
        id = user.id.value,
        name = user.name.value,
        balance = account.balance,
    )
