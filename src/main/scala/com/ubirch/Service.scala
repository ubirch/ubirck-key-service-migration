package com.ubirch

import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.Logger
import org.neo4j.driver.v1._

import scala.collection.JavaConverters._

object Service extends AutoCloseable {

  @transient
  protected lazy val logger: Logger = Logger("com.ubirch.Key_Exporter")

  val config = ConfigFactory.load()

  private var driver: Driver = _

  override def close(): Unit = {
    logger.info("Closing connection")
    if (driver != null) driver.close()
  }

  def main(args: Array[String]): Unit = {

    try {

      logger.info("state={}", "Connecting")

      driver = GraphDatabase.driver(
        config.getString("neo4j.uri"),
        AuthTokens.basic(config.getString("neo4j.username"), config.getString("neo4j.pass"))
      )

      val session = driver.session()

      logger.info("state={}", "Querying")

      val data = session.readTransaction(new TransactionWork[List[PublicKey]] {
        override def execute(transaction: Transaction): List[PublicKey] = {

          val records = transaction.run(
            """MATCH (pubKey: PublicKey)
              |RETURN pubKey""".stripMargin
          ).list().asScala

          DbModelUtils.recordsToPublicKeys(records, "pubKey").toList

        }
      })

      logger.info("state={}", "Grouping")

      val grouped = data
        .groupBy(_.pubKeyInfo.hwDeviceId)
        .mapValues { vs =>
          vs.sortWith { (a, b) =>
            a.pubKeyInfo.created.isAfter(b.pubKeyInfo.created)
          }
        }.mapValues(_.headOption)

      logger.info("state={}, total_keys_found={} filtered_keys={}", "After grouping", data.size, grouped.size)

    } catch {
      case e: Exception =>
        logger.error("error while setting up neo4j connection", e)
        throw e
    } finally {
      if (driver != null) driver.close()
    }

  }

}
