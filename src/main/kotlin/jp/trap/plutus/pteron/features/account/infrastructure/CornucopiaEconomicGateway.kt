package jp.trap.plutus.pteron.features.account.infrastructure

import io.grpc.Status
import io.grpc.StatusException
import jp.trap.plutus.api.CornucopiaServiceGrpcKt.CornucopiaServiceCoroutineStub
import jp.trap.plutus.api.createAccountRequest
import jp.trap.plutus.api.getAccountRequest
import jp.trap.plutus.api.getAccountsRequest
import jp.trap.plutus.api.transferRequest
import jp.trap.plutus.pteron.common.domain.model.AccountId
import jp.trap.plutus.pteron.features.account.domain.gateway.EconomicGateway
import jp.trap.plutus.pteron.features.account.domain.model.Account
import org.koin.core.annotation.Single
import kotlin.uuid.Uuid

@Single(binds = [EconomicGateway::class])
class CornucopiaEconomicGateway(
    private val stub: CornucopiaServiceCoroutineStub,
) : EconomicGateway {
    override suspend fun findAccountById(accountId: AccountId): Account? {
        val request =
            getAccountRequest {
                this.accountId = accountId.value.toString()
            }
        return try {
            val response = stub.getAccount(request)
            Account(
                accountId = AccountId(Uuid.parse(response.accountId)),
                balance = response.balance,
                canOverdraft = response.canOverdraft,
            )
        } catch (e: StatusException) {
            if (e.status.code == Status.Code.NOT_FOUND) {
                null
            } else {
                throw e
            }
        }
    }

    override suspend fun findAccountsByIds(accountIds: List<AccountId>): List<Account> {
        val request =
            getAccountsRequest {
                this.accountIds.addAll(accountIds.map { it.value.toString() })
            }
        return stub.getAccounts(request).accountsList.map {
            Account(
                accountId = AccountId(Uuid.parse(it.accountId)),
                balance = it.balance,
                canOverdraft = it.canOverdraft,
            )
        }
    }

    override suspend fun transfer(
        from: AccountId,
        to: AccountId,
        amount: Long,
    ) {
        val request =
            transferRequest {
                this.fromAccountId = from.value.toString()
                this.toAccountId = to.value.toString()
                this.amount = amount
                this.description = "Transfer from Pteron"
                this.idempotencyKey = Uuid.random().toString()
            }
        stub.transfer(request)
    }

    override suspend fun createAccount(canOverdraft: Boolean): Account {
        val request =
            createAccountRequest {
                this.canOverdraft = canOverdraft
            }
        val response = stub.createAccount(request)
        return Account(
            accountId = AccountId(Uuid.parse(response.accountId)),
            balance = response.balance,
            canOverdraft = response.canOverdraft,
        )
    }
}
