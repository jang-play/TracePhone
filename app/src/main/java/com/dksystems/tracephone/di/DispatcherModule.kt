package com.dksystems.tracephone.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * 디스크·시스템 조회처럼 블로킹이 발생하는 작업용 디스패처.
 *
 * 주입으로 받으면 테스트에서 즉시 실행 디스패처로 바꿔 끼울 수 있다.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

/**
 * 화면이 사라져도 끝까지 수행해야 하는 작업용 스코프.
 *
 * `viewModelScope`는 화면이 닫히면 취소된다. 설정을 저장하고 곧바로 뒤로 나가면
 * 저장이나 위젯 갱신이 중간에 끊겨, 화면에서는 바꿨는데 실제로는 옛 값이 남는다.
 * 짧게 끝나고 결과를 반드시 남겨야 하는 일만 이 스코프에서 실행한다.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object DispatcherModule {

    @Provides
    @Singleton
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    /**
     * `SupervisorJob`을 쓴다. 한 작업이 실패해도 나머지 작업까지 함께 죽으면 안 된다.
     *
     * 애플리케이션과 수명을 같이하므로 따로 취소하지 않는다.
     */
    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(
        @IoDispatcher ioDispatcher: CoroutineDispatcher
    ): CoroutineScope = CoroutineScope(SupervisorJob() + ioDispatcher)
}
