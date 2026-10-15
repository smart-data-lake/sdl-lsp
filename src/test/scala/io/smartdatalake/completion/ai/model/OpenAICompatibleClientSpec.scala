package io.smartdatalake.completion.ai.model

import com.sun.net.httpserver.HttpServer
import io.smartdatalake.UnitSpec

import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import scala.concurrent.Await
import scala.concurrent.duration.*

class OpenAICompatibleClientSpec extends UnitSpec:

    private case class ReceivedRequest(path: String, authorization: Option[String], body: String)

    /** Starts a local chat completions endpoint answering with the given content, runs the test and stops it */
    private def withServer(content: String)(test: (String, () => Option[ReceivedRequest]) => Unit): Unit =
        @volatile var received: Option[ReceivedRequest] = None
        val server = HttpServer.create(new InetSocketAddress("localhost", 0), 0)
        server.createContext("/", exchange =>
            val body = String(exchange.getRequestBody.readAllBytes(), StandardCharsets.UTF_8)
            received = Some(ReceivedRequest(exchange.getRequestURI.getPath, Option(exchange.getRequestHeaders.getFirst("Authorization")), body))
            val escaped = content.replace("\\", "\\\\").replace("\"", "\\\"")
            val response = s"""{"id":"1","object":"chat.completion","choices":[{"index":0,"message":{"role":"assistant","content":"$escaped"},"finish_reason":"stop"}]}"""
                .getBytes(StandardCharsets.UTF_8)
            exchange.sendResponseHeaders(200, response.length)
            exchange.getResponseBody.write(response)
            exchange.close()
        )
        server.start()
        try test(s"http://localhost:${server.getAddress.getPort}/v1/", () => received)
        finally server.stop(0)

    "OpenAI compatible client" should "be disabled without API key nor base URL" in {
        new OpenAICompatibleClient(None).isEnabled shouldBe false
    }

    it should "use Gemini by default and be enabled with an API key" in {
        val client = new OpenAICompatibleClient(Some("key"))
        client.isEnabled shouldBe true
        client.baseUrl shouldBe OpenAICompatibleClient.DEFAULT_BASE_URL
    }

    it should "send a chat completion request with bearer token and model" in withServer("[]") { (baseUrl, received) =>
        val client = new OpenAICompatibleClient(Some("secret-key"))
        client.configure(Some(baseUrl))
        Await.result(client.completeAsync("my prompt", Some("my-model")), 5.seconds) shouldBe "[]"
        val request = received().value
        request.path shouldBe "/v1/chat/completions"
        request.authorization shouldBe Some("Bearer secret-key")
        request.body should include ("\"model\":\"my-model\"")
        request.body should include ("\"content\":\"my prompt\"")
    }

    it should "work without API key when a base URL is configured, e.g. a local model" in withServer("hello") { (baseUrl, received) =>
        val client = new OpenAICompatibleClient(None)
        client.configure(Some(baseUrl))
        client.isEnabled shouldBe true
        Await.result(client.completeAsync("prompt"), 5.seconds) shouldBe "hello"
        received().value.authorization shouldBe None
        received().value.body should include (s"\"model\":\"${OpenAICompatibleClient.DEFAULT_MODEL}\"")
    }

    it should "return an empty completion if the endpoint is not reachable" in {
        val client = new OpenAICompatibleClient(Some("key"))
        client.configure(Some("http://localhost:1/v1"))
        Await.result(client.completeAsync("prompt"), 10.seconds) shouldBe ""
    }
