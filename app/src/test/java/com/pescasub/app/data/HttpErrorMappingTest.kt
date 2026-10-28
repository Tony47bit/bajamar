package com.pescasub.app.data

import com.pescasub.app.data.repo.toForecastError
import com.pescasub.app.domain.ForecastError
import java.io.IOException
import kotlinx.serialization.SerializationException
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

/** Usa Retrofit/OkHttp/kotlinx.serialization reales: se ejecuta en Gradle/CI. */
class HttpErrorMappingTest {
    private fun http(code: Int) = HttpException(Response.error<Any>(code, "".toResponseBody()))

    @Test
    fun http429IsRateLimited() {
        assertEquals(ForecastError.RATE_LIMITED, http(429).toForecastError())
    }

    @Test
    fun otherHttpErrorsAreServerErrors() {
        assertEquals(ForecastError.SERVER, http(500).toForecastError())
        assertEquals(ForecastError.SERVER, http(400).toForecastError())
    }

    @Test
    fun ioAndSerializationErrorsAreMappedSeparately() {
        assertEquals(ForecastError.NETWORK, IOException("timeout").toForecastError())
        assertEquals(ForecastError.PARSE, SerializationException("json roto").toForecastError())
    }
}
