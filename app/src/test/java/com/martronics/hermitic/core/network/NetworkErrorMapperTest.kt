package com.martronics.hermitic.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkErrorMapperTest {

    @Test
    fun detectsCloudflareAccessHtmlRedirect() {
        val err302 = NetworkErrorMapper.mapHttpFailure(
            statusCode = 302,
            contentType = "text/html",
            locationHeader = "https://martronics.cloudflareaccess.com/cdn-cgi/access/login",
            body = "<html>Redirecting</html>",
        )
        assertTrue(err302 is HermesApiError.CloudflareAccessRedirect)

        val err403 = NetworkErrorMapper.mapHttpFailure(
            statusCode = 403,
            contentType = "text/html; charset=utf-8",
            locationHeader = null,
            body = "<!DOCTYPE html><html><title>Cloudflare Access</title></html>",
        )
        assertTrue(err403 is HermesApiError.CloudflareAccessRedirect)
    }

    @Test
    fun mapsUnauthorizedAndConflictErrors() {
        val unauth = NetworkErrorMapper.mapHttpFailure(
            statusCode = 401,
            contentType = "application/json",
            locationHeader = null,
            body = """{"error":"invalid_token","detail":"Bad bearer"}""",
        )
        assertTrue(unauth is HermesApiError.Unauthorized)
        assertEquals("Bad bearer", unauth.message)

        val conflict = NetworkErrorMapper.mapHttpFailure(
            statusCode = 409,
            contentType = "application/json",
            locationHeader = null,
            body = """{"code":"stale_generation","detail":"Generation 2 already settled"}""",
        )
        assertTrue(conflict is HermesApiError.Conflict)
        assertEquals("stale_generation", conflict.code)
    }
}
