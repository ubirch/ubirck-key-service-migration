package com.ubirch

import scalatags.Text.all.{h2, _}


class Report {

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

  def all = {
    html(
      body(
        h1("Key Migration"),
        h2("Neo server=" +  neoserver),
        h2("Neo user=" + neouser),
        h2("Key server (msgpack) =" + msgpackurl),
        h2("Key server (json) =" + jsonurl),
        table(
          tr(th("data found"), th("filtered keys"), th("msgpacks"), th("jsons"), th("msgpacksProcessedOK"), th("msgpacksProcessedNotOK"), th("jsonsProcessedOK"), th("jsonsProcessedNotOK")),
          tr(td(dataFound), td(dataFiltered), td(msgpacks), td(jsons), th(msgpacksProcessedOK), th(msgpacksProcessedNotOK), th(jsonsProcessedOK), th(jsonsProcessedNotOK))
        ),
        table(
          caption("msgpacks"),
          tr(th("status"), th("hardwareid"), th("response"), th("sent body")),
          msgpacksProcessed.map{ case (status, hardwareId, response, body) => tr(td(status), td(hardwareId), td(response), td(body)) }
        ),
        table(
          caption("json"),
          tr(th("status"), th("hardwareid"), th("response"), th("sent body")),
          jsonsProcessed.map{ case (status, hardwareId, response, body) => tr(td(status), td(hardwareId), td(response), td(body)) }
        )
      )
    )
  }

}
