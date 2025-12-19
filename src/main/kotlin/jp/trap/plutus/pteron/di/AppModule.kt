package jp.trap.plutus.pteron.di

import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module

@Module(includes = [GrpcModule::class, DatabaseModule::class])
@ComponentScan("jp.trap.plutus.pteron")
object AppModule
