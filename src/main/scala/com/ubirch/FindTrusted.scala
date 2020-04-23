package com.ubirch

import org.joda.time.DateTime

case class FindTrusted(
    curveAlgorithm: String,
    depth: Int = 1,
    minTrustLevel: Int = 50,
    queryDate: DateTime,
    sourcePublicKey: String
)

case class FindTrustedSigned(
    findTrusted: FindTrusted,
    signature: String
)

case class TrustedKeyResult(
    depth: Int,
    trustLevel: Int,
    publicKey: PublicKey
)
