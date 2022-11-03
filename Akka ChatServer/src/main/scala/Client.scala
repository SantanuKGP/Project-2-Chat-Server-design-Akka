import akka.actor.typed.{ActorSystem, Behavior}
import akka.actor.typed.scaladsl.Behaviors

import java.io.{BufferedReader, DataInputStream, DataOutputStream, IOException, InputStreamReader}
import java.net.{Socket, UnknownHostException}
import scala.io.StdIn.readLine


class Client (var user:String,address : String, port : Int) {

  trait ClientProtocol
  case object ReadMessage extends ClientProtocol
  case object WriteMessage extends ClientProtocol
  trait ClientAdminProtocol

  case object Start extends ClientAdminProtocol

  private var socket: Socket = _
  private var input: DataInputStream = _
  private var output: DataOutputStream = _

  private val client = ActorSystem(ClientAdmin(user), s"Client-$user")
  var flag = true

  def init():Unit={
    try {
      socket = new Socket(address, port)
      println("Connected")
      input = new DataInputStream(socket.getInputStream)
      output = new DataOutputStream(socket.getOutputStream)

      output.writeUTF(user)
      var isUniqueUsername = input.readUTF()
      while(isUniqueUsername!="Ok"){
        println(s"Give unique username. $user exists already.")
        this.user = readLine()
        output.writeUTF(user)
        isUniqueUsername = input.readUTF()
      }
      println(input.readUTF()) // Passcode
      print("Enter the passcode : ")
      output.writeUTF(readLine())
      val code = input.readUTF()
      if (code == "SUCCESS") {
        println("=" * 100)
        println(" " * 47 + "Chat Started")
        println("=" * 100)
        println(input.readUTF())
        client ! Start
      }
      else {
        println("Are you a bot?")
        output.close()
        input.close()
        socket.close()
        client.terminate()
      }
    }
    catch {
      case t: UnknownHostException => println(t) ;client.terminate()
      case i: IOException => println(i) ;client.terminate()
      case e: NullPointerException => println(e) ;client.terminate()
    }
  }
  object ClientReader {
    def apply(): Behavior[ClientProtocol] = Behaviors.receive { (context, message) =>
      message match {
        case ReadMessage =>
          try {
            val str = input.readUTF()
            decoding(str)
            context.self ! ReadMessage
            Behaviors.same
          }
          catch {
            case _: Throwable =>
              println("=" * 100)
              println(" " * 47 + "Chat Ended")
              println("=" * 100)
              input.close()
              flag = false
              output.close()
              socket.close()
              client.terminate()
              Behaviors.stopped
          }
      }
    }
  }

  object ClientWriter{
    def apply(): Behavior[ClientProtocol] = Behaviors.receive{ (context,message)=>
      message match {
        case WriteMessage =>
          val reader = new BufferedReader(new InputStreamReader(System.in))
          if(reader.ready() && flag){
            val line = reader.readLine()
            output.writeUTF(line)
            if(isOpen(line)) {
              context.self ! WriteMessage
              Behaviors.same
            }
            else {
              Behaviors.stopped
            }
          }
          else if (!flag) Behaviors.stopped
          else {
            context.self ! WriteMessage
            Behaviors.same
          }
      }
    }
  }
  object ClientAdmin{
    def apply(username: String): Behavior[ClientAdminProtocol] = Behaviors.receive{ (context,message)=>
      val write = context.spawn(ClientWriter(),s"Writer$username")
      val read = context.spawn(ClientReader(),s"Reader$username")

      message match {
        case Start =>
          read ! ReadMessage
          write ! WriteMessage
          Behaviors.same
      }
    }
  }

  private def isOpen(x: String): Boolean={
    val y=x.toLowerCase()
    !(y== "close" || y=="exit" ||  y=="over")
  }

  private def decoding(str:String):Unit={
    val Line = str.split(":::").toList
    if (Line(1) == user) println(s"[${Line.head}] : ${Line.last}")
    else if (Line(1).toLowerCase == "all" && Line.head!=user) println(s"[${Line.head}] : ${Line.last}")
  }

}

object Client {
  def main(args: Array[String]): Unit = {
    val username= readLine("Enter your username: ").filter(_!=' ')
    val IP= "localhost"
    new Client(username,IP, 8080).init()
  }
}