package io.smartdatalake.client

trait ClientAware:
  var clientType: ClientType = ClientType.Unknown
