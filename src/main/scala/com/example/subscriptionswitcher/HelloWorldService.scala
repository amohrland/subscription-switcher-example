package com.example.subscriptionswitcher

import io.circe.Json
import cats.implicits._
import cats.effect._
import fs2._
import org.http4s.HttpService
import org.http4s.circe._
import org.http4s.dsl.Http4sDsl
import org.http4s.server.websocket.WebSocketBuilder
import org.http4s.websocket.WebsocketBits.{Text, WebSocketFrame}

import scala.concurrent.ExecutionContext

class HelloWorldService[F[_]: Effect](implicit ec: ExecutionContext) extends Http4sDsl[F] {
  def service[Q,D,E](
    percolator: Percolator[F,Q,D],
    webSocketCodec: WebSocketCodec[Q,D,E],
  ): HttpService[F] = {
    HttpService[F] {
      case GET -> Root / "ws" =>
        def getNextStream(query: Q): F[(Stream[F, D], F[Unit])] =
          percolator.registerQuery(query) *>
            percolator.getPercolation(query)
              .flatMap(cancellableStream)

        def cancellableStream[A](stream: Stream[F,A]): F[(Stream[F,A], F[Unit])] =
          for {
            cancelQ <- async.mutable.Queue.unbounded[F,Boolean]
          } yield (
            stream.interruptWhen(cancelQ.dequeue).mask,
            cancelQ.enqueue1(true),
          )

        def queryHandler(
          documentStreamQueue: async.mutable.Queue[F,Stream[F,D]],
          cancelRef: async.Ref[F,F[Unit]],
        )(query: Q): F[Unit] =
          for {
            (nextStream, cancelNext) <- getNextStream(query)
            _ <- cancelRef.get.flatten
            _ <- cancelRef.setSync(cancelNext)
            _ <- documentStreamQueue.enqueue1(nextStream)
          } yield ()

        def errorHandler(errorQueue: async.mutable.Queue[F,E])(e: E): F[Unit] =
          errorQueue.enqueue1(e)

        def frameStream(
          documents: async.mutable.Queue[F,Stream[F,D]],
          errors: async.mutable.Queue[F,E],
        ): Stream[F, WebSocketFrame] =
          documents
            .dequeue
            .flatten
            .map(webSocketCodec.successAsFrame(_))
            .merge(
              errors
                .dequeue
                .map(webSocketCodec.errorAsFrame)
            )

        def frameSink(queryHandler: Q => F[Unit], errorHandler: E => F[Unit]): Sink[F,WebSocketFrame] =
          Sink(frame =>
            webSocketCodec.fromFrame(frame).fold(errorHandler, queryHandler))

        for {
          streamQueue <- async.mutable.Queue.unbounded[F,Stream[F,D]]
          errorQueue <- async.mutable.Queue.unbounded[F,E]
          cancelRef <- async.refOf[F,F[Unit]](Sync[F].unit)
          cxn <- WebSocketBuilder[F].build(
            send = frameStream(streamQueue, errorQueue),
            receive = frameSink(
              queryHandler(streamQueue, cancelRef),
              errorQueue.enqueue1,
            )
          )
        } yield cxn
    }
  }
}
