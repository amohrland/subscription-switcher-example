package com.example.subscriptionswitcher

import org.http4s.websocket.WebsocketBits.{Text, WebSocketFrame}
import cats.implicits._

abstract class WebSocketCodec[Q,D,E] {
  def fromFrame(webSocketFrame: WebSocketFrame): Either[E,Q]
  def successAsFrame(d: D): WebSocketFrame
  def errorAsFrame(e: E): WebSocketFrame
}

case class StringyWebSocketCodec() extends WebSocketCodec[String,String,String] {
  override def fromFrame(webSocketFrame: WebSocketFrame): Either[String, String] =
    webSocketFrame match {
      case Text(body,_) if body.startsWith("?") =>
        body.asRight[String]
      case _ =>
        s"Error: Failed to parse query. Try prefixing query with '?'.".asLeft
    }

  override def successAsFrame(d: String): WebSocketFrame = Text(d)

  override def errorAsFrame(e: String): WebSocketFrame = Text(e)
}
