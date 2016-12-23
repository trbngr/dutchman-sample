package io.example

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.linktargeting.elasticsearch.AkkaHttpClient
import com.linktargeting.elasticsearch.api._
import com.linktargeting.elasticsearch.dsl._
import com.linktargeting.elasticsearch.http.Endpoint
import com.linktargeting.elasticsearch.http.circe._

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.io.StdIn
import scala.language.postfixOps
import scala.util.Success

object Example extends App {

  import model.{Person, _}

  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = system.dispatcher

  val httpClient = AkkaHttpClient()
  implicit val dsl = httpClient.bind(Endpoint.localhost)

  val index: Idx = "people"
  val tpe: Type = "person"

  /*=============== Index via Bulk ===============*/
  val bulkActions = (1 to 200).map { i ⇒
    val (first, last) = names.random
    Bulk(Index(index, tpe, Person(i, first, last)))
  }

  Bulk(bulkActions: _*) map { responses ⇒
    responses foreach { case BulkResponse(action, status, _) ⇒ println(s"$action: $status") }
  } onComplete {
    case Success(_) ⇒
      /*=============== User Input Loop ===============*/
      while (true) {
        val input = StdIn.readLine("Enter a name to search: ").trim
        if (input == "exit") exit else Await.ready(search(input), 1 second)
      }
    case _          ⇒ println("somethings amiss")
  }

  private def search(input: String) = {

    /*=============== Bool Query Construction ===============*/
    val query = Bool(
      Should(Prefix("first", input), Prefix("last", input))
    )

    /*=============== Execute the Search API ===============*/
    Search(index, tpe, query) map {
      case SearchResponse(_, total, documents) ⇒
        println("-----")
        println(s"Found $total people")

        documents.map(document ⇒ document.source.as[Person] → document.score) collect {
          case (Right(person), score) ⇒ person -> score
        } foreach println

        println("-----")
    }
  }

  def exit = DeleteIndex(index) map { r ⇒
    system.terminate() map {
      println(s"Deleted index: ${r.acknowledged}")
      sys.exit(0)
    }
  }
}
