package com.ubirch

import com.typesafe.scalalogging.LazyLogging
import org.neo4j.driver.v1.{ Record, Value }

import scala.language.postfixOps

/**
  * author: cvandrei
  * since: 2018-09-14
  */
object DbModelUtils extends LazyLogging {

  def recordsToPublicKeys(records: Seq[Record], recordLabel: String): Seq[PublicKey] = {

    records map { record =>

      val pubKey = record.get(recordLabel)
      parsePublicKeyFromRecord(pubKey)

    }

  }

  private def parsePublicKeyFromRecord(pubKey: Value) = {

    val pubKeyInfo = PublicKeyInfo(
      hwDeviceId = Neo4jParseUtil.asType[String](pubKey, "infoHwDeviceId"),
      pubKey = Neo4jParseUtil.asType[String](pubKey, "infoPubKey"),
      pubKeyId = Neo4jParseUtil.asTypeOrDefault[String](pubKey, "infoPubKeyId", "--UNDEFINED--"),
      algorithm = Neo4jParseUtil.asTypeOrDefault[String](pubKey, "infoAlgorithm", "ed25519-sha-512"),
      previousPubKeyId = Neo4jParseUtil.asTypeOption[String](pubKey, "infoPreviousPubKeyId"),
      created = Neo4jParseUtil.asDateTime(pubKey, "infoCreated"),
      validNotBefore = Neo4jParseUtil.asDateTime(pubKey, "infoValidNotBefore"),
      validNotAfter = Neo4jParseUtil.asDateTimeOption(pubKey, "infoValidNotAfter")
    )

    PublicKey(
      pubKeyInfo = pubKeyInfo,
      signature = Neo4jParseUtil.asType(pubKey, "signature"),
      previousPubKeySignature = Neo4jParseUtil.asTypeOption[String](pubKey, "previousPubKeySignature"),
      raw = Neo4jParseUtil.asTypeOption[String](pubKey, "raw"),
      signedRevoke = signedRevokeFromPublicKey(pubKey)
    )

  }

  private def signedRevokeFromPublicKey(pubKey: Value): Option[SignedRevoke] = {

    val revokeSignature = Neo4jParseUtil.asTypeOrDefault[String](pubKey, "revokeSignature", "--UNDEFINED--")
    val revokePublicKey = Neo4jParseUtil.asTypeOrDefault[String](pubKey, "revokePublicKey", "--UNDEFINED--")
    val revokeDate = Neo4jParseUtil.asDateTimeOption(pubKey, "revokeDateTime")
    val revokeAlgorithm = Neo4jParseUtil.asTypeOrDefault[String](pubKey, "revokeCurveAlgorithm", "ed25519-sha-512")
    if (revokeSignature == "--UNDEFINED--" && revokePublicKey == "--UNDEFINED--" && revokeDate.isEmpty) {

      None

    } else if (revokeSignature != "--UNDEFINED--" && revokePublicKey != "--UNDEFINED--" && revokeDate.isDefined) {

      Some(
        SignedRevoke(
          revokation = Revokation(publicKey = revokePublicKey, revokationDate = revokeDate.get, curveAlgorithm = revokeAlgorithm),
          signature = revokeSignature
        )
      )

    } else {

      logger.error(s"public key record in database seems to be invalid: signedRevoke.signature=$revokeSignature, signedRevoke.revokation.publicKey=$revokePublicKey, signedRevoke.revokation.revokationDate=$revokeDate")
      None

    }

  }

}
