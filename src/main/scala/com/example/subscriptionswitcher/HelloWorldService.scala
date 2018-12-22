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
          documentStreamSignal: async.mutable.Signal[F,(Stream[F,D],F[Unit])],
        )(query: Q): F[Unit] =
          for {
            nextStream_canceller <- getNextStream(query)
            _ <- documentStreamSignal.get.map(_._2).flatten
            _ <- documentStreamSignal.set(nextStream_canceller)
          } yield ()

        def errorHandler(errorQueue: async.mutable.Queue[F,E])(e: E): F[Unit] =
          errorQueue.enqueue1(e)

        def frameStream(
          documents: async.immutable.Signal[F,(Stream[F,D],F[Unit])],
          errors: async.mutable.Queue[F,E],
        ): Stream[F, WebSocketFrame] =
          documents.discrete.map(_._1)
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
          streamSignal <- async.mutable.Signal[F,(Stream[F,D],F[Unit])]((Stream.empty,Sync[F].unit))
          errorQueue <- async.mutable.Queue.unbounded[F,E]
          cxn <- WebSocketBuilder[F].build(
            send = frameStream(streamSignal, errorQueue),
            receive = frameSink(
              queryHandler(streamSignal),
              errorHandler(errorQueue),
            )
          )
        } yield cxn
    }
  }
}
