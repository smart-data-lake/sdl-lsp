package io.smartdatalake.completion.ai.model

import scala.concurrent.Future

trait ModelClient:
    def isEnabled: Boolean
    def completeAsync(prompt: String, model: String = ""): Future[String]
