package zio.sqs

import io.github.vigoo.zioaws
import io.github.vigoo.zioaws.sqs._
import io.github.vigoo.zioaws.sqs.model._
import zio.{ RIO, ZIO }
import zio.stream.ZStream

object SqsStream {

  def apply(
    queueUrl: String,
    settings: SqsStreamSettings = SqsStreamSettings()
  ): ZStream[Sqs, Throwable, Message.ReadOnly] = {

    val request = ReceiveMessageRequest(
      queueUrl = queueUrl,
      attributeNames = Some(settings.attributeNames),
      messageAttributeNames = Some(settings.messageAttributeNames),
      maxNumberOfMessages = Some(settings.maxNumberOfMessages),
      visibilityTimeout = settings.visibilityTimeout,
      waitTimeSeconds = settings.waitTimeSeconds
    )

    ZStream
      .repeatEffect(
        zioaws.sqs
          .receiveMessage(request)
          .mapError(_.toThrowable)
      )
      .map(_.messagesValue.getOrElse(List.empty))
      .takeWhile(_.nonEmpty || !settings.stopWhenQueueEmpty)
      .mapConcat(identity)
      .mapM(msg => ZIO.when(settings.autoDelete)(deleteMessage(queueUrl, msg)).as(msg))
  }

  def deleteMessage(queueUrl: String, msg: Message.ReadOnly): RIO[Sqs, Unit] =
    zioaws.sqs.deleteMessage(DeleteMessageRequest(queueUrl, msg.receiptHandleValue.getOrElse(""))).mapError(_.toThrowable)
}
