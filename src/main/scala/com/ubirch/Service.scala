package com.ubirch

import java.io.{ File, PrintWriter }
import java.util.UUID

import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.Logger
import org.apache.commons.codec.binary.Hex
import org.apache.http.client.HttpClient
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.{ ByteArrayEntity, StringEntity }
import org.apache.http.impl.client.HttpClients
import org.apache.http.util.EntityUtils
import org.joda.time.{ DateTime, DateTimeZone }
import org.neo4j.driver.v1._

import scala.collection.JavaConverters._

object Service {

  @transient
  protected lazy val logger: Logger = Logger("com.ubirch.Key_Exporter")

  private val config = ConfigFactory.load()

  def main(args: Array[String]): Unit = {

    var driver: Driver = null
    var report: Report = null
    var client: HttpClient = null

    try {

      val neoUri = config.getString("neo4j.uri")
      val neoUser = config.getString("neo4j.username")
      val msgpackurl = config.getString("msgpackurl")
      val jsonurl = config.getString("jsonurl")
      val reportFile = config.getString("reportFile")

      report = new Report
      client = HttpClients.createMinimal()
      driver = GraphDatabase.driver(neoUri, AuthTokens.basic(neoUser, config.getString("neo4j.pass")))

      report.reportFile = reportFile
      report.neoserver = neoUri
      report.neouser = neoUser
      report.msgpackurl = msgpackurl
      report.jsonurl = jsonurl

      logger.info("msgpackurl={} jsonurl={}", report.msgpackurl, report.jsonurl)

      val session = driver.session()
      //Querying
      val data = session.readTransaction(new TransactionWork[List[PublicKey]] {
        override def execute(transaction: Transaction): List[PublicKey] = {

          val records = transaction.run(
            """MATCH (pubKey: PublicKey)
              |RETURN pubKey""".stripMargin
          ).list().asScala

          DbModelUtils.recordsToPublicKeys(records, "pubKey").toList

        }
      })

      //Grouping and filtering
      val grouped = data
        .groupBy(_.pubKeyInfo.hwDeviceId)
        .mapValues { vs =>
          vs.sortWith { (a, b) =>
            a.pubKeyInfo.created.isAfter(b.pubKeyInfo.created)
          }
        }.mapValues(_.headOption.getOrElse(throw new Exception("Device Found with no Key!")))

      val msgpacks = grouped.filter { case (_, b) => b.raw.isDefined }
      val jsons = grouped.filter { case (_, b) => b.raw.isEmpty }

      val msgpackPosts = msgpacks.map { case (_, publicKey) =>
        val request = new HttpPost(msgpackurl)
        request.setHeader("Content-Type", "application/octet-stream")
        request.setEntity(new ByteArrayEntity(Hex.decodeHex(publicKey.raw.get)))
        (publicKey, request)
      }

      report.dataFound = data.size
      report.dataFiltered = grouped.size
      report.msgpacks = msgpacks.size
      report.jsons = jsons.size

      logger.info("state={}, total_keys_found={} filtered_keys={} msg_packs={} jsons={}", "KeyTyping", report.dataFound, report.dataFiltered, report.msgpacks, report.jsons)

      // Posting msgpacks

      msgpackPosts.foreach { case (pk, r) =>
        try {
          val res = client.execute(r)
          val status = res.getStatusLine
          val resBody = EntityUtils.toString(res.getEntity)
          report.msgpacksProcessed += ((status.getStatusCode.toString, pk.pubKeyInfo.hwDeviceId, resBody, pk.raw.get))
          if (status.getStatusCode > 200)
            report.msgpacksProcessedNotOK = report.msgpacksProcessedNotOK + 1
          else
            report.msgpacksProcessedOK = report.msgpacksProcessedOK + 1

          logger.info("status={} hardwareId={} response_body={} body={}", status, pk.pubKeyInfo.hwDeviceId, resBody, pk.raw.get)
        } catch {
          case e: Exception =>
            report.msgpacksProcessed += (("-", pk.pubKeyInfo.hwDeviceId, e.getMessage, pk.raw.get))
            report.msgpacksProcessedNotOK = report.msgpacksProcessedNotOK + 1
            logger.error("hardwareId={} ex={}", pk.pubKeyInfo.hwDeviceId, e.getMessage)
        }
      }

      /// Posting json

      val jsonPosts = jsons.map { case (_, publicKey) =>
        val request = new HttpPost(jsonurl)
        request.setHeader("Content-Type", "application/json")
        val body = stringify(toJValue(publicKey))
        request.setEntity(new StringEntity(body))
        (publicKey, body, request)
      }

      jsonPosts.foreach { case (pk, body, r) =>
        try {
          val res = client.execute(r)
          val status = res.getStatusLine
          val resBody = EntityUtils.toString(res.getEntity)
          report.jsonsProcessed += ((status.getStatusCode.toString, pk.pubKeyInfo.hwDeviceId, resBody, body))
          if (status.getStatusCode > 200)
            report.jsonsProcessedNotOK = report.jsonsProcessedNotOK + 1
          else
            report.jsonsProcessedOK = report.jsonsProcessedOK + 1

          logger.info("status={} hardwareId={} response_body={} body={}", status, pk.pubKeyInfo.hwDeviceId, resBody, body)
        } catch {
          case e: Exception =>
            report.jsonsProcessed += (("-", pk.pubKeyInfo.hwDeviceId, e.getMessage, body))
            report.jsonsProcessedOK = report.jsonsProcessedNotOK + 1
            logger.error("hardwareId={} ex={}", pk.pubKeyInfo.hwDeviceId, e.getMessage)
        }
      }

    } catch {
      case e: Exception =>
        logger.error("Error while setting up neo4j connection", e)
        throw e
    } finally {
      if (report != null) {
        val date = DateTime.now(DateTimeZone.UTC)
        val id = UUID.randomUUID()
        val resultsFile = report.reportFile + "_" + id + "_" + date.toString() + ".results.csv"
        logger.info("Creating report:" + resultsFile)
        val pw = new PrintWriter(new File(resultsFile))
        pw.write(report.dataProcessed)
        pw.close()

        val executiveFile = report.reportFile + "_" + id + "_" + date.toString() + ".summary.csv"
        logger.info("Creating report:" + executiveFile)
        val pwe = new PrintWriter(new File(executiveFile))
        pwe.write(report.executive)
        pwe.close()
      }
      if (driver != null) {
        logger.info("Closing connection")
        driver.close()
      }
      logger.info("Process Finished")
    }

  }

}
