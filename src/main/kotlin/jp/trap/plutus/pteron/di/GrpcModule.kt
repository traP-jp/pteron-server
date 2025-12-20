package jp.trap.plutus.pteron.di

import io.grpc.*
import jp.trap.plutus.api.CornucopiaServiceGrpcKt.CornucopiaServiceCoroutineStub
import jp.trap.plutus.pteron.config.Environment
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

@Module
class GrpcModule {
    @Single
    fun provideCornucopiaServiceCoroutineStub(): CornucopiaServiceCoroutineStub {
        val channel =
            ManagedChannelBuilder
                .forAddress(Environment.GRPC_HOST, Environment.GRPC_PORT)
                .usePlaintext()
                .intercept(ApiKeyInterceptor(Environment.GRPC_TOKEN))
                .build()

        return CornucopiaServiceCoroutineStub(channel)
    }
}

private class ApiKeyInterceptor(
    private val apiKey: String,
) : ClientInterceptor {
    companion object {
        private val API_KEY_HEADER: Metadata.Key<String> =
            Metadata.Key.of("x-api-key", Metadata.ASCII_STRING_MARSHALLER)
    }

    override fun <ReqT : Any?, RespT : Any?> interceptCall(
        method: MethodDescriptor<ReqT, RespT>,
        callOptions: CallOptions,
        next: Channel,
    ): ClientCall<ReqT, RespT> =
        object :
            ForwardingClientCall.SimpleForwardingClientCall<ReqT, RespT>(
                next.newCall(method, callOptions),
            ) {
            override fun start(
                responseListener: Listener<RespT>,
                headers: Metadata,
            ) {
                headers.put(API_KEY_HEADER, apiKey)
                super.start(responseListener, headers)
            }
        }
}
