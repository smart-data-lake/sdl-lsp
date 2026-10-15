package io.smartdatalake.completion.ai.model

import scala.concurrent.Future

trait ModelClient:
    def isEnabled: Boolean
    /** @param model model name, None to use the default model of the client */
    def completeAsync(prompt: String, model: Option[String] = None): Future[String]
