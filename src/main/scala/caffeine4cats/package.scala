package caffeine4cats

import cats.Applicative
import cats.data.Kleisli
import cats.implicits._
import cats.effect._
import cats.effect.concurrent.Deferred
import com.github.benmanes.caffeine.cache.{Cache => CCache}

trait KV[F[_], A, B] {
  def get(key: A): F[Option[B]]
  def put(key: A, value: B): F[Unit]
}

trait Management[F[_], A, B] {
  def invalidateAll: F[Unit]
  def invalidate(key: A): F[Unit]
}

trait Load[F[_], A, B] {
  def load(key: A): F[Option[B]]
}

trait LoadingCache[F[_], A, B] extends Load[F, A, B] with KV[F, A, B] {
  def getOrLoad(key: A): F[Option[B]]
}

trait CaffeineCache[F[_], A, B] extends LoadingCache[F, A, B] with Management[F, A, B]

sealed trait CachedValue[F[_], A]
object CachedValue {
  final case class Pending[F[_], A](value: Deferred[F, Option[A]]) extends CachedValue[F, A]
  final case class Done[F[_], A](value: A)                         extends CachedValue[F, A]

  implicit class syntax[F[_], A](private val cv: CachedValue[F, A]) {
    def get(implicit F: Applicative[F]): F[Option[A]] = cv match {
      case Pending(deferred) => deferred.get
      case Done(value)       => value.some.pure[F]
    }
  }
}

object Caffeine4cats {
  import CachedValue._

  def apply[F[_]: ConcurrentEffect, A, B](
    underlying: CCache[A, CachedValue[F, B]],
    loader: Kleisli[F, A, Option[B]]
  ): CaffeineCache[F, A, B] = new CaffeineCache[F, A, B] {

    override def get(key: A): F[Option[B]] =
      Sync[F]
        .delay(Option(underlying.getIfPresent(key)))
        .flatMap(_.fold(none[B].pure[F])(_.get))

    override def put(key: A, value: B): F[Unit] = Sync[F].delay(underlying.put(key, Done(value)))

    override def load(key: A): F[Option[B]] = loader.run(key)

    def unsafeLoad(key: A): CachedValue[F, B] = {
      // effectful code to be fed to `Cache.get(key, load)`
      val deferred = Deferred.unsafe[F, Option[B]]
      val pending  = Pending(deferred)
      val f        = loader
        .run(key)
        .attempt
        .flatMap {
          case Left(_)  => deferred.complete(none) *> invalidate(key)
          case Right(a) => deferred.complete(a) *> a.fold(invalidate(key))(a0 => put(key, a0))
        }
      ConcurrentEffect[F].toIO(f).unsafeRunAsyncAndForget()
      pending
    }

    override def getOrLoad(key: A): F[Option[B]] =
      Sync[F]
        .delay(Option(underlying.get(key, unsafeLoad)))
        .flatMap(_.flatTraverse(_.get))

    override def invalidateAll: F[Unit]      = Sync[F].delay(underlying.invalidateAll())
    override def invalidate(key: A): F[Unit] = Sync[F].delay(underlying.invalidate(key))
  }
}
