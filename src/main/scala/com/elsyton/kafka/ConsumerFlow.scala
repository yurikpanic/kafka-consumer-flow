package com.elsyton.kafka

import akka.stream.stage._
import akka.stream.{Attributes, FlowShape, Inlet, Outlet}
import com.elsyton.kafka.ConsumerFlow.ConsumerCommand
import org.apache.kafka.clients.consumer.{ConsumerRecord, KafkaConsumer}
import org.apache.kafka.common.errors.WakeupException

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.concurrent.duration._

class ConsumerFlow[K, V](kafkaConsumer: KafkaConsumer[K, V])
    extends GraphStage[FlowShape[ConsumerCommand[K, V], ConsumerRecord[K, V]]] {

  import ConsumerFlow._

  private val in     = Inlet.create[ConsumerCommand[K, V]]("consumer-flow.in")
  private val out    = Outlet.create[ConsumerRecord[K, V]]("consumer-flow.out")
  private val _shape = new FlowShape[ConsumerCommand[K, V], ConsumerRecord[K, V]](in, out)

  override def shape: FlowShape[ConsumerCommand[K, V], ConsumerRecord[K, V]] = _shape

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new TimerGraphStageLogicWithLogging(_shape) {
      val records: mutable.Queue[ConsumerRecord[K, V]] = mutable.Queue.empty[ConsumerRecord[K, V]]

      final val pollTimerId = 'poll

      setHandlers(in, out, new AbstractInOutHandler {
        override def onPull(): Unit =
          if (records.nonEmpty) push(out, records.dequeue())

        override def onPush(): Unit = {
          grab(in)(kafkaConsumer)
          pull(in)
          if (!isTimerActive(pollTimerId)) {
            schedulePoll()
            log.info("polling consumer resumed")
          }
        }
      })

      def schedulePoll(): Unit = schedulePeriodically(pollTimerId, 100.millis)

      def pollKafka(): Unit =
        try {
          records.enqueue(kafkaConsumer.poll(100).asScala.toSeq: _*)
          if (isAvailable(out) && records.nonEmpty) push(out, records.dequeue())
        } catch {
          case _: IllegalStateException =>
            cancelTimer(pollTimerId)
            log.info("not subscribed to any topic, polling consumer suspended until ConsumerCommand is received")
          case _: WakeupException =>
        }

      override protected def onTimer(timerKey: Any): Unit = pollKafka()

      override def preStart(): Unit = {
        schedulePoll()
        pull(in)
      }
    }
}

object ConsumerFlow {
  type ConsumerCommand[K, V] = (KafkaConsumer[K, V]) => Unit
}
