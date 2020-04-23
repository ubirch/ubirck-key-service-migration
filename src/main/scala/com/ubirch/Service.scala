package com.ubirch

import java.io.{File, PrintWriter}

import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.Logger
import com.ubirch.Service.config
import org.apache.commons.codec.binary.Hex
import org.apache.http.client.HttpClient
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.{ByteArrayEntity, StringEntity}
import org.apache.http.impl.client.HttpClients
import org.apache.http.util.EntityUtils
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

  var report: Report = _

  def main(args: Array[String]): Unit = {

    try {

      report = new Report

      logger.info("state={}", "Connecting")

      val neoUri = config.getString("neo4j.uri")
      val neoUser = config.getString("neo4j.username")

      driver = GraphDatabase.driver(neoUri, AuthTokens.basic(neoUser, config.getString("neo4j.pass")))

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
        }.mapValues(_.headOption.getOrElse(throw new Exception("Device Found with no Key!")))

      val msgpacks = grouped.filter{ case (_, b) => b.raw.isDefined }
      val jsons = grouped.filter { case (_, b) => b.raw.isEmpty }

      val client: HttpClient = HttpClients.createMinimal()

      val msgpackurl = config.getString("msgpackurl")
      val jsonurl = config.getString("jsonurl")

      logger.info("msgpackurl={} jsonurl={}", msgpackurl, jsonurl)

      val msgpackPosts = msgpacks.map { case (_, publicKey) =>
        val request = new HttpPost(msgpackurl)
        request.setHeader("Content-Type", "application/octet-stream")
        request.setEntity(new ByteArrayEntity(Hex.decodeHex(publicKey.raw.get)))
        (publicKey, request)
      }

      report.neoserver = neoUri
      report.neouser = neoUser

      report.dataFound = data.size
      report.dataFiltered = grouped.size
      report.msgpacks = msgpacks.size
      report.jsons = jsons.size

      logger.info("state={}, \n total_keys_found={} \n filtered_keys={} \n msg_packs={} \n jsons={}", "KeyTyping", report.dataFound, report.dataFiltered, report.msgpacks, report.jsons)

      logger.info("state={}", "Creating")

      msgpackPosts.foreach { case (pk, r) =>
        try {
          val res = client.execute(r)
          val status = res.getStatusLine
          val body = EntityUtils.toString(res.getEntity)
          report.msgpacksProcessed += ((status.getStatusCode.toString, pk.pubKeyInfo.hwDeviceId, body))
          if(status.getStatusCode > 200)
            report.msgpacksProcessedNotOK = report.msgpacksProcessedNotOK + 1
          else
            report.msgpacksProcessedOK = report.msgpacksProcessedOK + 1
          logger.info("\n status={} \n hardwareId={} \n body={}", status, pk.pubKeyInfo.hwDeviceId, body)
        } catch {
          case e: Exception =>
            report.msgpacksProcessed += (("-", pk.pubKeyInfo.hwDeviceId, e.getMessage))
            report.msgpacksProcessedNotOK = report.msgpacksProcessedNotOK + 1
            logger.error("hardwareId={} ex={}", pk.pubKeyInfo.hwDeviceId, e.getMessage)
        }
      }

      ///json
      val jsonPosts = jsons.map { case (_, publicKey) =>
        val request = new HttpPost(jsonurl)
        request.setHeader("Content-Type", "application/json")
        val body = stringify(toJValue(publicKey))
        request.setEntity(new StringEntity(body))
        (publicKey, request)
      }

      jsonPosts.foreach { case (pk, r) =>
        try {
          val res = client.execute(r)
          val status = res.getStatusLine
          val body = EntityUtils.toString(res.getEntity)
          report.jsonsProcessed += ((status.getStatusCode.toString, pk.pubKeyInfo.hwDeviceId, body))
          if(status.getStatusCode > 200)
            report.jsonsProcessedNotOK = report.jsonsProcessedNotOK + 1
          else
            report.jsonsProcessedOK = report.jsonsProcessedOK + 1
          logger.info("\n status={} \n hardwareId={} \n body={}", status, pk.pubKeyInfo.hwDeviceId, body)
        } catch {
          case e: Exception =>
            report.jsonsProcessed += (("-", pk.pubKeyInfo.hwDeviceId, e.getMessage))
            report.jsonsProcessedOK = report.jsonsProcessedNotOK + 1
            logger.error("hardwareId={} ex={}", pk.pubKeyInfo.hwDeviceId, e.getMessage)
        }
      }

    } catch {
      case e: Exception =>
        logger.error("Error while setting up neo4j connection", e)
        throw e
    } finally {
      if (driver != null) driver.close()
      if (report != null) {
        val pw = new PrintWriter(new File("hello.html" ))
        pw.write(report.all.toString())
        pw.close
      }
    }

  }

}
