package com.example.subscriptionswitcher

import cats.implicits._
import cats.effect.{Effect, IO}
import fs2._
import org.http4s.server.blaze.BlazeBuilder

import scala.concurrent.ExecutionContext

object HelloWorldServer extends StreamApp[IO] {
  import scala.concurrent.ExecutionContext.Implicits.global

  def stream(args: List[String], requestShutdown: IO[Unit]) = ServerStream.stream[IO]
}

object ServerStream {
  def stream[F[_]: Effect](implicit ec: ExecutionContext) = for {
    scheduler <- Scheduler(1)
    serverStream <- BlazeBuilder[F]
      .bindHttp(8080, "0.0.0.0")
      .mountService(new HelloWorldService[F]
        .service(StubbedPercolator(scheduler), StringyWebSocketCodec()), "/")
      .serve
  } yield serverStream
}
