package ip

import util.{ ParseLinks, FIFOBuffer, ConvertObject, ConvertNumber }
import java.net.{ DatagramSocket, InetAddress, DatagramPacket, InetSocketAddress }
import java.io.IOException
import scala.collection.mutable.HashMap

class NodeInterface {
  var localPhysPort: Int = _
  var localPhysHost: InetAddress = _
  var socket: DatagramSocket = _
  var linkInterfaceArray: Array[LinkInterface] = _
  // dst addr, cost, next addr
  val routingTable = new HashMap[InetAddress, (Int, InetAddress)]
  
  val UsageCommand = "We only accept: [i]nterfaces, [r]outes," +
    "[d]own <integer>, [u]p <integer>, [s]end <vip> <proto> <string>, [q]uit"

  // remote phys addr + port => interface
  var physAddrToInterface = new HashMap[InetSocketAddress, LinkInterface]
  
  // remote virtual addr => interface
  var virtAddrToInterface = new HashMap[InetAddress, LinkInterface]

  def initSocketAndInterfaces(file: String) {
    val lnx = ParseLinks.parseLinks(file)
    localPhysPort = lnx.localPhysPort
    localPhysHost = lnx.localPhysHost

    // init socket
    socket = new DatagramSocket(lnx.localPhysPort, lnx.localPhysHost)
    linkInterfaceArray = new Array[LinkInterface](lnx.links.length)

    // init link interfaces
    var id = 0
    for (link <- lnx.links) {
      val interface = new LinkInterface(link, id)
      linkInterfaceArray(id) = interface

      physAddrToInterface.put(new InetSocketAddress(interface.link.remotePhysHost, interface.link.remotePhysPort), interface)
      virtAddrToInterface.put(interface.link.remoteVirtIP, interface)
      // routingTable.put(link.localVirtIP, (16, link.remoteVirtIP))
      id += 1
    }
  }

  def sendPacket(interface: LinkInterface) {
    if (interface.isUpOrDown) {
      if (!interface.outBuffer.isEmpty) {
        val pkt = interface.outBuffer.bufferRead
        val headBuf: Array[Byte] = ConvertObject.headToByte(pkt.head)
        if (headBuf != null) {
          // TODO: static constant MTU
          val totalBuf = headBuf ++ pkt.payLoad
          val packet = new DatagramPacket(totalBuf, totalBuf.length, interface.link.remotePhysHost, interface.link.remotePhysPort)
          try {
            socket.send(packet)
          } catch {
            // disconnect
            case ex: IOException => println("send packet")
          }
        }
      }
    } else {
      println("interface " + interface.id + "down")
    }
  }

  def recvPacket() {
    try {
      val pkt = new IPPacket

      // head first byte
      val headByteBuf = new Array[Byte](1)
      val headByte = new DatagramPacket(headByteBuf, 1)
      socket.receive(headByte)
      val len = ConvertObject.headLen(headByteBuf(0))

      // head other bytes
      val headBuf = new Array[Byte](len - 1)
      val packetHead = new DatagramPacket(headBuf, headBuf.length)
      socket.receive(packetHead)

      // convert to IPHead
      pkt.head = ConvertObject.byteToHead(headByteBuf ++ headBuf)

      // payload
      val payLoadBuf = new Array[Byte](ConvertNumber.uint16ToInt(pkt.head.totlen) - len)
      val packetPayLoad = new DatagramPacket(payLoadBuf, payLoadBuf.length)
      socket.receive(packetPayLoad)
      pkt.payLoad = payLoadBuf

      val remote = packetHead.getSocketAddress().asInstanceOf[InetSocketAddress]
      val option = physAddrToInterface.get(remote)
      option match {
        case Some(interface) => {
          if (interface.isUpOrDown) {
            interface.inBuffer.bufferWrite(pkt)
          } else {
            println("interface " + interface.id + "down")
          }
        }
        case None => println("Receiving packet from " + remote.getHostString() + ":" + remote.getPort())
      }

    } catch {
      // disconnect
      case ex: IOException => println("recv packet")
    }

  }
  
  def printInterfaces(arr: Array[String]) {
    if (arr.length != 1) {
      println(UsageCommand)
    } else {
      println("Interfaces:")
      var i = 0;
      for (interface <- linkInterfaceArray) {
        interface.linkInterfacePrint
      }
    }
  }

  def printRoutes(arr: Array[String]) {
    if (arr.length != 1) {
      println(UsageCommand)
    } else {
      println("Routing table:")
      for (entry <- routingTable) {
        var throughAddr: String = ""
        if (entry._1.getHostAddress() == entry._2._2.getHostAddress()) {
          throughAddr = "self"
        } else {
          throughAddr = entry._2._2.getHostAddress()
        }

        println("Route to " + entry._1.getHostAddress() + " with cost " + entry._2._1 +
          ", through " + throughAddr)
      }
    }
  }

  def interfacesDown(arr: Array[String]) {
    if (arr.length != 2) {
      println(UsageCommand)
    } else if (arr(1).forall(_.isDigit)) {
      val num = arr(1).toInt

      if (num < linkInterfaceArray.length) {
        linkInterfaceArray(num).bringDown;
      } else {
        println("No such interface")
      }
    } else {
      println("input should be number")
    }
  }

  def interfacesUp(arr: Array[String]) {
    if (arr.length != 2) {
      println(UsageCommand)
    } else if (arr(1).forall(_.isDigit)) {
      val num = arr(1).toInt

      if (num < linkInterfaceArray.length) {
        linkInterfaceArray(num).bringUp
      } else {
        println("No such interface")
      }
    } else {
      println("input should be number")
    }
  }
}