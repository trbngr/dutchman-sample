package io.example

import akka.actor.{ActorSystem, Terminated}
import akka.stream.ActorMaterializer
import dutchman.AkkaHttpClient
import dutchman.api._
import dutchman.circe._
import dutchman.http._

import scala.concurrent.Future
import scala.io.StdIn
import scala.language.postfixOps

object Example extends App {

  import model.{Person, _}

  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = system.dispatcher
  implicit val es = AkkaHttpClient().bind(Endpoint.localhost)

  val index: Idx = "dutchman_sample_index"
  val tpe: Type = "person"

  /*=============== Index via Bulk ===============*/
  val bulkActions = (1 to 1000).map { i ⇒
    BulkAction(Index(index, tpe, Person(i, names.first, names.last)))
  }

  es.bulk(bulkActions: _*) flatMap { responses ⇒
    println(s"indexed ${responses.size} people")
    search
  } recover {
    case e: Throwable ⇒ println(s"somethings amiss: ${e.getMessage}")
  }

  def doSearch(input: String, page: Int = 0, totalFetched: Int = 0): Future[Nothing] = {

    /*=============== Bool Query Construction ===============*/
    //case class
    val firstName = Prefix("first", input)
    //or use the query interpolation
    val lastName = prefix"last:$input"

    val query = Bool(
      Should(firstName, lastName)
    )

    /*=============== Execute the Search API ===============*/
    val options = SearchOptions(size = Some(25), from = Some(page * 25))

    es.search(index, tpe, query, Some(options)) flatMap {
      case SearchResponse(_, total, documents) ⇒
        println("-----")
        val count = totalFetched + documents.size
        println(s"Found $total people, ${documents.size} documents returned, $count fetched so far.")

        documents.map(document ⇒ document.source.as[Person] → document.score) collect {
          case (Right(person), score) ⇒ person -> score
        } foreach println

        if (total == count) {
          println("-----")
          search
        } else {
          val answer = StdIn.readLine("fetch next page? [Y/n]: ")
          if (answer.toLowerCase() == "n") {
            search
          } else {
            val nextPage = page + 1
            doSearch(input, nextPage, count)
          }
        }
    }
  }

  def search: Future[Nothing] = {
    val input = StdIn.readLine("Enter a name to search: ").trim
    if (input == "exit") exit else {
      doSearch(input)
    }
  }

  def exit = {
    val response = for {
      r ← es.deleteIndex(index)
      t ← system.terminate()
    } yield (r, t)

    response map {
      case (d: DeleteIndexResponse, t: Terminated) ⇒
        println(s"Deleted index: ${d.acknowledged}")
        println(s"actor system terminated: ${t.actor}")
        sys.exit(0)
    }

  }
}
