package com.martronics.hermitic.core.network

import com.martronics.hermitic.core.network.dto.RunEventParser
import com.martronics.hermitic.core.network.dto.RunStreamEvent
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HermesSseClient @Inject constructor(
    private val apiClient: HermesApiClient,
    private val json: Json,
) {
    fun streamRunEvents(runId: String): Flow<RunStreamEvent> = callbackFlow {
        val config = apiClient.currentConfigOrThrow()
        val url = config.baseUrl.trimEnd('/')
            .toHttpUrl()
            .newBuilder()
            .addPathSegment("v1")
            .addPathSegment("runs")
            .addPathSegment(runId)
            .addPathSegment("events")
            .build()

        val sseHttpClient = apiClient.buildClient(config).newBuilder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build()

        val request = Request.Builder()
            .url(url)
            .header(HermesHeadersInterceptor.HEADER_ACCEPT, "text/event-stream")
            .header("Cache-Control", "no-cache")
            .get()
            .build()

        var terminalReached = false
        val listener = object : EventSourceListener() {
            override fun onEvent(
                eventSource: EventSource,
                id: String?,
                type: String?,
                data: String,
            ) {
                val parsed = RunEventParser.parse(type, data, json) ?: return
                trySend(parsed)
                if (parsed is RunStreamEvent.RunCompleted ||
                    parsed is RunStreamEvent.RunIncomplete ||
                    parsed is RunStreamEvent.RunFailed ||
                    parsed is RunStreamEvent.RunCancelled ||
                    parsed is RunStreamEvent.RunInterrupted
                ) {
                    terminalReached = true
                    eventSource.cancel()
                    close()
                }
            }

            override fun onClosed(eventSource: EventSource) {
                if (!terminalReached) {
                    trySend(RunStreamEvent.StreamDisconnected(null))
                }
                close()
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                if (terminalReached) {
                    close()
                    return
                }
                if (response != null) {
                    val body = runCatching { response.body?.string().orEmpty() }.getOrDefault("")
                    val mapped = NetworkErrorMapper.mapHttpFailure(
                        statusCode = response.code,
                        contentType = response.header("Content-Type"),
                        locationHeader = response.header("Location"),
                        body = body,
                        retryAfterHeader = response.header("Retry-After"),
                        json = json,
                    )
                    close(mapped)
                    return
                }
                trySend(RunStreamEvent.StreamDisconnected(t))
                close()
            }
        }

        val factory = EventSources.createFactory(sseHttpClient)
        val source = factory.newEventSource(request, listener)

        awaitClose {
            source.cancel()
        }
    }
}
