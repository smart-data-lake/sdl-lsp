package io.smartdatalake.formatting

import io.smartdatalake.client.ClientType

object FormattingStrategyFactory:
  def createFormattingStrategy(clientType: ClientType): FormattingStrategy = clientType match
    case ClientType.IntelliJ => new IntelliJFormattingStrategy()
    case ClientType.VSCode => new FormattingStrategy{}
    case ClientType.Unknown => new FormattingStrategy{}


