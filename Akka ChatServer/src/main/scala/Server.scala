import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.actor.typed.scaladsl.Behaviors

import java.io.{DataInputStream, DataOutputStream, IOException}
import java.net.{ServerSocket, Socket, UnknownHostException}
import scala.util.Random
import scala.collection.mutable.{Map => MutableMap}


class Server (port : Int){

  trait Text
  case object ReceiveMessages extends Text
  case class PrivateMessage(str:String, sendTo: String, sender: String) extends Text
  case class Broadcast(str: String, sender: String) extends Text
  case class CloseRequest(sender: String) extends Text

  trait ServerProtocol
  case class CreateRoom(username: String, inputStream: DataInputStream) extends ServerProtocol
  case class DeleteRoom(username: String) extends ServerProtocol
  case class BroadCastMessage(text: Text) extends ServerProtocol

  private var ip_users = 0
  private val socketMap = MutableMap[String,Socket]()
  private val mapOfOutputStream = MutableMap[String,DataOutputStream]()
  private val mapOfInputStream = MutableMap[String,DataInputStream]()
  private val actorRefs = MutableMap[String,ActorRef[Text]]()

  object serverAdmin{
    def apply(): Behavior[ServerProtocol] = Behaviors.receive{ (context,message) =>
      message match{
        case CreateRoom(username,inputStream)=>
          val childRef = context.spawn(messageAdmin(context.self,inputStream),s"$username")
          val statusMessage = s"$username has joined the chat"
          println(statusMessage)
          childRef ! Broadcast(statusMessage,"Server")
          actorRefs += (username -> childRef)
          Behaviors.same

        case DeleteRoom(username) =>
          try{
            val statusMessage = s"$username has left the chat"
            println(statusMessage)
            deleteUser(username)
            println("Total users now : " + mapOfOutputStream.keys.toList.length + ", IP Users Created: "+ ip_users)
            mapOfOutputStream.keys.foreach(i => mapOfOutputStream(i).writeUTF("Server"+":::all:::"+statusMessage))
          }
          catch{
            case _: Throwable => println("Logging out error!")
          }
          Behaviors.same

        case BroadCastMessage(text) =>
          actorRefs.keys.foreach(i => actorRefs(i) ! text)
          Behaviors.same

        case _ =>
          println("Wrong Protocol")
          Behaviors.same
      }
    }
  }

  object messageAdmin{
    def apply(parent: ActorRef[ServerProtocol],inputStream : DataInputStream): Behavior[Text]= Behaviors.receive{ (context,message)=>
      val senderName = context.self.path.name
      message match{
        case ReceiveMessages =>
          try{
            context.self ! reshape(inputStream.readUTF(),senderName)
          }
          catch {
            case _ : IOException => parent ! DeleteRoom(senderName)
          }
          Behaviors.same

        case PrivateMessage(str, sendTo, sender) =>
          try{
            mapOfOutputStream(sendTo).writeUTF(sender + ":::"+ sendTo + ":::" +str)
            println(s"$sender sends  a message to $sendTo")
          }
          catch{
            case _:Throwable =>
              mapOfOutputStream(sender).writeUTF(s"Server:::all:::$sendTo doesn't exist!")
          }
          context.self ! ReceiveMessages
          Behaviors.same

        case Broadcast(str, sender)=>
          mapOfOutputStream.keys.foreach(i => mapOfOutputStream(i).writeUTF(sender + ":::"+ "all"+ ":::" +str))
          println(s"$sender broadcasts  a message")
          context.self ! ReceiveMessages
          Behaviors.same

        case CloseRequest(sender) =>
          try{
            mapOfOutputStream(sender).writeUTF(sender + ":::"+ "all"+ ":::" +"close")
            parent ! DeleteRoom(sender)
          }
          catch{
            case _: IOException => println(s"$sender has been deleted!")
          }
          Behaviors.stopped
      }
    }
  }

  def init():Unit={
    try {
      val server = new ServerSocket(port)
      println("Server Started")
      val serverRef = ActorSystem(serverAdmin(), "Server")

      Runtime.getRuntime.addShutdownHook(new Thread(() => {
        println("I am here in the thread")
        mapOfOutputStream.keys.foreach( user =>{
          mapOfOutputStream(user).writeUTF("Server:::all:::Server is being closed")
          deleteUser(user)
        })
        println("Closing All users ")
      }))

      while (!server.isClosed) {
        println("Waiting for a client...")
        val socket = server.accept()
        val input = new DataInputStream(socket.getInputStream)
        val output = new DataOutputStream(socket.getOutputStream)

        var user = input.readUTF()
        while(mapOfOutputStream.contains(user)){
          output.writeUTF("Not Ok")
          user = input.readUTF()
        }
        output.writeUTF("Ok")
        val username = user
        println(s"$username joined ")
        ip_users += 1

        val token = generatePassword()
        val timeLimit = 60000 // new Random().between(2000*token.length,3000*token.length) // we can try out this thing!
        output.writeUTF(s"Print the passcode : $token, time limit = ${timeLimit / 1000} seconds")
        val time0 = System.currentTimeMillis()
        val tokenPass = input.readUTF()
        val timeTaken = System.currentTimeMillis() - time0

        if (tokenPass == token && timeLimit >= timeTaken) {
          output.writeUTF("SUCCESS")
          Thread.sleep(100)
          try {
            val available_users = mapOfOutputStream.keys.toList.reduce(_ + " " + _)
            output.writeUTF("Available users : " + available_users)
          }
          catch {
            case _: UnsupportedOperationException => output.writeUTF("No user is available now")
          }

          // Need the change in map
          socketMap += (username -> socket)
          mapOfOutputStream += (username -> output)
          mapOfInputStream += (username -> input)

          serverRef ! CreateRoom(username, input)
        }
        else {
          output.writeUTF("Failure")
        }
      }
    }
    catch {
      case t: UnknownHostException => println(t)
      case i: IOException => println(i)
    }

  }


  private def deleteUser(username: String):Unit={
    mapOfOutputStream(username).close()
    mapOfInputStream(username).close()
    socketMap(username).close()
    mapOfOutputStream -= username
    mapOfInputStream -= username
    actorRefs -=username
    socketMap -= username

  }

  private def generatePassword():String={
    val charSet=('a' to 'z') ++ ('A' to 'Z') ++ "@#$!-"
    val random= new Random()
    val size=random.between(1,2) //8-11
    var str=""
    while(str.length<size){
      val index= random.nextInt(charSet.length)
      if(!str.contains(charSet(index))) str += charSet(index)
    }
    str
  }
  private def reshape(str: String,sender: String): Text = {
    val regex = "@\\w+".r

    if(isClose(str)) CloseRequest(sender)
    else if (str.contains('@')) {
      val sendTo = regex.findFirstIn(str).get.filter(_ != '@')
      val message = regex.split(str).map(_.trim).reduce(_ + " " + _)

      if (sendTo.toLowerCase.contains("all"))
        Broadcast(message.replaceFirst(sendTo, ""), sender)
      else {
        PrivateMessage(message, sendTo, sender)
      }
    }
    else {
      Broadcast(str, sender)
    }

  }
  private def isClose(x: String): Boolean={
    val y=x.toLowerCase()
    y== "close" || y=="exit" ||  y=="over"
  }

}

object Server{
  def main(args: Array[String]): Unit = {
    new Server(8080).init()
  }
}