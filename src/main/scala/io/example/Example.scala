package io.example

import akka.actor.{ActorSystem, Terminated}
import akka.stream.ActorMaterializer
import dutchman.AkkaHttpClient
import dutchman.dsl._
import dutchman.ops._
import dutchman.circe._
import dutchman.http._
import io.circe.Json

import scala.concurrent.Future
import scala.io.StdIn
import scala.language.postfixOps

object Example extends App {

  import model.{Person, _}

  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = system.dispatcher
  implicit val elasticsearch = AkkaHttpClient().bind(Endpoint.localhost)

  val index: Idx = "dutchman_sample_index"
  val tpe: Type = "person"

  /*=============== Index via Bulk ===============*/
  val bulkActions = (1 to 100).map { i ⇒
    val document = Person(i, names.first, names.last)
    BulkAction(Index(index, tpe, document, None))
  }

  val indexAndRefresh = for {
    r ← bulk(bulkActions: _*)
    _ ← refresh(index)
  } yield r

  elasticsearch(indexAndRefresh) flatMap { responses ⇒
    println(s"indexed ${responses.size} people")
    askForName
  } recover {
    case e: Throwable ⇒ println(s"something's amiss: ${e.getMessage}")
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

    elasticsearch(search[Json](index, tpe, query, Some(options))) flatMap {
      case SearchResponse(_, total, documents) ⇒
        println("-----")
        val count = totalFetched + documents.size
        println(s"Found $total people, ${documents.size} documents returned, $count fetched so far.")

        documents.map(document ⇒ document.source.as[Person] → document.score) collect {
          case (Right(person), score) ⇒ person -> score
        } foreach println

        if (total == count) {
          println("-----")
          askForName
        } else {
          val answer = StdIn.readLine("fetch next page? [Y/n]: ")
          if (answer.toLowerCase() == "n") {
            askForName
          } else {
            val nextPage = page + 1
            doSearch(input, nextPage, count)
          }
        }
    }
  }

  def askForName: Future[Nothing] = {
    val input = StdIn.readLine("Enter a name to search: ").trim
    if (input == "exit") exit else {
      doSearch(input)
    }
  }

  def exit: Future[Nothing] = {
    val response = for {
      r ← elasticsearch(deleteIndex(index))
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
