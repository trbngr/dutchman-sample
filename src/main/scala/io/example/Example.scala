package io.example

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import dutchman.{AkkaHttpClient, Endpoint}
import dutchman.api._
import dutchman.dsl._
import dutchman.circe._

import scala.concurrent.Future
import scala.io.StdIn
import scala.language.postfixOps

object Example extends App {

  import model.{Person, _}

  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = system.dispatcher
  implicit val dsl = AkkaHttpClient().bind(Endpoint.localhost)

  val index: Idx = "dutchman_sample_index"
  val tpe: Type = "person"

  /*=============== Index via Bulk ===============*/
  val bulkActions = (1 to 1000).map { i ⇒
    Bulk(Index(index, tpe, Person(i, names.first, names.last)))
  }

  Bulk(bulkActions: _*) flatMap { responses ⇒
    println(s"indexed ${responses.size} people")
    search
  } recover {
    case e: Throwable ⇒ println(s"somethings amiss: ${e.getMessage}")
  }

  def search: Future[Nothing] = {
    val input = StdIn.readLine("Enter a name to search: ").trim
    if (input == "exit") exit else {
      /*=============== Bool Query Construction ===============*/
      val query = Bool(
        Should(Prefix("first", input), Prefix("last", input))
      )

      /*=============== Execute the Search API ===============*/
      val options = SearchOptions(size = Some(25))

      Search(index, tpe, query).withOptions(options) flatMap {
        case SearchResponse(_, total, documents) ⇒
          println("-----")
          println(s"Found $total people, ${documents.size} documents returned")

          documents.map(document ⇒ document.source.as[Person] → document.score) collect {
            case (Right(person), score) ⇒ person -> score
          } foreach println

          println("-----")
          search
      }
    }
  }

  def exit = {
    val response = for {
      r ← DeleteIndex(index)
      _ ← system.terminate()
    } yield r

    response map { d ⇒
      println(s"Deleted index: ${d.acknowledged}")
      println(s"actor system terminated")
      sys.exit(0)
    }

  }
}
