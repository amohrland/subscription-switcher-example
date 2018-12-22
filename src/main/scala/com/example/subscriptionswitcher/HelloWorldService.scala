package com.example.subscriptionswitcher

import io.circe.Json
import cats.implicits._
import cats.effect._
import fs2._
import org.http4s.HttpService
import org.http4s.circe._
import org.http4s.dsl.Http4sDsl
import org.http4s.server.websocket.WebSocketBuilder
import org.http4s.websocket.WebsocketBits.WebSocketFrame
import scala.concurrent.duration._

import scala.concurrent.ExecutionContext

class HelloWorldService[F[_]: Effect](implicit ec: ExecutionContext) extends Http4sDsl[F] {
  type CancelQueue[F[_]] = async.mutable.Queue[F,Boolean]

  def service(scheduler: Scheduler): HttpService[F] = {
    HttpService[F] {
      case GET -> Root / "ws" =>
        def getNextStream(s:WebSocketFrame): F[(Stream[F, WebSocketFrame], F[Unit])] =
          for {
            cancelQ <- async.mutable.Queue.unbounded[F,Boolean]
            cancel = cancelQ.enqueue1(true)
            result = scheduler.awakeEvery(1.seconds).as(s).interruptWhen(cancelQ.dequeue).mask
          } yield (result, cancel)


        def inSink(
          q: async.mutable.Queue[F,Stream[F,WebSocketFrame]],
          cancelRef: async.Ref[F,F[Unit]],
        ): Sink[F, WebSocketFrame] = Sink { (m: WebSocketFrame) =>
          for {
            _ <- Sync[F].delay(println(s"======= Got frame: ${m}"))
            (nextStream, cancelNext) <- getNextStream(m)
            _ <- Sync[F].delay(println(s"======= Fetched next stream"))
            _ <- cancelRef.get.flatten
            _ <- Sync[F].delay(println(s"======= Cancelled old stream"))
            _ <- cancelRef.setSync(cancelNext) // set new cancel action.
            _ <- Sync[F].delay(println(s"======= Set new cancel action"))
            _ <- q.enqueue1(nextStream)
            _ <- Sync[F].delay(println(s"======= Enqueued next stream"))
          } yield ()
        }

        def outStream(q: async.mutable.Queue[F,Stream[F,WebSocketFrame]]): Stream[F, WebSocketFrame] = {
          q.dequeue.flatMap(identity)
        }

        for {
          mutQ <- async.mutable.Queue.unbounded[F,Stream[F,WebSocketFrame]]
          cancelRef <- async.refOf[F,F[Unit]](Sync[F].unit)
          cxn <- WebSocketBuilder[F].build(
            send = outStream(mutQ),
            receive = inSink(mutQ, cancelRef),
          )
        } yield cxn
    }
  }
}
