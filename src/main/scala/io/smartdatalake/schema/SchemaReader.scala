package io.smartdatalake.schema

import io.smartdatalake.context.SDLBContext
import io.smartdatalake.schema.SchemaCollections.{AttributeCollection, TemplateCollection}

trait SchemaReader:
  def retrieveAttributeOrTemplateCollection(context: SDLBContext): AttributeCollection | TemplateCollection
  
  def retrieveDescription(context: SDLBContext): String
