package io.example

import akka.actor.{ActorSystem, Terminated}
import akka.stream.ActorMaterializer
import dutchman.AkkaHttpClient
import dutchman.circe._
import dutchman.dsl._
import dutchman._
import dutchman.ops._
import io.circe.Json

import scala.concurrent.Future
import scala.io.StdIn
import scala.language.postfixOps

object Example extends App with EmbeddedServer {

  import model.{Person, _}

  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = system.dispatcher
  implicit val elasticsearch = AkkaHttpClient().bind(endpoint)

  val index: Idx = "dutchman_sample_index"
  val tpe: Type = "person"

  /*=============== Index via Bulk ===============*/
  val bulkActions = (1 to 1000).map { i ⇒
    val document = Person(i, names.first, names.last)
    BulkAction(Index(index, tpe, document, None))
  }

  val indexAndRefresh = for {
    r ← bulk(bulkActions: _*)
    _ ← refresh(index)
  } yield r

  elasticsearch(indexAndRefresh) map {
    case Left(e) ⇒
      println(s"something's amiss: ${e.reason}")
      exit

    case Right(responses) ⇒
      println(s"indexed ${responses.size} people")
      println(s"running at endpoint: $endpoint")
      println(s"data directory: $dataDirectory")
      askForName
  }

  def askForName: Future[Nothing] = {
    val input = StdIn.readLine("Enter a name to search or 'exit' to stop: ").trim
    if (input == "exit") exit else {
      doSearch(input)
    }
  }

  def exit: Future[Nothing] = {
    val response = for {
      r ← elasticsearch(deleteIndex(index))
      _ ← shutDownServer()
      t ← system.terminate()
    } yield (r, t)

    response map {
      case (result: Result[DeleteIndexResponse], t: Terminated) ⇒
        println(s"Deleted index: $result")
        println(s"actor system terminated: ${t.actor}")
        sys.exit(0)
    }

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

    val program = for {
      r ← search[Json](index, tpe, query, Some(options))
    } yield r

    elasticsearch(program) flatMap {
      case Left(error)         ⇒
        println(s"Error: $error")
        exit
      case Right(response) ⇒
        val documents = response.documents
        val total = response.total
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
}
