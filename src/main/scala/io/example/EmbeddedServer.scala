package io.example

import java.io.File

import dutchman.http.{Endpoint, Http}
import org.elasticsearch.client.Client
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.node.NodeBuilder

import scala.concurrent.Future

trait EmbeddedServer {

  import EmbeddedServer._

  def endpoint: Endpoint = embeddedEndpoint
  def dataDirectory: File = home

  def shutDownServer(): Future[Unit] = Future.successful {
    println(s"shutting down ES client.")
    deleteDataDirectory()
    client.close()
  }

  def deleteDataDirectory(): Boolean = {
    def getFiles(file: File): List[File] = Option(file.listFiles()).getOrElse(Array.empty).toList

    @annotation.tailrec
    def loop(files: List[File]): Boolean = files match {
      case (Nil) ⇒ true

      case (head :: tail) if head.isDirectory && head.listFiles().nonEmpty ⇒
        loop(getFiles(head) ++ tail ++ List(head))

      case (head :: tail) ⇒
        head.delete()
        loop(tail)

    }

    if (!home.exists()) false else loop(getFiles(home) ++ List(home))
  }
}

object EmbeddedServer {

  private val home = tempDir
  //  private lazy val client = node.client()
  private lazy val embeddedEndpoint = {
    val nodes = client.admin().cluster().prepareNodesInfo().clear().setSettings(true).setHttp(true).get()
    val address = nodes.getNodes.map(_.getHttp.address()).head.publishAddress()
    Endpoint(address.getHost, address.getPort, Http)
  }

  private lazy val node = NodeBuilder.nodeBuilder()
    .local(true)
    .settings(Settings.settingsBuilder()
      .put("path.home", home)
      .build()
    ).node()
  lazy val client: Client = node.client()

  private def tempDir = {
    val name = "dutchman-sample"
    val file = File.createTempFile(name, f"${System.currentTimeMillis()}")
    val dir = new File(f"${file.getParentFile.getAbsolutePath}${File.separator}$name-${System.currentTimeMillis()}")
    dir.mkdir()
    file.delete()
    dir
  }
}
