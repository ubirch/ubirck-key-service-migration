package com.ubirch

import org.joda.time.DateTime

/**
  * author: cvandrei
  * since: 2018-09-17
  */
case class SignedRevoke(
    revokation: Revokation,
    signature: String
)

// fields should be ordered alphabetically as some client libs only produce JSON with alphabetically ordered fields!!!
case class Revokation(
    curveAlgorithm: String,
    publicKey: String,
    revokationDate: DateTime
)
