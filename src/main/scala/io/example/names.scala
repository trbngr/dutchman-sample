package io.example

import scala.io.Source
import scala.util.Random

object names {
  private val names: Seq[String] = Source.fromInputStream(getClass.getResourceAsStream("/names.txt")).mkString.split("\n").map(_.trim).toSet.toSeq

  private def random = {
    val line = names(Random.nextInt(names.size))
    val parts = line.split(" ")
    (parts(0), parts(1))
  }

  def first = random._1
  def last = random._2
}
