package com.ubirch

class Report {

  var reportFile = ""
  var neoserver = ""
  var neouser = ""

  var msgpackurl = ""
  var jsonurl = ""

  var msgpacks = 0
  var jsons = 0
  var dataFound = 0
  var dataFiltered = 0

  val msgpacksProcessed = scala.collection.mutable.ListBuffer.empty[(String, String, String, String)]
  var msgpacksProcessedOK = 0
  var msgpacksProcessedNotOK = 0

  val jsonsProcessed = scala.collection.mutable.ListBuffer.empty[(String, String, String, String)]
  var jsonsProcessedOK = 0
  var jsonsProcessedNotOK = 0

  val ret = "\n"
  val sep = ";"

  def executive = {

    val titleData = List("neo server", "neo user", "msgpack url", "msgpack url", "data found", "filtered keys", "msgpacks", "jsons", "msgpacks Processed OK", "msgpacks Processed Not OK", "jsons Processed OK", "jsons Processed Not OK").mkString(sep) + ret
    val data = List(neoserver, neouser, msgpackurl, jsonurl, dataFound, dataFiltered, msgpacks, jsons, msgpacksProcessedOK, msgpacksProcessedNotOK, jsonsProcessedOK, jsonsProcessedNotOK).mkString(sep) + ret
    titleData + data

  }

  def dataProcessed = {
    val titleResults = List("status", "hardwareId", "response", "sent body").mkString(sep) + ret
    val mgs = msgpacksProcessed.map { case (status, hardwareId, response, body) => List(status, hardwareId, response, body).mkString(sep) }.mkString(ret)
    val jss = jsonsProcessed.map { case (status, hardwareId, response, body) => List(status, hardwareId, response, body).mkString(sep) }.mkString(ret)
    titleResults + mgs + jss
  }

}
