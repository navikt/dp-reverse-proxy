package no.nav.helse

import io.ktor.application.Application
import io.ktor.application.ApplicationCallPipeline
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.client.HttpClient
import io.ktor.client.call.call
import io.ktor.client.engine.apache.Apache
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.url
import io.ktor.client.response.readText
import io.ktor.content.TextContent
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.header
import io.ktor.response.respond
import io.ktor.routing.Routing
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.URL

private val logger: Logger = LoggerFactory.getLogger("nav.App")
private val monitoringPaths = listOf("isalive", "isready")

fun main(args: Array<String>): Unit  = io.ktor.server.netty.EngineMain.main(args)

fun Application.helseReverseProxy() {
    val mappings = Environment().getMappings()
    val client = HttpClient(Apache)
    val excludeHeaders = mutableListOf<String>()

    HttpHeaders.UnsafeHeaders.forEach { header ->
        excludeHeaders.add(header.toLowerCase())
    }

    install(Routing) {
        monitoring()
    }

    intercept(ApplicationCallPipeline.Call) {
        if (!call.request.hasValidPath()) {
            call.respond(HttpStatusCode.BadGateway, "Invalid requested path.")
        } else if (!call.request.isMonitoringRequest())  {
            val destinationApplication = call.request.firstPathSegment()
            logger.trace("destinationApplication = '$destinationApplication'")
            val destinationPath = call.request.pathWithoutFirstPathSegment()
            logger.trace("destinationPath = '$destinationPath'")
            val httpMethod = call.request.httpMethod
            logger.trace("httpMethod = '$httpMethod'")

            if (!mappings.containsKey(destinationApplication)) {
                call.respond(HttpStatusCode.BadGateway, "Application '$destinationApplication' not configured.")
            } else  {
                val queryParameters = call.request.queryParameters
                val headers = call.request.headers

                val destinationUrl = produceDestinationUrl(destinationPath, mappings[destinationApplication]!!)
                logger.trace("destinationUrl = '$destinationUrl'")

                val httpRequestBuilder = HttpRequestBuilder()

                httpRequestBuilder.url(destinationUrl)
                httpRequestBuilder.method = httpMethod

                queryParameters.forEach { key, values ->
                    values.forEach { value ->
                        httpRequestBuilder.parameter(key, value)
                    }
                }
                headers.forEach { key, values ->
                    values.forEach { value ->
                        if (!excludeHeaders.contains(key.toLowerCase())) {
                            httpRequestBuilder.header(key, value)
                        }
                    }
                }


                httpRequestBuilder.body = TextContent(call.receiveText(), contentType = ContentType.Application.Json)

                try {
                    val clientResponse = client.call(httpRequestBuilder).response
                    clientResponse.headers.forEach { key, values ->
                        values.forEach {value ->
                            if (!excludeHeaders.contains(key.toLowerCase())) {
                                call.response.header(key, value)
                            }
                        }
                    }
                    val responseEntity = TextContent(clientResponse.readText(), contentType = ContentType.Application.Json)
                    call.respond(clientResponse.status, responseEntity)
                } catch (cause : Throwable) {
                    logger.error("Unable to proxy request.", cause)
                    call.respond(HttpStatusCode.GatewayTimeout)
                }
            }
        }
    }

}

private fun ApplicationRequest.hasValidPath(): Boolean {
    val path = getPathWithoutLeadingSlashes()
    return path.isNotBlank()
}

private fun ApplicationRequest.getPathWithoutLeadingSlashes(): String {
    var path = path()
    while (path.startsWith("/")) {
        path = path.substring(1)
    }
    return path.toLowerCase()
}

private fun ApplicationRequest.firstPathSegment(): String {
    val path = getPathWithoutLeadingSlashes()
    return path.split("/")[0]
}

private fun ApplicationRequest.pathWithoutFirstPathSegment(): String {
    val path = getPathWithoutLeadingSlashes()
    val firstPathSegment = firstPathSegment()
    return path.substring(firstPathSegment.length)
}

private fun produceDestinationUrl(destinationPath: String, baseUrl: URL) : URL {
    return URLBuilder(baseUrl.toString())
        .trimmedPath(listOf(baseUrl.path, destinationPath))
        .build().toURI().toURL()
}

private fun URLBuilder.trimmedPath(pathParts : List<String>): URLBuilder  {
    val trimmedPathParts = mutableListOf<String>()
    pathParts.forEach { part ->
        if (part.isNotBlank()) {
            trimmedPathParts.add(part.trimStart('/').trimEnd('/'))
        }
    }
    return path(trimmedPathParts)
}

private fun ApplicationRequest.isMonitoringRequest() : Boolean {
    return monitoringPaths.contains(firstPathSegment())
}