package com.example.subscriptionswitcher

import cats.implicits._
import cats.effect._
import fs2._

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.Random

abstract class Percolator[F[_],Query,Document] {
  def registerQuery(query: Query): F[Unit]
  def getPercolation(query: Query): F[Stream[F,Document]]
}

case class StubbedPercolator[F[_]:Effect](
  scheduler: Scheduler
)(implicit
  ec:ExecutionContext) extends Percolator[F,String,String] {

  override def registerQuery(query: String): F[Unit] =
    Sync[F].unit

  override def getPercolation(query: String): F[Stream[F, String]] =
    scheduler
      .awakeEvery(3.seconds)
      .evalMap(_ => Sync[F].delay(Random.nextString(10)))
      .map(body => s"made up document that matches query '$query' with random body: ${body}")
      .pure[F]
}