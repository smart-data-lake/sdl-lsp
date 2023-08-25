package io.smartdatalake.completion.schema

import io.smartdatalake.UnitSpec
import io.smartdatalake.schema.{ItemType, SchemaItem, SchemaReaderImpl}
import ujson.*

import scala.io.Source
import scala.util.Using

class SchemaReaderSpec extends UnitSpec {
  
  "Schema Reader" should "retrieve all the properties of copyAction" in {
    val actual = schemaReader.retrieveActionProperties("CopyAction").toList
    val expected = List(
      SchemaItem("type", ItemType.STRING, """""", true),
      SchemaItem("inputId", ItemType.STRING, """inputs DataObject""", true),
      SchemaItem("outputId", ItemType.STRING, """output DataObject""", true),
      SchemaItem("deleteDataAfterRead", ItemType.BOOLEAN, """a flag to enable deletion of input partitions after copying.""", false),
      SchemaItem("transformer", ItemType.OBJECT,
        """Configuration of a custom Spark-DataFrame transformation between one input and one output (1:1)
      |Define a transform function which receives a DataObjectIds, a DataFrames and a map of options and has to return a
      |DataFrame, see also[[CustomDfTransformer]].
      |
      |Note about Python transformation: Environment with Python and PySpark needed.
      |PySpark session is initialize and available under variables`sc`,`session`,`sqlContext`.
      |Other variables available are
      |-`inputDf`: Input DataFrame
      |-`options`: Transformation options as Map[String,String]
      |-`dataObjectId`: Id of input dataObject as String
      |Output DataFrame must be set with`setOutputDf(df)` .""".stripMargin, false),
      SchemaItem("transformers", ItemType.ARRAY,
        """optional list of transformations to apply. See[[spark.transformer]] for a list of included Transformers.
      |The transformations are applied according to the lists ordering.""".stripMargin, false),
      SchemaItem("breakDataFrameLineage", ItemType.BOOLEAN,
        """Stop propagating input DataFrame through action and instead get a new DataFrame from DataObject.
      |This can help to save memory and performance if the input DataFrame includes many transformations from previous Actions.
      |The new DataFrame will be initialized according to the SubFeed\'s partitionValues.""".stripMargin, false),
      SchemaItem("persist", ItemType.BOOLEAN,
        """Force persisting input DataFrame\'s on Disk.
      |This improves performance if dataFrame is used multiple times in the transformation and can serve as a recovery point
      |in case a task get\'s lost.
      |Note that DataFrames are persisted automatically by the previous Action if later Actions need the same data. To avoid this
      |behaviour set breakDataFrameLineage=false.""".stripMargin, false),
      SchemaItem("executionMode", ItemType.STRING, """optional execution mode for this Action""", false),
      SchemaItem("executionCondition", ItemType.OBJECT,
        """Definition of a Spark SQL condition with description.
      |This is used for example to define failConditions of[[PartitionDiffMode]] .""".stripMargin, false),
      SchemaItem("metricsFailCondition", ItemType.STRING,
        """optional spark sql expression evaluated as where-clause against dataframe of metrics. Available columns are dataObjectId, key, value.
      |If there are any rows passing the where clause, a MetricCheckFailed exception is thrown.""".stripMargin, false),
      SchemaItem("saveModeOptions", ItemType.STRING, """override and parametrize saveMode set in output DataObject configurations when writing to DataObjects.""", false),
      SchemaItem("metadata", ItemType.STRING, """""", false),
      SchemaItem("agentId", ItemType.STRING, """""", false)
    )

    actual shouldBe expected

  }

  it should "retrieve some action property description" in {
    val inputIdDescriptionOfCopyAction = schemaReader.retrieveActionPropertyDescription("CopyAction", "inputId")
    inputIdDescriptionOfCopyAction shouldBe "inputs DataObject"

    val overwriteDescriptionOfFileTransferAction = schemaReader.retrieveActionPropertyDescription("FileTransferAction", "overwrite")
    overwriteDescriptionOfFileTransferAction shouldBe "Allow existing output file to be overwritten. If false the action will fail if a file to be created already exists. Default is true."
  }


}